package com.apexwallet.app.account;

import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.domain.account.AccountId;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import com.apexwallet.domain.ledger.AccountStatement;
import com.apexwallet.domain.ledger.StatementLine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatementService {

    private final AccountService accounts;
    private final com.apexwallet.app.port.Bank bank;

    public StatementService(AccountService accounts, com.apexwallet.app.port.Bank bank) {
        this.accounts = accounts;
        this.bank = bank;
    }

    public PageResult statement(ApexPrincipal actor, AccountId id, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "page must be >= 0 and size must be 1..100");
        }
        accounts.visible(actor, id);
        AccountStatement statement = bank.statement(id);
        int from = page * size;
        List<StatementLine> lines = from >= statement.lines().size()
                ? List.of()
                : statement.lines().subList(from, Math.min(statement.lines().size(), from + size));
        return new PageResult(lines, page, size, statement.lines().size(), from + size < statement.lines().size(),
                statement.closingBalanceMinor());
    }

    public record PageResult(List<StatementLine> items, int page, int size, int total, boolean hasNext, long closingBalanceMinor) {
    }
}
