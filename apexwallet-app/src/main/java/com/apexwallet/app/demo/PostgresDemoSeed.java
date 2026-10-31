package com.apexwallet.app.demo;

import com.apexwallet.app.identity.AppUser;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.port.Bank;
import com.apexwallet.app.port.UserDirectory;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.ledger.Direction;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.TransactionType;
import com.apexwallet.domain.money.Currency;
import com.apexwallet.domain.money.Money;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Study users for the postgres profile. The same passwords as the local desk, stored as BCrypt hashes.
 * A second start leaves the existing rows alone.
 */
@Component
@Profile("postgres")
@Order(0)
public class PostgresDemoSeed implements ApplicationRunner {

    private final UserDirectory users;
    private final Bank bank;
    private final PasswordEncoder encoder;

    public PostgresDemoSeed(UserDirectory users, Bank bank, PasswordEncoder encoder) {
        this.users = users;
        this.bank = bank;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByUsername("alice").isEmpty()) {
            save("alice", DemoWorld.ALICE_PASSWORD, Role.CUSTOMER);
            save("bob", DemoWorld.BOB_PASSWORD, Role.CUSTOMER);
            save("sam", DemoWorld.SAM_PASSWORD, Role.SUPERVISOR);
            save("tim", "tim-teller-1", Role.TELLER);
            save("aud", "aud-auditor-1", Role.AUDITOR);
        }
        boolean aliceOpen = bank.findAll().stream()
                .anyMatch(account -> "alice".equals(account.ownerRef()) && account.type() == AccountType.CUSTOMER_LIABILITY);
        boolean loanBook = bank.findAll().stream()
                .anyMatch(account -> account.type() == AccountType.LOAN_RECEIVABLE);
        if (!loanBook) {
            bank.open(Account.open("bank", Currency.EUR, AccountType.LOAN_RECEIVABLE));
        }
        if (aliceOpen) {
            return;
        }
        Account cash = bank.open(Account.open("bank", Currency.EUR, AccountType.BANK_CASH));
        bank.open(Account.open("bank", Currency.EUR, AccountType.FEE_INCOME));
        Account alice = bank.open(Account.open("alice", Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        bank.open(Account.open("bob", Currency.EUR, AccountType.CUSTOMER_LIABILITY));
        Money funding = Money.ofMinor(200_000, Currency.EUR);
        bank.post(new Posting(null, TransactionType.DEPOSIT, List.of(
                new Posting.Leg(cash.id(), Direction.DEBIT, funding),
                new Posting.Leg(alice.id(), Direction.CREDIT, funding)),
                "demo funding", null, null, null));
    }

    private void save(String username, String password, Role role) {
        users.save(new AppUser(UUID.randomUUID(), username, encoder.encode(password), role, 0, null));
    }
}
