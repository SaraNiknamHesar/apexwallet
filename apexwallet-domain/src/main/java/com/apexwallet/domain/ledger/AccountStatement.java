package com.apexwallet.domain.ledger;

import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.money.Currency;

import java.util.List;

public record AccountStatement(
        AccountId accountId,
        Currency currency,
        List<StatementLine> lines,
        long closingBalanceMinor
) {
    public AccountStatement {
        lines = List.copyOf(lines);
    }
}
