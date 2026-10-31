package com.apexwallet.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeskChat {

    static final String BRIEF = """
            You are the assistant service for ApexWallet, a study banking desk. You run in your own process.
            You have no ledger and you cannot post a payment. Answer questions about this project.
            Reply in the same language as the user's latest message. Use plain sentences.
            Do not reveal passwords, API keys, or tokens.
            Facts you can rely on:
            - The bank desk is one Spring Boot application. This chat is a second process that the desk calls.
            - Alice is a customer and starts with €2,000.00. Bob is a customer and starts at €0. Sam approves a payment of €1,000.00 or more. Tim is a teller. Aud can only read.
            - Alice's study IBAN is GB82WEST12345698765432. A transfer needs two account ids. An IBAN can be checked, but it is not who the money is sent to.
            - €10.00 is posted now. €1,000.00 waits for Sam. Sending the same payment again with the same key does not move the money a second time.
            - The daily send limit is €5,000.00. An overdraft limit is the lowest balance allowed. It does not add money.
            - Accounts and payments are stored in Postgres. After a payment is posted, Kafka publishes a note on apexwallet.ledger.
            - Fees, the order book, IBAN checks, and practice files live in the main desk.
            - A study loan pays the customer from the loan book. Up to €500.00 posts now. From €500.01 to €2,000.00, Sam approves first. There is no interest and no credit check. Repayment comes out of the customer balance and reduces what they still owe. This chat cannot grant a loan.
            If the question is outside this desk, say so.
            """;

    private static final Logger log = LoggerFactory.getLogger(DeskChat.class);

    private final DeepSeekChat model;

    public DeskChat(DeepSeekChat model) {
        this.model = model;
    }

    public String answer(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > 20) {
            throw new IllegalArgumentException("send one question, with at most 20 messages");
        }
        List<DeepSeekChat.Turn> turns = new ArrayList<>();
        turns.add(new DeepSeekChat.Turn("system", BRIEF));
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            String role = message.role() == null ? "" : message.role();
            if (!role.equals("user") && !role.equals("assistant")) {
                throw new IllegalArgumentException("a message must be from the user or the assistant");
            }
            String text = message.text() == null ? "" : message.text().trim();
            if (text.isEmpty() || text.length() > 2_000) {
                throw new IllegalArgumentException("each message needs text under 2000 characters");
            }
            if (i == messages.size() - 1 && !role.equals("user")) {
                throw new IllegalArgumentException("the last message must be the question");
            }
            turns.add(new DeepSeekChat.Turn(role, text));
        }
        log.info("desk chat, messages={}", messages.size());
        return model.complete(turns);
    }

    public record ChatMessage(String role, String text) {
    }
}
