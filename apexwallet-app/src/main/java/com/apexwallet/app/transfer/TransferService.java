package com.apexwallet.app.transfer;

import com.apexwallet.app.api.ApprovalView;
import com.apexwallet.app.api.TransferCommand;
import com.apexwallet.app.api.TransferView;
import com.apexwallet.app.approval.ApprovalStatus;
import com.apexwallet.app.approval.ApprovalStore;
import com.apexwallet.app.approval.PendingTransfer;
import com.apexwallet.app.audit.Audited;
import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.port.Bank;
import com.apexwallet.app.security.WindowRateLimiter;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.fees.DailyLimit;
import com.apexwallet.domain.fees.LimitDecision;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;
import com.apexwallet.iso20022.IbanCheck;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private final Bank bank;
    private final ApprovalStore approvals;
    private final ApexProperties properties;
    private final WindowRateLimiter limiter;
    private final Clock clock;
    private final DailyLimit dailyLimit;

    public TransferService(Bank bank, ApprovalStore approvals, ApexProperties properties,
                           WindowRateLimiter limiter, Clock clock) {
        this.bank = bank;
        this.approvals = approvals;
        this.dailyLimit = new DailyLimit("transfer-daily", properties.getDailyLimitMinor());
        this.properties = properties;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Audited(action = "TRANSFER")
    @Transactional
    public TransferView transfer(ApexPrincipal actor, TransferCommand command) {
        if (actor.role() != Role.CUSTOMER && actor.role() != Role.TELLER) {
            throw new DomainException(ErrorCode.FORBIDDEN, "role cannot move money");
        }
        if (!limiter.allow("user:" + actor.username(), properties.getTransfersPerMinute(), Duration.ofMinutes(1), clock.instant())) {
            throw new DomainException(ErrorCode.RATE_LIMITED, "too many transfers");
        }
        Account from = ownedSource(actor, command.fromAccountId());
        Account to = bank.find(new AccountId(command.toAccountId()))
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found"));
        Currency currency = Currency.fromIso(command.currency());
        Money amount = Money.ofMinor(command.amountMinor(), currency);
        if (command.amountMinor() >= properties.getApprovalThresholdMinor()) {
            PendingTransfer pending = new PendingTransfer(
                    UUID.randomUUID(), actor.id(), actor.username(), from.id().value(), to.id().value(),
                    amount.amountMinor(), currency.isoCode(), command.description(), ApprovalStatus.PENDING, null);
            approvals.save(pending);
            return new TransferView(pending.id(), "PENDING_APPROVAL", null, null);
        }
        consumeDaily(from.id(), amount.amountMinor());
        Transaction posted = bank.post(legs(from, to, amount, command.description(), TransactionType.TRANSFER, null));
        return view(posted, from.id(), to.id());
    }

    @Audited(action = "APPROVE")
    @Transactional
    public TransferView approve(ApexPrincipal actor, UUID requestId) {
        PendingTransfer pending = load(requestId);
        if (actor.role() != Role.SUPERVISOR) {
            throw new DomainException(ErrorCode.FORBIDDEN, "only a supervisor can approve");
        }
        if (actor.id().equals(pending.makerId())) {
            throw new DomainException(ErrorCode.SELF_APPROVAL, "the maker cannot approve their own transfer");
        }
        if (pending.status() != ApprovalStatus.PENDING) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "approval is " + pending.status());
        }
        Account from = bank.find(new AccountId(pending.fromAccountId()))
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found"));
        Account to = bank.find(new AccountId(pending.toAccountId()))
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found"));
        Money amount = Money.ofMinor(pending.amountMinor(), Currency.fromIso(pending.currency()));
        consumeDaily(from.id(), amount.amountMinor());
        Transaction posted = bank.post(legs(from, to, amount, pending.description(), TransactionType.TRANSFER, null));
        approvals.save(pending.decide(ApprovalStatus.APPROVED, actor.id()));
        return view(posted, from.id(), to.id());
    }

    @Transactional
    public TransferView reject(ApexPrincipal actor, UUID requestId) {
        PendingTransfer pending = load(requestId);
        if (actor.role() != Role.SUPERVISOR) {
            throw new DomainException(ErrorCode.FORBIDDEN, "only a supervisor can reject");
        }
        if (pending.status() != ApprovalStatus.PENDING) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "decision is terminal: " + pending.status());
        }
        approvals.save(pending.decide(ApprovalStatus.REJECTED, actor.id()));
        return new TransferView(pending.id(), "REJECTED", null, null);
    }

    /** Confirmation of Payee: turn an IBAN into the account that owns it. */
    public PayeeView resolve(ApexPrincipal actor, String iban) {
        String compact = iban == null ? "" : iban.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IbanCheck.valid(compact)) {
            return new PayeeView(false, false, null, null, null, compact, "That is not a valid IBAN.");
        }
        Optional<Account> match = bank.findAll().stream()
                .filter(account -> account.externalAccountNumber() != null
                        && account.externalAccountNumber().replace(" ", "").equalsIgnoreCase(compact))
                .findFirst();
        if (match.isEmpty()) {
            return new PayeeView(true, false, null, null, null, compact, "This IBAN is well formed, but no account here uses it.");
        }
        Account account = match.get();
        return new PayeeView(true, true, account.ownerRef(), account.id().value(), account.currency().isoCode(),
                compact, null);
    }

    public List<ApprovalView> queue(ApexPrincipal actor) {
        if (actor.role() != Role.SUPERVISOR) {
            throw new DomainException(ErrorCode.FORBIDDEN, "only a supervisor can read the approval queue");
        }
        return approvals.pending().stream()
                .map(row -> new ApprovalView(
                        row.id(),
                        row.makerUsername(),
                        row.fromAccountId(),
                        row.toAccountId(),
                        row.amountMinor(),
                        row.currency(),
                        row.description(),
                        row.status().name()))
                .toList();
    }

    private void consumeDaily(AccountId accountId, long amountMinor) {
        LimitDecision decision = dailyLimit.tryConsume(accountId.value().toString(), LocalDate.now(clock), amountMinor);
        if (!decision.allowed()) {
            throw new DomainException(ErrorCode.DAILY_LIMIT,
                    "daily limit exceeded; remaining " + decision.remainingMinor());
        }
    }

    private PendingTransfer load(UUID id) {
        return approvals.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "approval not found"));
    }

    private Account ownedSource(ApexPrincipal actor, UUID accountId) {
        Account account = bank.find(new AccountId(accountId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "account not found"));
        if (actor.role() == Role.CUSTOMER && !account.ownerRef().equals(actor.username())) {
            throw new DomainException(ErrorCode.NOT_FOUND, "account not found");
        }
        return account;
    }

    private static Posting legs(Account from, Account to, Money amount, String description,
                                 TransactionType type, com.apexwallet.domain.ledger.TransactionId reverses) {
        return new Posting(null, type, List.of(
                new Posting.Leg(from.id(), Direction.DEBIT, amount),
                new Posting.Leg(to.id(), Direction.CREDIT, amount)),
                description, reverses, null, null);
    }

    private TransferView view(Transaction posted, com.apexwallet.domain.account.AccountId from, com.apexwallet.domain.account.AccountId to) {
        return new TransferView(posted.id().value(), posted.status().name(), bank.balanceOf(from), bank.balanceOf(to));
    }

    /** What the transfer screen shows once an IBAN is typed. */
    public record PayeeView(boolean valid, boolean found, String ownerRef, UUID accountId, String currency,
                            String iban, String reason) {
    }
}
