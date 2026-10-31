package com.apexwallet.app.ai;

import com.apexwallet.ai.Categoriser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpModelClientTest {

    @Test
    void readsTheTextFieldAndAFailureBecomesOther() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model", exchange -> {
            byte[] body = "{\"text\":\"SALARY\"}".getBytes(StandardCharsets.UTF_8);
            if ("fail".equals(exchange.getRequestURI().getPath())) {
                body = "no".getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/fail", exchange -> {
            byte[] body = "no".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            HttpModelClient client = new HttpModelClient("http://127.0.0.1:" + port + "/model", "study-token");
            assertEquals("SALARY", Categoriser.categorise(client, "salary run 123456"));
            HttpModelClient down = new HttpModelClient("http://127.0.0.1:" + port + "/fail", "");
            assertEquals("OTHER", Categoriser.categorise(down, "salary"));
            assertTrue(HttpModelClient.textOf("plain TRANSFER").contains("TRANSFER"));
        } finally {
            server.stop(0);
        }
    }
}
