package com.joblens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class OpenAIProviderClientImpl implements OpenAIProviderClient {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.temperature:0.0}")
    private Double temperature;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String prompt, int maxTokens) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", new Object[] { message });
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);

        String requestBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/chat/completions"))
            .timeout(Duration.ofSeconds(40))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            log.error("OpenAI request failed: {} -> {}", response.statusCode(), response.body());
            throw new IllegalStateException("OpenAI request failed with status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            log.error("OpenAI response missing choices: {}", response.body());
            throw new IllegalStateException("Invalid OpenAI response");
        }
        return choices.get(0).path("message").path("content").asText("{}");
    }
}
