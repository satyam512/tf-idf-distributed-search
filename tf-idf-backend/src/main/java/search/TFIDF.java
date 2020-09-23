package search;

import model.DocumentData;

import java.util.*;

public class TFIDF {

    public static double calculateTermFrequency(List<String> words, String term) {
        long count = 0;
        for(String word : words) {
            if(word.equalsIgnoreCase(term))
                count++;
        }
        double total = words.size();
        double tf = count/total;
        return tf;
    }

    public static DocumentData createDocumentData(List<String> words, List<String> terms) {

        //System.out.println("Called begin");
        //System.out.println(words.size());

        long startTime = System.nanoTime();

        // this works slightly faster for larger number of terms
//        DocumentData documentData = new DocumentData();
//        Map<String, Long> ctmap = new HashMap<>();
//        for (String word : words) {
//
//            String wordLower = word.toLowerCase();
//            if(!ctmap.containsKey(wordLower))
//                ctmap.put(wordLower,  1L);
//            else
//                ctmap.put(wordLower, ctmap.get(wordLower)+1L);
//        }
//        double total = words.size();
//        //System.out.println(total);
//        for(String term : terms) {
//
//            long freq = ctmap.containsKey(term.toLowerCase()) ? ctmap.get(term.toLowerCase()) : 0L;
//            double tf = freq/total;
//            documentData.putTermFrequency(term, tf);
//        }

        // this one works just fine
        DocumentData documentData = new DocumentData();

        for (String term : terms) {
            double termFreq = TFIDF.calculateTermFrequency(words, term.toLowerCase());
            documentData.putTermFrequency(term, termFreq);
        }

        //System.out.println("Call end");
        long endTime = System.nanoTime();
        System.out.println("time taken : " + (endTime-startTime));
        return documentData;
    }

    private static double getIDF (String term, Map<String, DocumentData> documentResults) {
        double nt = 0;
        for(String key : documentResults.keySet()) {
            DocumentData documentData = documentResults.get(key);
            if(documentData.getTermFrequency(term)>0)
                nt++;
        }
        return  nt == 0L ? 0.0 : Math.log10( documentResults.size()/nt );
    }

    private static Map<String, Double> getTermToIDFMap (List<String> terms, Map<String, DocumentData> documentResults) {

        Map<String, Double> IDFMap = new HashMap<>();
        for (String term : terms) {
            double termIDF = TFIDF.getIDF(term, documentResults);
            IDFMap.put(term, termIDF);
        }
        return IDFMap;
    }

    public static Map<Double, List<String>> getDocumentsScores(List<String> terms,
                                                               Map<String, DocumentData> documentResults) {
        TreeMap<Double, List<String>> scoreToDoc = new TreeMap<>();

        Map<String, Double> termToInverseDocumentFrequency = getTermToIDFMap(terms, documentResults);

        for (String document : documentResults.keySet()) {
            DocumentData documentData = documentResults.get(document);

            double score = calculateDocumentScore(terms, documentData, termToInverseDocumentFrequency);

            addDocumentScoreToTreeMap(scoreToDoc, score, document);
        }
        return scoreToDoc.descendingMap(); // returns map sorted in desc by key value
    }
    private static void addDocumentScoreToTreeMap(TreeMap<Double, List<String>> scoreToDoc, double score, String document) {
        List<String> booksWithCurrentScore = scoreToDoc.get(score);
        if (booksWithCurrentScore == null) {
            booksWithCurrentScore = new ArrayList<>();
        }
        booksWithCurrentScore.add(document);
        scoreToDoc.put(score, booksWithCurrentScore);
    }

    private static double calculateDocumentScore(List<String> terms,
                                                 DocumentData documentData,
                                                 Map<String, Double> termToInverseDocumentFrequency) {
        double score = 0;
        for (String term : terms) {
            double termFrequency = documentData.getTermFrequency(term);
            double inverseTermFrequency = termToInverseDocumentFrequency.get(term);
            score += termFrequency * inverseTermFrequency;
        }
        return score;
    }

    public static List<String> getWordsFromDocument(List<String> lines) {
        List<String> words = new ArrayList<>();
        for (String line : lines) {
            words.addAll(getWordsFromLine(line));
        }
        return words;
    }

    public static List<String> getWordsFromLine(String line) {
        return Arrays.asList(line.split("(\\.)+|(,)+|( )+|(-)+|(\\?)+|(!)+|(;)+|(:)+|(/d)+|(/n)+"));
    }
}
