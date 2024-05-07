package com.googol.frontend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import com.googol.frontend.model.Story;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class HackerNewsService {
    @Value("${hackernews.api.top_stories}")
    private String topStoriesUrl;

    @Value("${hackernews.api.story}")
    private String storyUrl;

    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    private final ConcurrentHashMap<Integer, Story> stories = new ConcurrentHashMap<Integer, Story>();


    //@PostConstruct
    private void onStartup(){
        try{
            ArrayList<Integer> topStories = getTopStories();
            processTopStories(topStories);
            System.out.println("Got " + topStories.size() + " top stories on boot");
        } catch (IOException e){
            System.err.println("[ERROR] Failed to get top stories on boot: " + e.getMessage());
        }
    }


    public void processTopStories(ArrayList<Integer> topStories){
        ArrayList<CompletableFuture<Story>> futures = new ArrayList<>();
        for(int storyId : topStories){
            if(!stories.containsKey(storyId)){
                futures.add(getStoryByIdAsync(storyId));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }


    public ArrayList<Integer> getTopStories() throws IOException {
        Request request = new Request.Builder().url(topStoriesUrl).build();
        try(Response response = client.newCall(request).execute()){
            if(response.isSuccessful()){
                return new ArrayList<>(Arrays.asList(mapper.readValue(response.body().string(), Integer[].class)));
            } else {
                throw new IOException("Unexpected code " + response);
            }
        }
    }


    public Story getStoryById(int storyId) throws  IOException {
        if(stories.containsKey(storyId)) return stories.get(storyId);
        Request request = new Request.Builder().url(storyUrl.replace("{id}", String.valueOf(storyId))).build();
        try(Response response = client.newCall(request).execute()){
            if(response.isSuccessful()){
                Story story = mapper.readValue(response.body().string(), Story.class);
                if(story.getUrl() == null || story.getUrl().isEmpty()) return null;
                try{ // try to get text from story url and put it in the text field
                    story.setUniqueTextWords(fetchTextFromUrl(story.getUrl()));
                } catch (IOException | IllegalArgumentException e){
                    return null; // if it fails, just return null and ignore story
                }

                stories.putIfAbsent(storyId, story);
                return story;
            } else{
                throw new IOException("Unexpected code " + response);
            }
        }
    }


    @Async
    public CompletableFuture<Story> getStoryByIdAsync(int storyId) {
        if (stories.containsKey(storyId)) return CompletableFuture.completedFuture(stories.get(storyId));

        Request request = new Request.Builder().url(storyUrl.replace("{id}", String.valueOf(storyId))).build();

        // Create a new CompletableFuture that will complete once the asynchronous call finishes
        CompletableFuture<Story> future = new CompletableFuture<>();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.err.println("[ERROR] Failed to get story " + storyId + ": " + e.getMessage());
                future.complete(null);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Story story = mapper.readValue(response.body().string(), Story.class);

                    try {
                        story.setUniqueTextWords(fetchTextFromUrl(story.getUrl()));
                    } catch (IOException | IllegalArgumentException e) {
                        future.complete(null);
                        return; // Stop processing if the story text can't be fetched
                    }

                    stories.putIfAbsent(storyId, story);
                    future.complete(story); // Complete the CompletableFuture successfully
                } else {
                    IOException e = new IOException("Unexpected response code " + response);
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }


    // TODO change this to return the title, url and text so the user can see it
    public ArrayList<Story> getMatchingStories(ArrayList<String> searchTerms) throws IOException {
        ArrayList<Integer> topStories = getTopStories();
        ArrayList<CompletableFuture<Story>> futureStories = new ArrayList<>();

        for (int storyId : topStories) {
            futureStories.add(getStoryByIdAsync(storyId));
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futureStories.toArray(new CompletableFuture[0]));

        ArrayList<Story> results = new ArrayList<>();
        try {
            allFutures.join(); // Ensure all futures have completed

            for (CompletableFuture<Story> future : futureStories) {
                Story story = future.get(); // Retrieve the completed story

                // Ensure the story contains all search terms in its unique text words
                if (story != null && story.getUniqueTextWords().containsAll(searchTerms)) {
                    results.add(story);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("[ERROR] Failed to get matching stories: " + e.getMessage());
        }
        if(results.isEmpty()) return null;
        return results;

        /*
        System.out.println("STORIES NUMBER: " + topStories.size());
        for(int i = 0; i < topStories.size(); i++){
            Story story = getStoryById(topStories.get(i));
            if(story != null && story.getUniqueTextWords().containsAll(searchTerms)) {
                System.out.println("HIT! " + i);
                results.add(story);
            }
            System.out.println(i);
        }
        if(results.isEmpty()) return null;
        return results;
         */
    }

    private HashSet<String> fetchTextFromUrl(String url) throws IOException{
        // check if the URL is null or empty
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("The provided URL is null or empty");
        }

        // parse and validate the URL
        HttpUrl parsedUrl = HttpUrl.parse(url);
        if (parsedUrl == null) {
            throw new IllegalArgumentException("The URL is malformed: " + url);
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            if(response.body() == null){
                throw new IOException("Null text body");
            }

            // TODO dont use a generic locale here for the lower case conversion
            return new HashSet<>(Arrays.asList(response.body().string().toLowerCase().split("\\W+")));
        }
    }
}


