package com.apexwallet.app.demo;

import com.apexwallet.app.identity.AppUser;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.port.Bank;
import com.apexwallet.app.port.UserDirectory;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Local demo users only. Passwords here are for the study profile and must never be reused anywhere real.
 */
@Component
@Profile("local")
public class DemoWorld implements ApplicationRunner {

    public static final String ALICE_PASSWORD = "alice-pass-1";
    public static final String SAM_PASSWORD = "sam-supervisor-1";
    public static final String BOB_PASSWORD = "bob-pass-1111";

    private final UserDirectory users;
    private final Bank bank;
    private final PasswordEncoder encoder;
    private AccountId aliceAccount;
    private AccountId bobAccount;

    public DemoWorld(UserDirectory users, Bank bank, PasswordEncoder encoder) {
        this.users = users;
        this.bank = bank;
        this.encoder = encoder;
    }

    public AccountId aliceAccount() {
        return aliceAccount;
    }

    public AccountId bobAccount() {
        return bobAccount;
    }

    @Override
    public void run(ApplicationArguments args) {
        save("alice", ALICE_PASSWORD, Role.CUSTOMER);
        save("bob", BOB_PASSWORD, Role.CUSTOMER);
        save("sam", SAM_PASSWORD, Role.SUPERVISOR);
        save("tim", "tim-teller-1", Role.TELLER);
        save("aud", "aud-auditor-1", Role.AUDITOR);
        Account cash = bank.open(Account.open("bank", Currency.EUR, AccountType.BANK_CASH));
        bank.open(Account.open("bank", Currency.EUR, AccountType.FEE_INCOME));
        bank.open(Account.open("bank", Currency.EUR, AccountType.LOAN_RECEIVABLE));
        Account alice = bank.open(Account.open("alice", Currency.EUR, AccountType.CUSTOMER_LIABILITY)
                .withExternalNumber("GB82WEST12345698765432"));
        Account bob = bank.open(Account.open("bob", Currency.EUR, AccountType.CUSTOMER_LIABILITY)
                .withExternalNumber("GB33BUKB20201555555555"));
        history(cash, alice);
        this.aliceAccount = alice.id();
        this.bobAccount = bob.id();
    }

    private void history(Account cash, Account alice) {
        // Credits 275_000 and debits 75_000, so Alice still opens at 2_000.00 EUR.
        move(cash, alice, true, 42_000, "Salary", 2026, 1, 5);
        move(cash, alice, false, 12_000, "Rent", 2026, 1, 13);
        move(cash, alice, true, 38_000, "Salary", 2026, 2, 10);
        move(cash, alice, false, 9_000, "Groceries", 2026, 2, 18);
        move(cash, alice, true, 46_000, "Salary", 2026, 3, 4);
        move(cash, alice, false, 15_000, "Utilities", 2026, 3, 12);
        move(cash, alice, true, 30_000, "Salary", 2026, 4, 2);
        move(cash, alice, false, 8_000, "Travel", 2026, 4, 10);
        move(cash, alice, true, 51_000, "Salary", 2026, 5, 8);
        move(cash, alice, false, 18_000, "Shopping", 2026, 5, 16);
        move(cash, alice, true, 22_000, "Salary", 2026, 6, 6);
        move(cash, alice, false, 6_000, "Dining", 2026, 6, 14);
        move(cash, alice, true, 16_000, "Salary", 2026, 7, 5);
        move(cash, alice, false, 7_000, "Transfer", 2026, 8, 3);
        move(cash, alice, true, 30_000, "Salary", 2026, 9, 8);
    }

    private void move(Account cash, Account alice, boolean inbound, long minor, String description, int year, int month, int day) {
        Money money = Money.ofMinor(minor, Currency.EUR);
        Direction aliceDirection = inbound ? Direction.CREDIT : Direction.DEBIT;
        Direction cashDirection = inbound ? Direction.DEBIT : Direction.CREDIT;
        Instant bookedAt = LocalDate.of(year, month, day).atTime(12, 0).toInstant(ZoneOffset.UTC);
        bank.post(new Posting(null, inbound ? TransactionType.DEPOSIT : TransactionType.TRANSFER, List.of(
                new Posting.Leg(cash.id(), cashDirection, money),
                new Posting.Leg(alice.id(), aliceDirection, money)),
                description, null, null, bookedAt));
    }

    private void save(String username, String password, Role role) {
        users.save(new AppUser(UUID.randomUUID(), username, encoder.encode(password), role, 0, null));
    }
}
