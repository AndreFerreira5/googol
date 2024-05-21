package com.googol.frontend.controller;

import com.googol.frontend.service.GoogleAIService;
import com.googol.frontend.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AIAnalysisController {

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private GoogleAIService googleAIService;

    @GetMapping("/openai")
    public String generateAnalysisOpenAI(@RequestParam("query") String query) {
        return openAIService.getCompletion(query);
    }

    @GetMapping("/google")
    public String generateAnalysisGoogle(@RequestParam("query") String query) {
        return googleAIService.generateContent(query);
    }
}
