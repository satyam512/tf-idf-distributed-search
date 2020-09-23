package model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class Task implements Serializable {
    private List<String> searchTerms; // query for this task
    private List<String> documents;  // list of names of subset of documents

    public Task(List<String> searchTerms, List<String> documents) {
        this.searchTerms = searchTerms;
        this.documents = documents;
    }

    public List<String> getSearchTerms() {
        return Collections.unmodifiableList(this.searchTerms);
    }

    public List<String> getDocuments() {
        return Collections.unmodifiableList(this.documents);
    }
}
