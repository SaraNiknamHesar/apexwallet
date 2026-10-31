package com.apexwallet.app.port;

import com.apexwallet.domain.account.Account;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.ledger.AccountStatement;
import com.apexwallet.domain.ledger.Posting;
import com.apexwallet.domain.ledger.ReconciliationReport;
import com.apexwallet.domain.ledger.Transaction;
import com.apexwallet.domain.ledger.TransactionId;

import java.util.List;
import java.util.Optional;

public interface Bank {

    Account open(Account account);

    Optional<Account> find(AccountId id);

    List<Account> findByOwner(String ownerRef);

    List<Account> findAll();

    Transaction post(Posting posting);

    Transaction reverse(TransactionId id, String description);

    long balanceOf(AccountId id);

    AccountStatement statement(AccountId id);

    Account changeOverdraft(AccountId id, long newLimitMinor, long expectedVersion);

    long versionOf(AccountId id);

    ReconciliationReport reconcile();

    List<String> descriptions();
}
