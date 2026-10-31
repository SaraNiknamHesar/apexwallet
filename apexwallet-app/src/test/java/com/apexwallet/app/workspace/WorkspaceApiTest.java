package com.apexwallet.app.workspace;

import com.apexwallet.app.demo.DemoWorld;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WorkspaceApiTest {

    @Autowired
    MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void deskReachesTheStudyModules() throws Exception {
        String alice = token("alice", DemoWorld.ALICE_PASSWORD);
        String bob = token("bob", DemoWorld.BOB_PASSWORD);
        String sam = token("sam", DemoWorld.SAM_PASSWORD);
        String aliceId = accountId(alice);
        String bobId = accountId(bob);

        mvc.perform(post("/api/v1/workspace/fees")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PERCENTAGE","amountMinor":10000,"fixedMinor":0,"basisPoints":250,"capMinor":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeMinor").value(250));

        mvc.perform(post("/api/v1/workspace/iban")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iban\":\"GB82WEST12345698765432\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mvc.perform(post("/api/v1/workspace/categorise")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"monthly rent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("TRANSFER"));

        mvc.perform(post("/api/v1/workspace/orders")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("bid-1", "BUY", aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestBid").value(100));
        mvc.perform(post("/api/v1/workspace/orders")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order("ask-1", "SELL", bobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.ledgerTransactionIds.length()").value(1));
        mvc.perform(get("/api/v1/accounts/" + aliceId).header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.balanceMinor").value(199_800));
        mvc.perform(get("/api/v1/accounts/" + bobId).header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.balanceMinor").value(200));

        mvc.perform(get("/api/v1/workspace/reconciliation").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clean").value(true));
        mvc.perform(get("/api/v1/workspace/outbox").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payload").exists());
        mvc.perform(get("/api/v1/workspace/coverage").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postgresOnThisProcess").value(false))
                .andExpect(jsonPath("$.kafkaConsumer").value(false))
                .andExpect(jsonPath("$.grafanaServer").value(false))
                .andExpect(jsonPath("$.hostedModel").value(false))
                .andExpect(jsonPath("$.assistantService").value(false));
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("apexwallet_outbox_unpublished")));

        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + sam))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[3].id").exists());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ownerRef").value("bob"));

        mvc.perform(post("/api/v1/workspace/fees/charge")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PERCENTAGE","amountMinor":10000,"fixedMinor":0,"basisPoints":250,"capMinor":0,"accountId":"%s"}
                                """.formatted(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeMinor").value(250))
                .andExpect(jsonPath("$.balanceMinor").value(199_550));

        mvc.perform(post("/api/v1/workspace/camt")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"xml":"<Document><Stmt><Id>STMT-1</Id><Ntry><NtryRef>R1</NtryRef><Amt>1500</Amt></Ntry></Stmt></Document>","accountId":"%s"}
                                """.formatted(aliceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fresh").value(true))
                .andExpect(jsonPath("$.transactionId").exists());
        mvc.perform(get("/api/v1/accounts/" + aliceId).header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.balanceMinor").value(201_050));
        mvc.perform(post("/api/v1/workspace/camt")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"xml":"<Document><Stmt><Id>STMT-1</Id><Ntry><NtryRef>R1</NtryRef><Amt>1500</Amt></Ntry></Stmt></Document>","accountId":"%s"}
                                """.formatted(aliceId)))
                .andExpect(jsonPath("$.fresh").value(false))
                .andExpect(jsonPath("$.transactionId").doesNotExist());

        mvc.perform(get("/api/v1/workspace/eval").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pass").value(true));
        mvc.perform(post("/api/v1/workspace/budget")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokens\":10}"))
                .andExpect(jsonPath("$.withinBudget").value(true))
                .andExpect(jsonPath("$.spentMicros").value(1_000));
        mvc.perform(post("/api/v1/workspace/budget")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokens\":1}"))
                .andExpect(jsonPath("$.breached").value(true));
    }

    private String accountId(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get(0).get("id").asText();
    }

    private static String order(String id, String side, String accountId) {
        return "{\"id\":\"" + id + "\",\"side\":\"" + side + "\",\"type\":\"LIMIT\",\"limitPrice\":100,\"quantity\":2,\"accountId\":\"" + accountId + "\"}";
    }

    private String token(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
}
