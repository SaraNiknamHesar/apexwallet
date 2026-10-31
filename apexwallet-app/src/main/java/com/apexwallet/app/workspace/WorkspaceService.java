package com.apexwallet.app.workspace;

import com.apexwallet.ai.AgentQueryLog;
import com.apexwallet.ai.Categoriser;
import com.apexwallet.ai.EvalHarness;
import com.apexwallet.ai.Masker;
import com.apexwallet.ai.ModelBudget;
import com.apexwallet.ai.RiskModel;
import com.apexwallet.ai.RiskScore;
import com.apexwallet.ai.SimilarTransactions;
import com.apexwallet.ai.ModelPort;
import com.apexwallet.app.ai.HttpModelClient;
import com.apexwallet.app.assistant.AssistantClient;
import com.apexwallet.app.audit.AuditEvent;
import com.apexwallet.app.audit.AuditSink;
import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.messaging.KafkaLedgerBridge;
import com.apexwallet.app.messaging.OutboxQueue;
import com.apexwallet.app.port.Bank;
import com.apexwallet.app.messaging.OutboxMessage;
import com.apexwallet.app.messaging.OutboxRelay;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.fees.DailyLimit;
import com.apexwallet.domain.fees.DayCount;
import com.apexwallet.domain.fees.FeeCalculator;
import com.apexwallet.domain.fees.FeeQuote;
import com.apexwallet.domain.fees.FeeRule;
import com.apexwallet.domain.fees.Interest;
import com.apexwallet.domain.fees.LimitDecision;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.ReconciliationReport;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.markets.Order;
import com.apexwallet.domain.money.Money;
import com.apexwallet.domain.markets.OrderBook;
import com.apexwallet.domain.markets.OrderType;
import com.apexwallet.domain.markets.Side;
import com.apexwallet.domain.markets.Trade;
import com.apexwallet.iso20022.CamtStatement;
import com.apexwallet.iso20022.IbanCheck;
import com.apexwallet.iso20022.Pain001;
import com.apexwallet.iso20022.StatementImport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
@Profile({"local", "postgres"})
public class WorkspaceService {

    static final long DAILY_LIMIT_MINOR = 500_000;

    private static final String DESK_BRIEF = """
            You are the assistant inside ApexWallet, a study banking desk. Answer questions about this project.
            Reply in the same language as the user's latest message. Use plain sentences, not a memo or markdown headings.
            Do not move money, and do not say a payment happened unless the user already described one.
            Do not reveal passwords, API keys, or tokens.
            Facts you can rely on:
            - This is one Spring Boot application with a React desk. It is not a set of microservices.
            - Alice is a customer and starts with €2,000.00. Bob is a customer and starts at €0. Sam is the supervisor and approves a payment of €1,000.00 or more. Tim is a teller and can send for a customer. Aud can only read.
            - Alice's study IBAN is GB82WEST12345698765432. A transfer needs two account ids. An IBAN can be checked, but it is not who the money is sent to.
            - €10.00 is posted now. €1,000.00 waits for Sam. Sending the same payment again with the same key repeats it and does not move the money a second time.
            - The daily send limit is €5,000.00.
            - An overdraft limit is the lowest balance the account may reach. It does not add money. Alice can type any limit that is zero or more. There is no credit review and no debt collection.
            - On this running desk, accounts and payments are stored in Postgres. After a payment is posted, Kafka publishes a note on the topic apexwallet.ledger. The money is already in the ledger before that note.
            - Grafana is on port 3001. Prometheus reads the app's metrics.
            - Fees, the order book, IBAN checks, and practice statement files are real parts of this desk.
            - A study loan pays the customer from the loan book. Up to €500.00 posts now. From €500.01 to €2,000.00, Sam approves first. There is no interest and no credit check. Repayment comes out of the customer balance and reduces what they still owe.
            - This chat is the only part that calls the hosted model. Payments do not.
            If the question is outside this desk, say so and offer a related question about the project.
            """;

