package com.apexwallet.app.ledger;

import com.apexwallet.app.crypto.EncryptedValue;
import com.apexwallet.app.crypto.FieldEncryptor;
import com.apexwallet.app.messaging.MemoryOutbox;
import com.apexwallet.app.messaging.OutboxMessage;
import com.apexwallet.app.port.Bank;
import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.ledger.AccountStatement;
import com.apexwallet.domain.ledger.Ledger;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.Reconciliation;
import com.apexwallet.domain.ledger.ReconciliationReport;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class LocalBank implements Bank {

    private final Ledger ledger = new Ledger();
    private final FieldEncryptor encryptor;
    private final MemoryOutbox outbox;
    private final ConcurrentHashMap<AccountId, EncryptedValue> protectedNumbers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AccountId, String> numberHashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AccountId, Long> versions = new ConcurrentHashMap<>();

    public LocalBank(FieldEncryptor encryptor, MemoryOutbox outbox) {
        this.encryptor = encryptor;
        this.outbox = outbox;
    }

    @Override
    public synchronized Account open(Account account) {
        Account stored = protect(account);
        ledger.open(stored);
        versions.put(stored.id(), 0L);
        return reveal(stored);
    }

    @Override
    public synchronized Optional<Account> find(AccountId id) {
        return ledger.find(id).map(this::reveal);
    }

    @Override
    public synchronized List<Account> findByOwner(String ownerRef) {
        return ledger.findByOwner(ownerRef).stream().map(this::reveal).toList();
    }

    @Override
    public synchronized List<Account> findAll() {
        return ledger.all().stream().map(this::reveal).toList();
    }

    @Override
    public synchronized Transaction post(Posting posting) {
        Transaction posted = ledger.post(posting);
        String accountKey = posted.entries().isEmpty()
                ? "ledger"
                : posted.entries().get(0).accountId().value().toString();
        outbox.add(new OutboxMessage(posted.id().value().toString(), accountKey, "POSTED|" + posted.id().value(), 0));
        return posted;
    }

    @Override
    public synchronized Transaction reverse(TransactionId id, String description) {
        return ledger.reverse(id, description);
    }

    @Override
    public synchronized long balanceOf(AccountId id) {
        return ledger.balanceOf(id);
    }

    @Override
    public synchronized AccountStatement statement(AccountId id) {
        return ledger.statement(id);
    }

    @Override
    public synchronized Account changeOverdraft(AccountId id, long newLimitMinor, long expectedVersion) {
        Account account = ledger.find(id)
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found"));
        long current = versions.getOrDefault(id, 0L);
        if (current != expectedVersion) {
            throw new DomainException(ErrorCode.OPTIMISTIC_LOCK,
                    "version " + expectedVersion + " is stale; current is " + current);
        }
        Account updated = account.withOverdraft(newLimitMinor);
        ledger.replace(updated);
        versions.put(id, current + 1);
        return reveal(updated);
    }

    @Override
    public synchronized long versionOf(AccountId id) {
        if (ledger.find(id).isEmpty()) {
            throw new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "account not found");
        }
        return versions.getOrDefault(id, 0L);
    }

    @Override
    public synchronized ReconciliationReport reconcile() {
        return Reconciliation.compare(ledger.cachedByAccount(), ledger.foldedByAccount());
    }

    @Override
    public synchronized List<String> descriptions() {
        return ledger.descriptions();
    }

    public Optional<EncryptedValue> ciphertext(AccountId id) {
        return Optional.ofNullable(protectedNumbers.get(id));
    }

    private Account protect(Account account) {
        if (account.externalAccountNumber() == null || account.externalAccountNumber().isBlank()) {
            return account.withExternalNumber(null);
        }
        EncryptedValue encrypted = encryptor.encrypt(account.externalAccountNumber());
        protectedNumbers.put(account.id(), encrypted);
        numberHashes.put(account.id(), encryptor.hash(account.externalAccountNumber()));
        return account.withExternalNumber(null);
    }

    private Account reveal(Account account) {
        EncryptedValue encrypted = protectedNumbers.get(account.id());
        if (encrypted == null) {
            return account;
        }
        return account.withExternalNumber(encryptor.decrypt(encrypted));
    }
}
