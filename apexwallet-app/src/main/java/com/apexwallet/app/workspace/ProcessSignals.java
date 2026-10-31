package com.apexwallet.app.workspace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class ProcessSignals {

    private ProcessSignals() {
    }

    static boolean grafanaUp(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(400))
                    .GET()
                    .build();
            int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status < 500;
        } catch (Exception ex) {
            return false;
        }
    }

    static boolean assistantUp(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String health = url.endsWith("/") ? url + "health" : url + "/health";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(health))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (Exception ex) {
            return false;
        }
    }
}
