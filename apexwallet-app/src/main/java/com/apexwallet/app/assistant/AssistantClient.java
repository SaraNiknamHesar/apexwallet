package com.apexwallet.app.assistant;

import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.app.workspace.WorkspaceService.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "apex", name = "assistant-url")
public class AssistantClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI endpoint;

    public AssistantClient(ApexProperties properties) {
        String base = properties.getAssistantUrl() == null ? "" : properties.getAssistantUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = URI.create(base + "/api/v1/chat");
    }

    public String ask(List<ChatMessage> messages) {
        try {
            String body = JSON.writeValueAsString(Map.of("messages", messages));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("assistant status " + response.statusCode());
            }
            String answer = JSON.readTree(response.body()).path("answer").asText("").trim();
            if (answer.isBlank()) {
                throw new IllegalStateException("assistant returned an empty answer");
            }
            return answer;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("assistant call interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("assistant call failed", ex);
        }
    }
}
