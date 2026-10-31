package com.apexwallet.app.loan;

import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.port.Bank;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LoanService {

    /** Posted at once. A larger study loan waits for a supervisor. */
    public static final long IMMEDIATE_LIMIT_MINOR = 50_000;
    /** Hard cap for this sample. There is no credit check behind it. */
    public static final long MAX_MINOR = 200_000;

    private final Bank bank;
    private final LoanStore loans;

    public LoanService(Bank bank, LoanStore loans) {
        this.bank = bank;
        this.loans = loans;
    }

    @Transactional
    public LoanView request(ApexPrincipal actor, long amountMinor) {
        if (actor.role() != Role.CUSTOMER) {
            throw new DomainException(ErrorCode.FORBIDDEN, "only a customer can ask for a loan");
        }
        if (amountMinor <= 0 || amountMinor > MAX_MINOR) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "a study loan is from 0.01 to 2000.00 EUR");
        }
        if (openLoan(actor.id())) {
            throw new DomainException(ErrorCode.DUPLICATE, "repay or finish the open loan first");
        }
        Account customer = customerAccount(actor.username());
        if (amountMinor > IMMEDIATE_LIMIT_MINOR) {
            Loan pending = new Loan(
                    UUID.randomUUID(), actor.id(), actor.username(), customer.id().value(),
                    amountMinor, 0, Currency.EUR.isoCode(), LoanStatus.PENDING, null);
            loans.save(pending);
            return LoanView.from(pending);
        }
        disburse(customer, amountMinor);
        Loan active = new Loan(
                UUID.randomUUID(), actor.id(), actor.username(), customer.id().value(),
                amountMinor, amountMinor, Currency.EUR.isoCode(), LoanStatus.ACTIVE, null);
        loans.save(active);
        return LoanView.from(active);
    }

    @Transactional
    public LoanView approve(ApexPrincipal actor, UUID loanId) {
        Loan loan = load(loanId);
        if (actor.role() != Role.SUPERVISOR) {
            throw new DomainException(ErrorCode.FORBIDDEN, "only a supervisor can approve a loan");
        }
        if (actor.id().equals(loan.borrowerId())) {
            throw new DomainException(ErrorCode.SELF_APPROVAL, "the borrower cannot approve their own loan");
        }
        if (loan.status() != LoanStatus.PENDING) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "loan is " + loan.status());
        }
        Account customer = bank.find(new AccountId(loan.accountId()))
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found"));
        disburse(customer, loan.principalMinor());
        Loan active = loan.withOutstanding(loan.principalMinor(), LoanStatus.ACTIVE, actor.id());
        loans.save(active);
        return LoanView.from(active);
    }

    @Transactional
    public LoanView reject(ApexPrincipal actor, UUID loanId) {
        Loan loan = load(loanId);
        if (actor.role() != Role.SUPERVISOR) {
            throw new DomainException(ErrorCode.FORBIDDEN, "only a supervisor can reject a loan");
        }
        if (loan.status() != LoanStatus.PENDING) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "loan is " + loan.status());
        }
        Loan rejected = loan.withOutstanding(0, LoanStatus.REJECTED, actor.id());
        loans.save(rejected);
        return LoanView.from(rejected);
    }

    @Transactional
    public LoanView repay(ApexPrincipal actor, UUID loanId, long amountMinor) {
        Loan loan = load(loanId);
        if (actor.role() != Role.CUSTOMER || !actor.id().equals(loan.borrowerId())) {
            throw new DomainException(ErrorCode.FORBIDDEN, "only the borrower can repay");
        }
        if (loan.status() != LoanStatus.ACTIVE) {
            throw new DomainException(ErrorCode.ILLEGAL_TRANSITION, "loan is " + loan.status());
        }
        if (amountMinor <= 0 || amountMinor > loan.outstandingMinor()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "repay at most the amount still open");
        }
        Account customer = bank.find(new AccountId(loan.accountId()))
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found"));
        Money money = Money.ofMinor(amountMinor, Currency.EUR);
        bank.post(new Posting(null, TransactionType.LOAN, List.of(
                new Posting.Leg(customer.id(), Direction.DEBIT, money),
                new Posting.Leg(loanBook().id(), Direction.CREDIT, money)),
                "loan repayment", null, null, null));
        long left = loan.outstandingMinor() - amountMinor;
        Loan next = loan.withOutstanding(left, left == 0 ? LoanStatus.REPAID : LoanStatus.ACTIVE, loan.checkerId());
        loans.save(next);
        return LoanView.from(next);
    }

    public List<LoanView> list(ApexPrincipal actor) {
        List<Loan> rows = actor.role() == Role.CUSTOMER ? loans.findByBorrower(actor.id()) : loans.all();
        return rows.stream().map(LoanView::from).toList();
    }

    private void disburse(Account customer, long amountMinor) {
        Money money = Money.ofMinor(amountMinor, Currency.EUR);
        bank.post(new Posting(null, TransactionType.LOAN, List.of(
                new Posting.Leg(loanBook().id(), Direction.DEBIT, money),
                new Posting.Leg(customer.id(), Direction.CREDIT, money)),
                "loan", null, null, null));
    }

    private Account loanBook() {
        return bank.findAll().stream()
                .filter(account -> account.type() == AccountType.LOAN_RECEIVABLE && account.currency() == Currency.EUR)
                .findFirst()
                .orElseGet(() -> bank.open(Account.open("bank", Currency.EUR, AccountType.LOAN_RECEIVABLE)));
    }

    private Account customerAccount(String username) {
        return bank.findByOwner(username).stream()
                .filter(account -> account.type() == AccountType.CUSTOMER_LIABILITY && account.currency() == Currency.EUR)
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "open a EUR account first"));
    }

    private boolean openLoan(UUID borrowerId) {
        return loans.findByBorrower(borrowerId).stream()
                .anyMatch(loan -> loan.status() == LoanStatus.PENDING || loan.status() == LoanStatus.ACTIVE);
    }

    private Loan load(UUID id) {
        return loans.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "loan not found"));
    }
}
