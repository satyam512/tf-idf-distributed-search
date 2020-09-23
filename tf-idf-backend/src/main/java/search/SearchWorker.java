package search;

import model.DocumentData;
import model.Result;
import model.SerializationUtils;
import model.Task;
import networking.OnRequestCallback;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SearchWorker implements OnRequestCallback {  // will pass instance of this to WebServer as it implements OnRequestCallback

    private static final String ENDPOINT = "/task";


    @Override
    public byte[] handleRequest(byte[] requestPayload) {
        Task task = (Task) SerializationUtils.deserialize(requestPayload);
        //System.out.println("getting req ");
        Result result = createResult(task);

        byte [] ser = SerializationUtils.serialize(result);
        System.out.println(ser.length);
        return ser;
    }

    private Result createResult(Task task) {
        Result result = new Result();
        List<String> documents = task.getDocuments();
        System.out.println(String.format("Received %d documents to process", documents.size()));

        for (String document : documents) {
            List<String> wordsInThisDocument = parseWordsFromDocument(document);
            DocumentData documentData = TFIDF.createDocumentData(wordsInThisDocument, task.getSearchTerms());
            result.addDocumentData(document, documentData);
        }
        System.out.println("result generated");
        return result;
    }

    private List<String> parseWordsFromDocument(String document) {

        FileReader fileReader = null;
        try {
            fileReader = new FileReader(document);
        } catch (FileNotFoundException e) {
            return Collections.emptyList();
        }

        BufferedReader bufferedReader = new BufferedReader(fileReader);
        List<String> lines = bufferedReader.lines().collect(Collectors.toList());
        List<String> words = TFIDF.getWordsFromDocument(lines);
        return words;

    }
    @Override
    public String getEndpoint() {
        return SearchWorker.ENDPOINT;
    }
}
