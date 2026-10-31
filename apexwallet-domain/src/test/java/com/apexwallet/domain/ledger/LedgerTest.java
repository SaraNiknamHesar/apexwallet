package com.apexwallet.domain.ledger;

import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountStatus;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {

    @Test
    void unbalancedPostDoesNotChangeTheJournal() {
        Ledger ledger = funded();
        int before = ledger.transactionCount();
        DomainException error = assertThrows(DomainException.class, () -> ledger.post(new Posting(
                TransactionId.random(),
                TransactionType.TRANSFER,
                List.of(
                        new Posting.Leg(alice(ledger).id(), Direction.DEBIT, Money.ofMinor(10, Currency.EUR)),
                        new Posting.Leg(bob(ledger).id(), Direction.CREDIT, Money.ofMinor(9, Currency.EUR))),
                "bad",
                null,
                null,
                null)));
        assertEquals(ErrorCode.UNBALANCED_TRANSACTION, error.code());
        assertEquals(before, ledger.transactionCount());
    }

    @Test
    void transferUpdatesDerivedBalancesAndRebuildMatchesCache() {
        Ledger ledger = funded();
        Account alice = alice(ledger);
        Account bob = bob(ledger);
        ledger.post(transfer(alice, bob, 2_500, "rent"));
        assertEquals(7_500, ledger.balanceOf(alice.id()));
        assertEquals(2_500, ledger.balanceOf(bob.id()));
        assertEquals(0, ledger.netByCurrency().get(Currency.EUR).longValue());
        assertEquals(7_500, ledger.rebuildBalances().get(alice.id()).longValue());
    }

    @Test
    void insufficientFundsAndFrozenAccountAreRejected() {
        Ledger ledger = funded();
        Account alice = alice(ledger);
        Account bob = bob(ledger);
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS,
                assertThrows(DomainException.class, () -> ledger.post(transfer(alice, bob, 20_000, "too much"))).code());
        ledger.replace(alice.withStatus(AccountStatus.FROZEN));
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE,
                assertThrows(DomainException.class, () -> ledger.post(transfer(alice, bob, 100, "frozen"))).code());
    }

    @Test
    void reversalKeepsBothSidesVisibleAndNetsToZero() {
        Ledger ledger = funded();
        Account alice = alice(ledger);
        Account bob = bob(ledger);
        Transaction posted = ledger.post(transfer(alice, bob, 2_000, "gift"));
        Transaction reversal = ledger.reverse(posted.id(), "mistake");
        assertEquals(TransactionStatus.REVERSED, ledger.findTransaction(posted.id()).orElseThrow().status());
        assertEquals(TransactionStatus.POSTED, reversal.status());
        assertEquals(10_000, ledger.balanceOf(alice.id()));
        assertEquals(0, ledger.balanceOf(bob.id()));
        assertEquals(3, ledger.entries(alice.id()).size());
        assertEquals(0, ledger.netByCurrency().get(Currency.EUR).longValue());
        assertEquals(ErrorCode.ILLEGAL_TRANSITION,
                assertThrows(DomainException.class, () -> ledger.reverse(posted.id(), "again")).code());
    }

    @Test
    void sameIdempotencyKeyPostsOnce() {
        Ledger ledger = funded();
        Posting posting = new Posting(
                TransactionId.random(),
                TransactionType.TRANSFER,
                List.of(
                        new Posting.Leg(alice(ledger).id(), Direction.DEBIT, Money.ofMinor(100, Currency.EUR)),
                        new Posting.Leg(bob(ledger).id(), Direction.CREDIT, Money.ofMinor(100, Currency.EUR))),
                "once",
                null,
                "key-1",
                null);
        Transaction first = ledger.post(posting);
        Transaction second = ledger.post(posting);
        assertEquals(first.id(), second.id());
        assertEquals(9_900, ledger.balanceOf(alice(ledger).id()));
    }

    @Test
    void statementIsOrderedAndExportIsRereadable() throws Exception {
        Ledger ledger = funded();
        ledger.post(transfer(alice(ledger), bob(ledger), 100, "a"));
        ledger.post(transfer(alice(ledger), bob(ledger), 50, "b"));
        AccountStatement statement = ledger.statement(alice(ledger).id());
        assertEquals(3, statement.lines().size());
        assertEquals(9_850, statement.closingBalanceMinor());
        assertTrue(statement.lines().get(0).runningBalanceMinor() > statement.lines().get(2).runningBalanceMinor());
        Path path = Files.createTempFile("apex-statement", ".txt");
        StatementFiles.write(statement, path);
        String text = StatementFiles.read(path);
        assertTrue(text.contains("currency=EUR"));
        assertTrue(text.contains("closing=9850"));
        StatementFiles.write(statement, path);
        assertEquals(text, StatementFiles.read(path));
    }

    @Test
    void mixingCurrenciesIsRejected() {
        Ledger ledger = new Ledger();
        Account eur = ledger.open(Account.open("alice", Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        Account jpy = ledger.open(Account.open("alice", Currency.JPY, AccountType.CUSTOMER_LIABILITY));
        assertEquals(ErrorCode.CURRENCY_MISMATCH, assertThrows(DomainException.class, () -> ledger.post(new Posting(
                null,
                TransactionType.TRANSFER,
                List.of(
                        new Posting.Leg(eur.id(), Direction.DEBIT, Money.ofMinor(10, Currency.EUR)),
                        new Posting.Leg(jpy.id(), Direction.CREDIT, Money.ofMinor(10, Currency.JPY))),
                "fx",
                null,
                null,
                null))).code());
    }

    @Property(tries = 30)
    void randomTransfersHoldInvariants(@ForAll("moves") List<Integer> moves) {
        Ledger ledger = funded();
        Account alice = alice(ledger);
        Account bob = bob(ledger);
        for (int amount : moves) {
            Account from = amount % 2 == 0 ? alice : bob;
            Account to = from == alice ? bob : alice;
            try {
                ledger.post(transfer(from, to, amount, "p"));
            } catch (DomainException ex) {
                assertEquals(ErrorCode.INSUFFICIENT_FUNDS, ex.code());
            }
        }
        ledger.rebuildBalances();
        assertEquals(0L, ledger.netByCurrency().get(Currency.EUR));
        assertTrue(ledger.balanceOf(alice.id()) >= 0);
        assertTrue(ledger.balanceOf(bob.id()) >= 0);
    }

    @Provide
    Arbitrary<List<Integer>> moves() {
        return Arbitraries.integers().between(1, 3_000).list().ofMinSize(1).ofMaxSize(20);
    }

    private static Posting transfer(Account from, Account to, long amountMinor, String description) {
        Money money = Money.ofMinor(amountMinor, from.currency());
        return new Posting(null, TransactionType.TRANSFER, List.of(
                new Posting.Leg(from.id(), Direction.DEBIT, money),
                new Posting.Leg(to.id(), Direction.CREDIT, money)),
                description, null, null, null);
    }

    private static Ledger funded() {
        Ledger ledger = new Ledger();
        Account cash = ledger.open(Account.open("bank", Currency.EUR, AccountType.BANK_CASH));
        Account alice = ledger.open(Account.open("alice", Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        ledger.open(Account.open("bob", Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        deposit(ledger, cash, alice, 10_000);
        return ledger;
    }

    private static void deposit(Ledger ledger, Account cash, Account customer, long amount) {
        Money money = Money.ofMinor(amount, Currency.EUR);
        ledger.post(new Posting(null, TransactionType.DEPOSIT, List.of(
                new Posting.Leg(cash.id(), Direction.DEBIT, money),
                new Posting.Leg(customer.id(), Direction.CREDIT, money)),
                "fund", null, null, null));
    }

    private static Account alice(Ledger ledger) {
        return ledger.findByOwner("alice").get(0);
    }

    private static Account bob(Ledger ledger) {
        return ledger.findByOwner("bob").get(0);
    }
}
