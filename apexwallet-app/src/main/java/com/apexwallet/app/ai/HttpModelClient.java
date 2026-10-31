package com.apexwallet.app.ai;

import com.apexwallet.ai.ModelPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls one HTTP model. Payments do not use this. An empty URL keeps {@code KeywordModel} instead.
 */
public final class HttpModelClient implements ModelPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final URI endpoint;
    private final String token;
    private final boolean chat;

    public HttpModelClient(String url, String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), url, token);
    }

    HttpModelClient(HttpClient http, String url, String token) {
        this.http = http;
        this.endpoint = URI.create(url);
        this.token = token == null ? "" : token;
        this.chat = url.contains("chat/completions");
    }

    @Override
    public String complete(String maskedPrompt) {
        String prompt = maskedPrompt == null ? "" : maskedPrompt;
        String body = chat ? chatBody(prompt) : "{\"prompt\":\"" + escape(prompt) + "\"}";
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(chat ? Duration.ofSeconds(30) : Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("model status " + response.statusCode());
            }
            return chat ? label(textOf(response.body())) : textOf(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("model call interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("model call failed", ex);
        }
    }

    /**
     * A conversation reply. Classification still uses {@link #complete(String)}.
     */
    public String answer(List<Turn> turns) {
        if (!chat) {
            throw new IllegalStateException("this model url is not a chat endpoint");
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
            String body = JSON.writeValueAsString(payload);
            HttpResponse<String> response = http.send(request(body, Duration.ofSeconds(30)), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("model status " + response.statusCode());
            }
            JsonNode content = JSON.readTree(response.body()).path("choices").path(0).path("message").path("content");
            String answer = content.asText("").trim();
            if (answer.isBlank()) {
                throw new IllegalStateException("model returned an empty answer");
            }
            return answer;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("model call interrupted", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("model call failed", ex);
        }
    }

    public record Turn(String role, String content) {
    }

    private HttpRequest request(String body, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    static String textOf(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String text = quoted(body, "\"text\"");
        if (text != null) {
            return text;
        }
        String content = quoted(body, "\"content\"");
        return content == null ? body.trim() : content;
    }

    private static String chatBody(String prompt) {
        String asked = "Classify this payment into exactly one label. "
                + "GROCERIES: food or a market. UTILITIES: a bill, electricity, or a utility. "
                + "SUBSCRIPTIONS: a subscription. SALARY: salary or payroll. "
                + "TRANSFER: rent, a transfer, or sending money. OTHER: anything else. "
                + "Reply with the label only. Payment: " + prompt;
        return "{\"model\":\"deepseek-chat\",\"temperature\":0,\"max_tokens\":8,\"messages\":[{\"role\":\"user\",\"content\":\""
                + escape(asked) + "\"}]}";
    }

    private static String label(String text) {
        String word = text == null ? "" : text.trim().toUpperCase();
        if (word.endsWith(".")) {
            word = word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static String quoted(String body, String key) {
        int at = body.indexOf(key);
        if (at < 0) {
            return null;
        }
        int colon = body.indexOf(':', at);
        int start = body.indexOf('"', colon + 1);
        int end = start < 0 ? -1 : body.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return null;
        }
        return body.substring(start + 1, end);
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
