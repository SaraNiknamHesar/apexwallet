package com.apexwallet.app.account;

import com.apexwallet.app.api.AccountView;
import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.app.identity.Role;
import com.apexwallet.app.port.Bank;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.account.AccountType;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.money.Currency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final Bank bank;

    public AccountService(Bank bank) {
        this.bank = bank;
    }

    @Transactional
    public AccountView open(ApexPrincipal actor, String requestedOwner, String currency, String type, String externalNumber) {
        if (actor.role() != Role.CUSTOMER && actor.role() != Role.TELLER) {
            throw new DomainException(ErrorCode.FORBIDDEN, "role cannot open accounts");
        }
        String owner = actor.role() == Role.CUSTOMER ? actor.username() : requestedOwner;
        if (owner == null || owner.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "ownerRef is required");
        }
        Account account = Account.open(owner, Currency.fromIso(currency), AccountType.valueOf(type))
                .withExternalNumber(externalNumber);
        return toView(bank.open(account));
    }

    public AccountView get(ApexPrincipal actor, AccountId id) {
        return toView(visible(actor, id));
    }

    public List<AccountView> list(ApexPrincipal actor) {
        List<Account> accounts = actor.role() == Role.CUSTOMER
                ? bank.findByOwner(actor.username())
                : bank.findAll();
        return accounts.stream().map(this::toView).toList();
    }

    public AccountView changeOverdraft(ApexPrincipal actor, AccountId id, long limit, long expectedVersion) {
        visible(actor, id);
        return toView(bank.changeOverdraft(id, limit, expectedVersion));
    }

    public Account visible(ApexPrincipal actor, AccountId id) {
        Account account = bank.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "account not found"));
        if (actor.role() == Role.CUSTOMER && !account.ownerRef().equals(actor.username())) {
            throw new DomainException(ErrorCode.NOT_FOUND, "account not found");
        }
        return account;
    }

    private AccountView toView(Account account) {
        return new AccountView(
                account.id().value(),
                account.ownerRef(),
                account.currency().isoCode(),
                account.type().name(),
                account.status().name(),
                bank.balanceOf(account.id()),
                account.externalAccountNumber(),
                bank.versionOf(account.id()));
    }
}