    private final Bank bank;
    private final AuditSink audit;
    private final OutboxQueue outbox;
    private final Clock clock;
    private final ModelPort model;
    private final Environment environment;
    private final ApexProperties properties;
    private final ObjectProvider<KafkaLedgerBridge> kafka;
    private final ObjectProvider<HttpModelClient> hosted;
    private final ObjectProvider<AssistantClient> assistant;
    private final DailyLimit dailyLimit = new DailyLimit("desk-daily", DAILY_LIMIT_MINOR);
    /** One price-time book per market, so a BTC order never trades against an XRP one. */
    private static final String DEFAULT_MARKET = "APEX";
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<String, List<Trade>> tradesBySymbol = new ConcurrentHashMap<>();
    private final AgentQueryLog queries = new AgentQueryLog(80);
    private final StatementImport imports = new StatementImport();
    private final ModelBudget budget = new ModelBudget(1_000);
    private final Map<String, UUID> orderAccounts = new HashMap<>();
    private final List<String> tradePostings = new ArrayList<>();
    private final List<String> relayed = new ArrayList<>();

    public WorkspaceService(Bank bank, AuditSink audit, OutboxQueue outbox, Clock clock, ModelPort model,
                            Environment environment, ApexProperties properties, ObjectProvider<KafkaLedgerBridge> kafka,
                            ObjectProvider<HttpModelClient> hosted, ObjectProvider<AssistantClient> assistant) {
        this.bank = bank;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
        this.model = model;
        this.environment = environment;
        this.properties = properties;
        this.kafka = kafka;
        this.hosted = hosted;
        this.assistant = assistant;
    }

    public FeeQuote fee(String type, long amountMinor, long fixedMinor, int basisPoints, long capMinor) {
        FeeRule rule = switch (type == null ? "" : type) {
            case "FIXED" -> FeeRule.fixed("desk-fixed", fixedMinor);
            case "PERCENTAGE" -> FeeRule.percentage("desk-pct", basisPoints);
            case "PERCENTAGE_WITH_CAP" -> FeeRule.percentageWithCap("desk-cap", basisPoints, capMinor);
            case "FREE" -> FeeRule.free("desk-free");
            default -> throw new DomainException(ErrorCode.VALIDATION_FAILED, "fee type must be FIXED, PERCENTAGE, PERCENTAGE_WITH_CAP, or FREE");
        };
        try {
            return FeeCalculator.quote(rule, amountMinor);
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        }
    }

