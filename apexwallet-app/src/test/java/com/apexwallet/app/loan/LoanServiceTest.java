package com.apexwallet.app.loan;

import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.port.Bank;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.ledger.AccountStatement;
import com.apexwallet.domain.ledger.Ledger;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.ReconciliationReport;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionId;
import com.apexwallet.domain.money.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanServiceTest {

    private final LedgerBank bank = new LedgerBank();
    private final LoanService loans = new LoanService(bank, new MemoryLoanStore());
    private final Account alice = bank.open(Account.open("alice", Currency.EUR, AccountType.CUSTOMER_LIABILITY));
    private final ApexPrincipal customer = new ApexPrincipal(UUID.randomUUID(), "alice", Role.CUSTOMER);
    private final ApexPrincipal supervisor = new ApexPrincipal(UUID.randomUUID(), "sam", Role.SUPERVISOR);

    @Test
    void smallLoanPostsAndRepaymentClearsIt() {
        LoanView granted = loans.request(customer, 10_000);
        assertEquals("ACTIVE", granted.status());
        assertEquals(10_000, bank.balanceOf(alice.id()));
        assertEquals(10_000, bookBalance());

        LoanView part = loans.repay(customer, granted.id(), 4_000);
        assertEquals("ACTIVE", part.status());
        assertEquals(6_000, part.outstandingMinor());
        assertEquals(6_000, bank.balanceOf(alice.id()));

        LoanView done = loans.repay(customer, granted.id(), 6_000);
        assertEquals("REPAID", done.status());
        assertEquals(0, done.outstandingMinor());
        assertEquals(0, bank.balanceOf(alice.id()));
        assertEquals(0, bookBalance());
    }

    @Test
    void largerLoanWaitsForSam() {
        LoanView waiting = loans.request(customer, 60_000);
        assertEquals("PENDING", waiting.status());
        assertEquals(0, bank.balanceOf(alice.id()));

        LoanView granted = loans.approve(supervisor, waiting.id());
        assertEquals("ACTIVE", granted.status());
        assertEquals(60_000, granted.outstandingMinor());
        assertEquals(60_000, bank.balanceOf(alice.id()));
        assertEquals(60_000, bookBalance());
    }

    @Test
    void aSecondOpenLoanIsRefused() {
        loans.request(customer, 1_000);
        assertThrows(DomainException.class, () -> loans.request(customer, 1_000));
    }

    @Test
    void repaymentCannotExceedWhatIsStillOpen() {
        LoanView granted = loans.request(customer, 1_000);
        assertThrows(DomainException.class, () -> loans.repay(customer, granted.id(), 1_001));
    }

    private long bookBalance() {
        return bank.findAll().stream()
                .filter(account -> account.type() == AccountType.LOAN_RECEIVABLE)
                .map(account -> bank.balanceOf(account.id()))
                .findFirst()
                .orElseThrow();
    }

    private static final class LedgerBank implements Bank {
        private final Ledger ledger = new Ledger();

        @Override
        public Account open(Account account) {
            return ledger.open(account);
        }

        @Override
        public Optional<Account> find(AccountId id) {
            return ledger.find(id);
        }

        @Override
        public List<Account> findByOwner(String ownerRef) {
            return ledger.findByOwner(ownerRef);
        }

        @Override
        public List<Account> findAll() {
            return ledger.all();
        }

        @Override
        public Transaction post(Posting posting) {
            return ledger.post(posting);
        }

        @Override
        public Transaction reverse(TransactionId id, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long balanceOf(AccountId id) {
            return ledger.balanceOf(id);
        }

        @Override
        public AccountStatement statement(AccountId id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Account changeOverdraft(AccountId id, long newLimitMinor, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long versionOf(AccountId id) {
            return 0;
        }

        @Override
        public ReconciliationReport reconcile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> descriptions() {
            return List.of();
        }
    }
}
