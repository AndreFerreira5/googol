package com.googol.frontend.controller;

import com.googol.frontend.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/openai")
public class OpenAIController {

    @Autowired
    private OpenAIService openAIService;

    /*
    @GetMapping
    public ArrayList<String> generateAnalysis(@RequestParam("query") String query) {
        ArrayList<CompletableFuture<String>> aiFutures = Arrays.stream(query.split(" "))
                .map(prompt -> openAIService.getCompletionAsync("Provide an analysis for: " + prompt))
                .collect(Collectors.toList());

        return aiFutures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }*/
}
