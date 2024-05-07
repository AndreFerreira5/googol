package com.googol.frontend.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Story {
    private int id;
    private String title;
    private String url;
    private HashSet<String> uniqueTextWords;

    public Story() {}

    public Story(int id, String title, String url, HashSet<String> uniqueTextWords) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.uniqueTextWords = uniqueTextWords;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public HashSet<String> getUniqueTextWords() {
        return uniqueTextWords;
    }

    public void setUniqueTextWords(HashSet<String> uniqueTextWords) {
        this.uniqueTextWords = uniqueTextWords;
    }
    @Override
    public String toString() {
        return "Story{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", uniqueTextWords='" + uniqueTextWords + '\'' +
                '}';
    }
}
