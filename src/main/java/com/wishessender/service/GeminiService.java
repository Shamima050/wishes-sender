package com.wishessender.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Calls the Google Gemini REST API to write text.
 * Docs: https://ai.google.dev/api/generate-content
 */
@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final RestTemplate rest = new RestTemplate();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Sends the prompt to Gemini and returns the reply text.
     * Returns null if there is no API key or the call fails (the caller then uses a template).
     */
    @SuppressWarnings("unchecked")
    public String generate(String prompt) {
        if (!isConfigured()) return null;
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

            // Request body: { "contents": [ { "parts": [ { "text": "..." } ] } ] }
            Map<String, Object> part = Collections.singletonMap("text", prompt);
            Map<String, Object> content = Collections.singletonMap("parts", Collections.singletonList(part));
            Map<String, Object> body = Collections.singletonMap("contents", Collections.singletonList(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);   // key stays on the server, never sent to the browser

            Map<String, Object> response = rest.postForObject(url, new HttpEntity<>(body, headers), Map.class);

            // Reply: candidates[0].content.parts[0].text
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> replyContent = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) replyContent.get("parts");
            return parts.get(0).get("text").toString().trim();
        } catch (Exception e) {
            System.out.println("Gemini call failed, using template instead: " + e.getMessage());
            return null;
        }
    }
}
