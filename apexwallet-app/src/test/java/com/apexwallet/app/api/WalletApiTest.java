package com.apexwallet.app.api;

import com.apexwallet.app.demo.DemoWorld;
import com.apexwallet.app.demo.TransactionProbe;
import com.apexwallet.app.demo.TransactionProbeClient;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.app.ledger.LocalBank;
import com.apexwallet.app.port.UserDirectory;
import com.apexwallet.app.transfer.TransferService;
import com.apexwallet.domain.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "apex.approval-threshold-minor=5000")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WalletApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    DemoWorld demo;
    @Autowired
    LocalBank bank;
    @Autowired
    UserDirectory users;
    @Autowired
    TransferService transfers;
    @Autowired
    TransactionProbe probe;
    @Autowired
    TransactionProbeClient probeClient;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void moneyMovementSecurityAndIdempotency() throws Exception {
        assertFalse(probe.selfInvocation());
        assertTrue(probeClient.throughProxy());

        String alice = login("alice", DemoWorld.ALICE_PASSWORD);
        String bob = login("bob", DemoWorld.BOB_PASSWORD);
        String sam = login("sam", DemoWorld.SAM_PASSWORD);

        mvc.perform(get("/api/v1/accounts/" + demo.bobAccount()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/" + demo.bobAccount()).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/accounts/" + demo.bobAccount()).header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk());

        String body = transferBody(demo.aliceAccount().value(), demo.bobAccount().value(), 1_000);
        MvcResult posted = mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "rent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "rent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(result -> assertEquals(posted.getResponse().getContentAsString(), result.getResponse().getContentAsString()));
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "rent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(demo.aliceAccount().value(), demo.bobAccount().value(), 1_001)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(199_000, bank.balanceOf(demo.aliceAccount()));

        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        MvcResult pending = mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + alice)
                        .header("X-Idempotency-Key", "big-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(demo.aliceAccount().value(), demo.bobAccount().value(), 6_000)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        UUID approvalId = UUID.fromString(json.readTree(pending.getResponse().getContentAsString()).get("transactionId").asText());
        mvc.perform(get("/api/v1/transfers/pending").header("Authorization", "Bearer " + alice))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transfers/pending").header("Authorization", "Bearer " + sam))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(approvalId.toString()))
                .andExpect(jsonPath("$[0].makerUsername").value("alice"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        var aliceUser = users.findByUsername("alice").orElseThrow();
        var self = assertThrows(com.apexwallet.domain.error.DomainException.class,
                () -> transfers.approve(new ApexPrincipal(aliceUser.id(), "alice", Role.SUPERVISOR), approvalId));
        assertEquals(ErrorCode.SELF_APPROVAL, self.code());
        mvc.perform(post("/api/v1/transfers/" + approvalId + "/approve").header("Authorization", "Bearer " + sam))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));
        mvc.perform(post("/api/v1/transfers/" + approvalId + "/approve").header("Authorization", "Bearer " + sam))
                .andExpect(status().isConflict());
        assertEquals(193_000, bank.balanceOf(demo.aliceAccount()));
        mvc.perform(get("/api/v1/transfers/pending").header("Authorization", "Bearer " + sam))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerRef":"bob","currency":"EUR","type":"CUSTOMER_LIABILITY","externalAccountNumber":"GB82WEST12345698765433"}
                                """))
                .andExpect(status().isBadRequest());

        var protectedNumber = bank.ciphertext(demo.aliceAccount()).orElseThrow();
        assertNotEquals("GB82WEST12345698765432", protectedNumber.ciphertext());
        assertEquals("GB82WEST12345698765432", bank.find(demo.aliceAccount()).orElseThrow().externalAccountNumber());

        String refresh = json.readTree(loginRaw("alice", DemoWorld.ALICE_PASSWORD)).get("refreshToken").asText();
        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String next = json.readTree(rotated.getResponse().getContentAsString()).get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + next + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        JsonNode node = json.readTree(loginRaw(username, password));
        return node.get("accessToken").asText();
    }

    private String loginRaw(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String transferBody(UUID from, UUID to, long amount) {
        return "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to + "\",\"amountMinor\":" + amount
                + ",\"currency\":\"EUR\",\"description\":\"rent\"}";
    }
}
