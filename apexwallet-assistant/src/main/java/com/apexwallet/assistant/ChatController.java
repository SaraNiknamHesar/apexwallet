package com.apexwallet.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    private final DeskChat chat;

    public ChatController(DeskChat chat) {
        this.chat = chat;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/api/v1/chat")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String answer = chat.answer(request == null ? null : request.messages());
        return Map.of("answer", answer);
    }

    public record ChatRequest(List<DeskChat.ChatMessage> messages) {
    }

    @RestControllerAdvice
    static class Errors {

        @ExceptionHandler(IllegalArgumentException.class)
        @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
        public Map<String, String> rejected(IllegalArgumentException ex) {
            return Map.of("detail", ex.getMessage());
        }

        @ExceptionHandler(IllegalStateException.class)
        @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
        public Map<String, String> unavailable(IllegalStateException ex) {
            return Map.of("detail", "the assistant could not answer just now");
        }
    }
}
