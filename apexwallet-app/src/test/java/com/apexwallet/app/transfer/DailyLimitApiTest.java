package com.apexwallet.app.transfer;

import com.apexwallet.app.demo.DemoWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "apex.daily-limit-minor=1000")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DailyLimitApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    DemoWorld demo;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void postedTransferConsumesTheDailyLimitAndAPendingOneDoesNot() throws Exception {
        String alice = token("alice", DemoWorld.ALICE_PASSWORD);
        String body = transfer(1_000);
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "limit-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "limit-ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Idempotency-Replayed", "true"));
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "limit-over")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT"));
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "limit-pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer(100_000)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    private String transfer(long amount) {
        return "{\"fromAccountId\":\"" + demo.aliceAccount().value()
                + "\",\"toAccountId\":\"" + demo.bobAccount().value()
                + "\",\"amountMinor\":" + amount + ",\"currency\":\"EUR\",\"description\":\"limit\"}";
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
