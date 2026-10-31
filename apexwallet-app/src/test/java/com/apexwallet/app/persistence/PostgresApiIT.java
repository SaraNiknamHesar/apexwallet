package com.apexwallet.app.persistence;

import com.apexwallet.app.ApexWalletApplication;
import com.apexwallet.app.demo.DemoWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ApexWalletApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
class PostgresApiIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void httpLoginAndTransferStayInPostgres() throws Exception {
        String alice = token("alice", DemoWorld.ALICE_PASSWORD);
        String bob = token("bob", DemoWorld.BOB_PASSWORD);
        String aliceId = accountId(alice);
        String bobId = accountId(bob);
        String body = "{\"fromAccountId\":\"" + aliceId + "\",\"toAccountId\":\"" + bobId
                + "\",\"amountMinor\":1000,\"currency\":\"EUR\",\"description\":\"postgres\"}";
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "pg-rent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"));
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "pg-rent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));
        mvc.perform(get("/api/v1/accounts/" + aliceId).header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.balanceMinor").value(199_000));

        Integer users = jdbc.queryForObject("select count(*) from app_users", Integer.class);
        Integer sessions = jdbc.queryForObject("select count(*) from refresh_sessions", Integer.class);
        Integer keys = jdbc.queryForObject("select count(*) from idempotency_keys", Integer.class);
        Integer outbox = jdbc.queryForObject("select count(*) from outbox", Integer.class);
        assertTrue(users >= 5);
        assertTrue(sessions >= 2);
        assertTrue(keys >= 1);
        assertTrue(outbox >= 2);
    }

    private String accountId(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get(0).get("id").asText();
    }

    private String token(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("accessToken").asText();
    }
}
