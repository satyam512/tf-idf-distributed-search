package model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public class DocumentData implements Serializable {
    private Map<String, Double> termToFreqency = new HashMap<>();

    public void putTermFrequency (String term, double termFrequency) {
        this.termToFreqency.put(term, termFrequency);
    }

    public Double getTermFrequency(String term) {
        return this.termToFreqency.get(term);
    }
}
