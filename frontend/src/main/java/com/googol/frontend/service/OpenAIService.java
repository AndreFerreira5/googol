package com.googol.frontend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

@Service
public class OpenAIService {
    @Value("${openai.api.url}")
    private String openAIApiUrl;

    @Value("${openai.api.key}")
    private String openAIApiKey;

    @Value("${openai.api.model}")
    private String openAIApiModel;

    @Value("${ai.prompt.context}")
    private String promptContext;

    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public String getCompletion(String searchTerms) {
        String prompt = promptContext.replace("{searchTerms}", searchTerms);

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", openAIApiModel);

        ArrayNode messages = mapper.createArrayNode();
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);

        requestBody.set("messages", messages);

        RequestBody body = RequestBody.create(
                MediaType.get("application/json"), requestBody.toString());

        Request request = new Request.Builder()
                .url(openAIApiUrl)
                .header("Authorization", "Bearer " + openAIApiKey)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Request failed: " + response);
                return null;
            }

            ObjectNode responseBody = (ObjectNode) mapper.readTree(response.body().string());
            ArrayNode choices = (ArrayNode) responseBody.get("choices");
            if (!choices.isEmpty()) {
                return choices.get(0).get("message").get("content").asText().trim();
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
