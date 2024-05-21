package com.googol.frontend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

@Service
public class GoogleAIService {
    @Value("${google.api.key}")
    private String googleApiKey;

    @Value("${ai.prompt.context}")
    private String promptContext;

    @Value("${google.api.url}")
    private String googleApiUrl;

    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public String generateContent(String searchTerms) {
        String prompt = promptContext.replace("{searchTerms}", searchTerms);

        ObjectNode requestBody = mapper.createObjectNode();
        ArrayNode contentsArray = requestBody.putArray("contents");

        ObjectNode contentObject = mapper.createObjectNode();
        contentObject.put("role", "user");

        ArrayNode partsArray = contentObject.putArray("parts");
        ObjectNode textPart = mapper.createObjectNode();
        textPart.put("text", prompt);
        partsArray.add(textPart);

        contentsArray.add(contentObject);

        RequestBody body = RequestBody.create(
                MediaType.get("application/json"), requestBody.toString());

        Request request = new Request.Builder()
                .url(googleApiUrl)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", googleApiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Request failed: " + response);
                return null;
            }

            JsonNode responseBody = mapper.readTree(response.body().string());
            // Extracting the text from the response
            JsonNode candidates = responseBody.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }

            return null;
        } catch (IOException e) {
            System.err.println("Google API Error: " + e.getMessage());
            return null;
        }
    }
}
