package search;

import clusterManagement.ServiceRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.jcraft.jsch.ChannelSftp;
import model.DocumentData;
import model.Result;
import model.SerializationUtils;
import model.Task;
import model.proto.SearchModel;
import networking.OnRequestCallback;
import networking.WebClient;
import org.apache.zookeeper.KeeperException;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class SearchCoordinator implements OnRequestCallback {

    private static final String ENDPOINT = "/search";
    private final ServiceRegistry workerServiceRegistry;
    private static final String BOOK_REPO = "./resources/books";
    private WebClient webClient;
    private List<String> documents;

    public SearchCoordinator(ServiceRegistry workerServiceRegistry, WebClient webClient) {
        this.workerServiceRegistry = workerServiceRegistry;
        this.webClient = webClient; // this Leader behaves as a client, requesting worker nodes (Servers) to perform task and send results back
        this.documents = readDocumentsList();
    }


    @Override
    public byte[] handleRequest(byte[] requestPayload) {
        try {
            SearchModel.Request request = SearchModel.Request.parseFrom(requestPayload);
            SearchModel.Response response = createResponse(request);
            return response.toByteArray();
        } catch (InvalidProtocolBufferException | InterruptedException | KeeperException e) {
            e.printStackTrace();
            return SearchModel.Response.getDefaultInstance().toByteArray();
        }
    }

    private SearchModel.Response createResponse(SearchModel.Request request) throws KeeperException, InterruptedException {
        SearchModel.Response.Builder response = SearchModel.Response.newBuilder(); // since proto compiler generates immutable classes so, to create instances it requires builders

        System.out.println("Received search query: " + request.getSearchQuery());
        List<String> searchTerms = TFIDF.getWordsFromLine(request.getSearchQuery());

        List<String> workerAddresses = workerServiceRegistry.getAllServiceAddresses();

        if (workerAddresses.isEmpty()) {  // in case no worker is available so we can't send tasks // see it'll break the code if modulo by 0 is taken
            System.out.println("No search workers currently available");
            return response.build();
        }

        List<Task> taskList = createTasks(workerAddresses.size(), searchTerms);
        List<Result> results = sendTasksToWorkers(workerAddresses, taskList);

        // now we need to aggregate the results
        List<SearchModel.Response.DocumentStats> documentStatsList = aggregateResults(results, searchTerms); // sorted list by score
        response.addAllResultDocuments(documentStatsList);

        return response.build();
    }

    private List<SearchModel.Response.DocumentStats> aggregateResults(List<Result> results, List<String> seachTerms) {
        Map<String, DocumentData> documentResults = new HashMap<>();
        for (Result result : results) {
            documentResults.putAll(result.getDocumentToDocumentData());
        }

        Map<Double, List<String>> scoreMap = TFIDF.getDocumentsScores(seachTerms, documentResults);
        return sortDocumentsByScore(scoreMap);
    }

    private List<SearchModel.Response.DocumentStats> sortDocumentsByScore(Map<Double, List<String> > scoreMap) {
        List<SearchModel.Response.DocumentStats> sortedList = new ArrayList<>();


        for (Map.Entry<Double, List<String>> doubleListEntry : scoreMap.entrySet()) {

            Double score = doubleListEntry.getKey();
            for (String doc : doubleListEntry.getValue()) {
                File documentPath = new File(doc);

                SearchModel.Response.DocumentStats documentStats = SearchModel.Response.DocumentStats.newBuilder()
                        .setScore(score)
                        .setDocumentName(documentPath.getName())
                        .setDocumentSize(documentPath.length())
                        .build();

                sortedList.add(documentStats);
            }
        }

        return sortedList;
    }

    private List<Result> sendTasksToWorkers(List<String> workerAddresses, List<Task> workerTasks) {
        // now now See client, it has a method sendTask which takes a URL, and a Task in byte array
        // So here we need A string of worker URLs to sent requests to, along with List of tasks and then we need to convert each task into a byte array
        // say I assume that it gets the List of urls and List of tasks in input then just need to send them using WebClient API
        CompletableFuture<Result>[] futures = new CompletableFuture[workerTasks.size()];

        System.out.println(workerAddresses);
        for (int i = 0 ; i < workerTasks.size() ; i++) {
            Task task = workerTasks.get(i);
            byte [] payload = SerializationUtils.serialize(task);
            futures[i] = webClient.sendTask(workerAddresses.get(i), payload);
        }

        //List<String> results = new ArrayList<>(); // different way here as now we'll get the deserialized the responses from client
        List<Result> results = new ArrayList<>();
        for(CompletableFuture<Result> future : futures) {
            // results.add(future.join()); // need to change this as we actually need response from all workers
            try {
                Result result = future.get();  // this now waits till some (even null) response is received
                results.add(result);
            } catch (InterruptedException | ExecutionException e) {
            }
        }
        System.out.println(String.format("Received %d/%d results", results.size(), workerTasks.size()));
        return results;
    }
    private List<Task> createTasks(int numberOfWorkers, List<String> searchTerms) {
        List<List<String> > splitList = SearchCoordinator.splitDocumentList(numberOfWorkers, documents);
        List<Task> taskList = new ArrayList<>();
        for (List<String> taskDocument : splitList) {
            Task workerTask = new Task(searchTerms, taskDocument);
            taskList.add(workerTask);
        }
        return taskList;
    }
    private static List<List<String> > splitDocumentList(int numberOfWorkers, List<String> documentsList) {
        // returns a list, each list contains a List of document names that can be assigned to a worker
        // assuming that the number of documents is greater number of workers

        List<List<String>> splitDocuments = new ArrayList<>();

        for(int i=0;i<documentsList.size();i++)
        {
            int index = i%numberOfWorkers;
            if(splitDocuments.size()<=index)
            {
                List<String> values = new ArrayList<>();
                values.add(documentsList.get(i));
                splitDocuments.add(values);
            }
            else
                splitDocuments.get(index).add(documentsList.get(i));
        }

        return  splitDocuments;
    }
    private static List<String> readDocumentsList() {
        File documentsDirectory = new File(BOOK_REPO);
        return Arrays.asList(documentsDirectory.list())
                .stream()
                .map(documentName -> BOOK_REPO + "/" + documentName)
                .collect(Collectors.toList());
    }

    @Override
    public String getEndpoint() {
        return ENDPOINT;  // this is the end point where coordinator expects a search request from front end server
    }
}
