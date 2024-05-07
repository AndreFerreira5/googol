package com.googol.frontend.controller;

import com.googol.frontend.service.HackerNewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.googol.frontend.model.Story;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

@RestController
@RequestMapping("/api/hacker-news")
public class HackerNewsController {
    @Autowired
    private HackerNewsService hackerNewsService;

    @GetMapping
    public ArrayList<Story> getMatchingStories(@RequestParam("query") String query) throws IOException {
        ArrayList<String> searchTerms = new ArrayList<>(Arrays.asList(query.split(" ")));
        return hackerNewsService.getMatchingStories(searchTerms);
    }
}