    public long interest(long principalMinor, String annualRate, LocalDate from, LocalDate to, String convention) {
        if (annualRate == null || !annualRate.matches("\\d+(\\.\\d+)?")) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "annual rate must be a decimal such as 0.02");
        }
        try {
            return Interest.accruedMinor(principalMinor, new BigDecimal(annualRate), from, to, DayCount.valueOf(convention));
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        }
    }

    public LimitDecision limit(String accountId, long amountMinor) {
        if (accountId == null || accountId.isBlank() || amountMinor < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "account and a non-negative amount are required");
        }
        return dailyLimit.tryConsume(accountId, LocalDate.now(ZoneOffset.UTC), amountMinor);
    }

    public ReconciliationReport reconciliation() {
        return bank.reconcile();
    }

    public List<AuditEvent> audit() {
        return audit.events();
    }

    public boolean iban(String value) {
        return IbanCheck.valid(value);
    }

    public synchronized FeePost charge(ApexPrincipal actor, UUID accountId, String type, long amountMinor, long fixedMinor, int basisPoints, long capMinor) {
        FeeQuote quote = fee(type, amountMinor, fixedMinor, basisPoints, capMinor);
        Account customer = visible(actor, accountId);
        if (quote.feeMinor() == 0) {
            return new FeePost(quote.feeMinor(), null, bank.balanceOf(customer.id()));
        }
        if (customer.type() != AccountType.CUSTOMER_LIABILITY) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "a fee is charged to a customer account");
        }
        Money fee = Money.ofMinor(quote.feeMinor(), customer.currency());
        Transaction posted = bank.post(new Posting(null, TransactionType.FEE, List.of(
                new Posting.Leg(customer.id(), Direction.DEBIT, fee),
                new Posting.Leg(required(AccountType.FEE_INCOME).id(), Direction.CREDIT, fee)),
                "fee " + quote.ruleId(), null, null, null));
        return new FeePost(quote.feeMinor(), posted.id().value(), bank.balanceOf(customer.id()));
    }

    public synchronized CamtImport camt(ApexPrincipal actor, String xml, UUID accountId) {
        if (xml == null || xml.isBlank() || xml.length() > 200_000) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "paste a study camt statement under 200000 characters");
        }
        try {
            CamtStatement.Statement statement = CamtStatement.parse(xml);
            boolean fresh = imports.importStatement(statement);
            UUID transactionId = null;
            if (fresh && accountId != null) {
                long sum = statement.entries().values().stream().mapToLong(Long::longValue).sum();
                if (sum > 0) {
                    Account customer = visible(actor, accountId);
                    Money money = Money.ofMinor(sum, customer.currency());
                    Transaction posted = bank.post(new Posting(null, TransactionType.DEPOSIT, List.of(
                            new Posting.Leg(required(AccountType.BANK_CASH).id(), Direction.DEBIT, money),
                            new Posting.Leg(customer.id(), Direction.CREDIT, money)),
                            "camt " + statement.id(), null, null, null));
                    transactionId = posted.id().value();
                }
            }
            return new CamtImport(statement.id(), statement.entries().size(), imports.postedMinor(), fresh, transactionId);
        } catch (DomainException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        } catch (Exception ex) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        }
    }

    public String pain(String messageId, List<String> endToEndIds, long amountMinor) {
        if (messageId == null || messageId.isBlank() || endToEndIds == null || endToEndIds.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "a message id and at least one end-to-end id are required");
        }
        String xml = Pain001.batch(messageId, endToEndIds, amountMinor);
        Pain001.requireStudySchema(xml);
        return xml;
    }

    public String categorise(String description) {
        return Categoriser.categorise(model, description == null ? "" : description);
    }

    public RiskScore risk(long amountMinor, int paymentsToday, boolean newPayee) {
        return RiskModel.score(amountMinor, paymentsToday, newPayee);
    }

    public List<String> similar(String query) {
        return SimilarTransactions.rank(query == null ? "" : query, bank.descriptions());
    }

    public synchronized AskResult ask(ApexPrincipal actor, String text) {
        String masked = Masker.mask(text);
        boolean accepted = queries.query(actor.username(), text == null ? "" : text, clock.instant());
        return new AskResult(accepted, masked, queries.lines());
    }

    public synchronized ChatReply chat(ApexPrincipal actor, List<ChatMessage> messages) {
        List<ChatMessage> masked = checked(actor, messages);
        AssistantClient remote = assistant.getIfAvailable();
        if (remote != null && properties.getAssistantUrl() != null && !properties.getAssistantUrl().isBlank()) {
            try {
                return new ChatReply(remote.ask(masked));
            } catch (RuntimeException ex) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "the assistant service could not answer just now");
            }
        }
        HttpModelClient client = hosted.getIfAvailable();
        if (client == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "the hosted model is not connected");
        }
        List<HttpModelClient.Turn> turns = new ArrayList<>();
        turns.add(new HttpModelClient.Turn("system", DESK_BRIEF));
        for (ChatMessage message : masked) {
            turns.add(new HttpModelClient.Turn(message.role(), message.text()));
        }
        try {
            return new ChatReply(client.answer(turns));
        } catch (RuntimeException ex) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "the assistant could not answer just now");
        }
    }

    private List<ChatMessage> checked(ApexPrincipal actor, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > 20) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "send one question, with at most 20 messages");
        }
        List<ChatMessage> masked = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            String role = message.role() == null ? "" : message.role();
            if (!role.equals("user") && !role.equals("assistant")) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "a message must be from the user or the assistant");
            }
            String text = message.text() == null ? "" : message.text().trim();
            if (text.isEmpty() || text.length() > 2_000) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "each message needs text under 2000 characters");
            }
            if (i == messages.size() - 1 && !role.equals("user")) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "the last message must be the question");
            }
            masked.add(new ChatMessage(role, role.equals("user") ? Masker.mask(text) : text));
        }
        String question = messages.get(messages.size() - 1).text();
        if (!queries.query(actor.username(), question, clock.instant())) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "the assistant has reached its question limit for this run");
        }
        return masked;
    }

    public EvalView eval() {
        double accuracy = EvalHarness.accuracy(model, List.of(
                new EvalHarness.Sample("monthly rent", "TRANSFER"),
                new EvalHarness.Sample("grocery market", "GROCERIES"),
                new EvalHarness.Sample("unknown zebra", "OTHER")));
        return new EvalView(accuracy, accuracy >= 0.8);
    }

    public synchronized BudgetView budget(int tokens) {
        if (tokens < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "tokens must be >= 0");
        }
        boolean within = budget.record(tokens, 100, 1);
        return new BudgetView(within, budget.breached(), budget.spentMicros(), budget.calls(), 1_000);
    }

    public synchronized BookView order(ApexPrincipal actor, String symbol, String id, String side, String type, Long limitPrice, long quantity, UUID accountId) {
        String market = market(symbol);
        Account taker = visible(actor, accountId);
        try {
            Side orderSide = Side.valueOf(side);
            OrderType orderType = OrderType.valueOf(type);
            if (orderSide == Side.BUY && orderType == OrderType.LIMIT) {
                long notional = Math.multiplyExact(limitPrice, quantity);
                if (bank.balanceOf(taker.id()) < notional) {
                    throw new DomainException(ErrorCode.INSUFFICIENT_FUNDS, "a limit buy needs balance for price times quantity");
                }
            }
            Order order = new Order(id, orderSide, orderType, limitPrice, quantity);
            orderAccounts.put(id, taker.id().value());
            OrderBook.MatchResult result = bookOf(market).submit(order);
            tradesBy(market).addAll(result.trades());
            List<String> posted = settle(orderSide, taker, result.trades());
            return view(market, result.trades(), result.restedQuantity(), result.cancelledQuantity(), posted);
        } catch (DomainException ex) {
            throw ex;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
        }
    }

    /** Kept for callers that predate the per-market books. */
    public synchronized BookView order(ApexPrincipal actor, String id, String side, String type, Long limitPrice, long quantity, UUID accountId) {
        return order(actor, DEFAULT_MARKET, id, side, type, limitPrice, quantity, accountId);
    }

    public synchronized BookView book(String symbol) {
        return view(market(symbol), List.of(), 0, 0, List.of());
    }

    public synchronized BookView book() {
        return book(DEFAULT_MARKET);
    }

    private static String market(String symbol) {
        return symbol == null || symbol.isBlank() ? DEFAULT_MARKET : symbol.trim().toUpperCase(Locale.ROOT);
    }

    private OrderBook bookOf(String market) {
        return books.computeIfAbsent(market, key -> new OrderBook());
    }

    private List<Trade> tradesBy(String market) {
        return tradesBySymbol.computeIfAbsent(market, key -> new ArrayList<>());
    }

    public List<OutboxMessage> outbox() {
        return outbox.unpublished();
    }

    public synchronized RelayResult relay() {
        List<OutboxMessage> dead = new ArrayList<>();
        int sent = OutboxRelay.publish(outbox, message -> relayed.add(message.payload()), 3, dead);
        return new RelayResult(sent, dead.size(), List.copyOf(relayed));
    }

    public Coverage coverage() {
        KafkaLedgerBridge bridge = kafka.getIfAvailable();
        return new Coverage(
                true, true, true, true, true, true,
                environment.acceptsProfiles(Profiles.of("postgres")),
                bridge != null && bridge.isRunning(),
                ProcessSignals.grafanaUp(properties.getGrafanaUrl()),
                properties.getModelUrl() != null && !properties.getModelUrl().isBlank(),
                ProcessSignals.assistantUp(properties.getAssistantUrl()),
                false, false);
    }

    private List<String> settle(Side takerSide, Account taker, List<Trade> matched) {
        List<String> posted = new ArrayList<>();
        for (Trade trade : matched) {
            UUID makerAccountId = orderAccounts.get(trade.makerId());
            if (makerAccountId == null) {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "the resting order has no account");
            }
            Account maker = bank.find(new AccountId(makerAccountId))
                    .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "resting account not found"));
            Account buyer = takerSide == Side.BUY ? taker : maker;
            Account seller = takerSide == Side.BUY ? maker : taker;
            long notional = Math.multiplyExact(trade.price(), trade.quantity());
            Money money = Money.ofMinor(notional, buyer.currency());
            Transaction transaction = bank.post(new Posting(null, TransactionType.TRANSFER, List.of(
                    new Posting.Leg(buyer.id(), Direction.DEBIT, money),
                    new Posting.Leg(seller.id(), Direction.CREDIT, money)),
                    "trade " + trade.takerId() + "/" + trade.makerId(), null, null, null));
            posted.add(transaction.id().value().toString());
            tradePostings.add(transaction.id().value().toString());
        }
        return posted;
    }

    private Account visible(ApexPrincipal actor, UUID accountId) {
        Account account = bank.find(new AccountId(accountId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "account not found"));
        if (actor.role() == Role.CUSTOMER && !account.ownerRef().equals(actor.username())) {
            throw new DomainException(ErrorCode.NOT_FOUND, "account not found");
        }
        return account;
    }

    private Account required(AccountType type) {
        return bank.findAll().stream()
                .filter(account -> account.type() == type)
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, type + " account is not open"));
    }

    private BookView view(String market, List<Trade> justTraded, long rested, long cancelled, List<String> posted) {
        OrderBook book = bookOf(market);
        return new BookView(market, book.bestBid(), book.bestAsk(), rested, cancelled, justTraded, List.copyOf(tradesBy(market)), posted);
    }

    public record FeePost(long feeMinor, UUID transactionId, long balanceMinor) {
    }

    public record CamtImport(String statementId, int entries, long postedMinor, boolean fresh, UUID transactionId) {
    }

    public record EvalView(double accuracy, boolean pass) {
    }

    public record BudgetView(boolean withinBudget, boolean breached, long spentMicros, long calls, long maxCostMicros) {
    }

    public record AskResult(boolean accepted, String masked, List<String> log) {
    }

    public record ChatMessage(String role, String text) {
    }

    public record ChatReply(String answer) {
    }

    public record BookView(String market, Long bestBid, Long bestAsk, long restedQuantity, long cancelledQuantity,
                            List<Trade> trades, List<Trade> history, List<String> ledgerTransactionIds) {
    }

    public record RelayResult(int published, int dead, List<String> payloads) {
    }

    public record Coverage(
            boolean ledger,
            boolean pricing,
            boolean isoStudySubset,
            boolean offlineAssistant,
            boolean orderBook,
            boolean inProcessOutbox,
            boolean postgresOnThisProcess,
            boolean kafkaConsumer,
            boolean grafanaServer,
            boolean hostedModel,
            boolean assistantService,
            boolean demoVideo,
            boolean mutationScore
    ) {
    }
}
