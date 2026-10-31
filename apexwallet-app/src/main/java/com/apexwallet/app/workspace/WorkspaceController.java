package com.apexwallet.app.workspace;

import com.apexwallet.app.identity.CurrentUser;
import com.apexwallet.app.workspace.WorkspaceService.AskResult;
import com.apexwallet.app.workspace.WorkspaceService.BookView;
import com.apexwallet.app.workspace.WorkspaceService.CamtImport;
import com.apexwallet.app.workspace.WorkspaceService.Coverage;
import com.apexwallet.app.workspace.WorkspaceService.RelayResult;
import com.apexwallet.ai.RiskScore;
import com.apexwallet.app.audit.AuditEvent;
import com.apexwallet.app.messaging.OutboxMessage;
import com.apexwallet.domain.fees.FeeQuote;
import com.apexwallet.domain.fees.LimitDecision;
import com.apexwallet.domain.ledger.ReconciliationReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workspace")
@Profile({"local", "postgres"})
public class WorkspaceController {

    private final WorkspaceService workspace;

    public WorkspaceController(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping("/coverage")
    public Coverage coverage() {
        CurrentUser.require();
        return workspace.coverage();
    }

    @PostMapping("/fees")
    public FeeQuote fee(@Valid @RequestBody FeeRequest request) {
        CurrentUser.require();
        return workspace.fee(request.type(), request.amountMinor(), request.fixedMinor(), request.basisPoints(), request.capMinor());
    }

    @PostMapping("/fees/charge")
    public WorkspaceService.FeePost charge(@Valid @RequestBody FeeChargeRequest request) {
        return workspace.charge(CurrentUser.require(), request.accountId(), request.type(), request.amountMinor(),
                request.fixedMinor(), request.basisPoints(), request.capMinor());
    }

    @PostMapping("/interest")
    public InterestView interest(@Valid @RequestBody InterestRequest request) {
        CurrentUser.require();
        long accrued = workspace.interest(request.principalMinor(), request.annualRate(), request.from(), request.to(), request.convention());
        return new InterestView(accrued);
    }

    @PostMapping("/limits")
    public LimitDecision limit(@Valid @RequestBody LimitRequest request) {
        CurrentUser.require();
        return workspace.limit(request.accountId(), request.amountMinor());
    }

    @GetMapping("/reconciliation")
    public ReconciliationView reconciliation() {
        CurrentUser.require();
        ReconciliationReport report = workspace.reconciliation();
        return new ReconciliationView(report.clean(), report.mismatches(), report.checksumMinor());
    }

    @GetMapping("/audit")
    public List<AuditEvent> audit() {
        CurrentUser.require();
        return workspace.audit();
    }

    @PostMapping("/iban")
    public IbanView iban(@Valid @RequestBody IbanRequest request) {
        CurrentUser.require();
        return new IbanView(workspace.iban(request.iban()));
    }

    @PostMapping("/camt")
    public CamtImport camt(@Valid @RequestBody XmlRequest request) {
        CurrentUser.require();
        return workspace.camt(CurrentUser.require(), request.xml(), request.accountId());
    }

    @PostMapping("/pain")
    public PainView pain(@Valid @RequestBody PainRequest request) {
        CurrentUser.require();
        return new PainView(workspace.pain(request.messageId(), request.endToEndIds(), request.amountMinor()));
    }

    @GetMapping("/eval")
    public WorkspaceService.EvalView eval() {
        CurrentUser.require();
        return workspace.eval();
    }

    @PostMapping("/budget")
    public WorkspaceService.BudgetView budget(@Valid @RequestBody BudgetRequest request) {
        CurrentUser.require();
        return workspace.budget(request.tokens());
    }

    @PostMapping("/categorise")
    public LabelView categorise(@Valid @RequestBody TextRequest request) {
        CurrentUser.require();
        return new LabelView(workspace.categorise(request.text()));
    }

    @PostMapping("/risk")
    public RiskScore risk(@Valid @RequestBody RiskRequest request) {
        CurrentUser.require();
        return workspace.risk(request.amountMinor(), request.paymentsToday(), request.newPayee());
    }

    @PostMapping("/similar")
    public List<String> similar(@Valid @RequestBody TextRequest request) {
        CurrentUser.require();
        return workspace.similar(request.text());
    }

    @PostMapping("/ask")
    public AskResult ask(@Valid @RequestBody TextRequest request) {
        return workspace.ask(CurrentUser.require(), request.text());
    }

    @PostMapping("/chat")
    public WorkspaceService.ChatReply chat(@RequestBody ChatRequest request) {
        return workspace.chat(CurrentUser.require(), request == null ? null : request.messages());
    }

    @PostMapping("/orders")
    public BookView submit(@Valid @RequestBody OrderRequest request) {
        CurrentUser.require();
        return workspace.order(CurrentUser.require(), request.symbol(), request.id(), request.side(), request.type(), request.limitPrice(), request.quantity(), request.accountId());
    }

    @GetMapping("/orders")
    public BookView orders(@RequestParam(required = false) String symbol) {
        CurrentUser.require();
        return workspace.book(symbol);
    }

    @GetMapping("/outbox")
    public List<OutboxMessage> outbox() {
        CurrentUser.require();
        return workspace.outbox();
    }

    @PostMapping("/outbox/relay")
    public RelayResult relay() {
        CurrentUser.require();
        return workspace.relay();
    }

    public record FeeRequest(@NotBlank String type, @NotNull Long amountMinor, long fixedMinor, int basisPoints, long capMinor) {
    }

    public record FeeChargeRequest(@NotBlank String type, @NotNull Long amountMinor, long fixedMinor, int basisPoints, long capMinor, @NotNull java.util.UUID accountId) {
    }

    public record InterestRequest(@NotNull Long principalMinor, @NotBlank String annualRate,
                                  @NotNull LocalDate from, @NotNull LocalDate to, @NotBlank String convention) {
    }

    public record InterestView(long accruedMinor) {
    }

    public record LimitRequest(@NotBlank String accountId, @NotNull Long amountMinor) {
    }

    public record IbanRequest(@NotBlank String iban) {
    }

    public record IbanView(boolean valid) {
    }

    public record XmlRequest(@NotBlank String xml, java.util.UUID accountId) {
    }

    public record BudgetRequest(int tokens) {
    }

    public record PainRequest(@NotBlank String messageId, @NotNull List<String> endToEndIds, @Positive long amountMinor) {
    }

    public record PainView(String xml) {
    }

    public record TextRequest(String text) {
    }

    public record ChatRequest(List<WorkspaceService.ChatMessage> messages) {
    }

    public record LabelView(String label) {
    }

    public record RiskRequest(@NotNull Long amountMinor, int paymentsToday, boolean newPayee) {
    }

    public record OrderRequest(@NotBlank String id, @NotBlank String side, @NotBlank String type, Long limitPrice, @Positive long quantity, @NotNull java.util.UUID accountId, String symbol) {
    }

    public record ReconciliationView(boolean clean, java.util.List<String> mismatches, long checksumMinor) {
    }
}
