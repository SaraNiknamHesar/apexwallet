package com.apexwallet.app.api;

import com.apexwallet.app.account.AccountService;
import com.apexwallet.app.account.StatementService;
import com.apexwallet.app.identity.CurrentUser;
import com.apexwallet.domain.account.AccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "accounts")
@SecurityRequirement(name = "bearer")
public class AccountController {

    private final AccountService accounts;
    private final StatementService statements;

    public AccountController(AccountService accounts, StatementService statements) {
        this.accounts = accounts;
        this.statements = statements;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open an account. A customer always becomes the owner, even if ownerRef says otherwise.")
    public AccountView open(@Valid @RequestBody OpenAccountRequest request) {
        return accounts.open(CurrentUser.require(), request.ownerRef(), request.currency(), request.type(),
                request.externalAccountNumber());
    }

    @GetMapping
    public List<AccountView> list() {
        return accounts.list(CurrentUser.require());
    }

    @GetMapping("/{id}")
    public AccountView get(@PathVariable UUID id) {
        return accounts.get(CurrentUser.require(), new AccountId(id));
    }

    @GetMapping("/{id}/statement")
    public StatementService.PageResult statement(@PathVariable UUID id,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return statements.statement(CurrentUser.require(), new AccountId(id), page, size);
    }

    @PutMapping("/{id}/overdraft")
    public AccountView overdraft(@PathVariable UUID id, @Valid @RequestBody OverdraftRequest request) {
        return accounts.changeOverdraft(CurrentUser.require(), new AccountId(id), request.limitMinor(), request.expectedVersion());
    }
}
