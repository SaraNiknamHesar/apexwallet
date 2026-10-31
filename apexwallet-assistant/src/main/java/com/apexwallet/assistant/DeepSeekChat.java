package com.apexwallet.assistant;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekChat {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AssistantProperties properties;

    public DeepSeekChat(AssistantProperties properties) {
        this.properties = properties;
    }

    public String complete(List<Turn> turns) {
        String url = properties.getModelUrl() == null ? "" : properties.getModelUrl().trim();
        if (url.isBlank()) {
            throw new IllegalStateException("the model is not configured");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", "deepseek-chat");
        payload.put("temperature", 0.3);
        payload.put("max_tokens", 700);
        List<Map<String, String>> messages = new ArrayList<>();
        for (Turn turn : turns) {
            messages.add(Map.of("role", turn.role(), "content", turn.content()));
        }
        payload.put("messages", messages);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
            String token = properties.getModelToken() == null ? "" : properties.getModelToken();
            if (!token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("model status " + response.statusCode());
            }
            String answer = JSON.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("").trim();
            if (answer.isBlank()) {
                throw new IllegalStateException("model returned an empty answer");
            }
            return answer;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("model call interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("model call failed", ex);
        }
    }

    public record Turn(String role, String content) {
    }
}
