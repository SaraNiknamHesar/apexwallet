package com.apexwallet.app.api;

import com.apexwallet.app.identity.CurrentUser;
import com.apexwallet.app.loan.LoanService;
import com.apexwallet.app.loan.LoanView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

    private final LoanService loans;

    public LoanController(LoanService loans) {
        this.loans = loans;
    }

    @GetMapping
    public List<LoanView> list() {
        return loans.list(CurrentUser.require());
    }

    @PostMapping
    public ResponseEntity<LoanView> request(@Valid @RequestBody LoanAmount request) {
        LoanView view = loans.request(CurrentUser.require(), request.amountMinor());
        HttpStatus status = "PENDING".equals(view.status()) ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(view);
    }

    @PostMapping("/{id}/approve")
    public LoanView approve(@PathVariable UUID id) {
        return loans.approve(CurrentUser.require(), id);
    }

    @PostMapping("/{id}/reject")
    public LoanView reject(@PathVariable UUID id) {
        return loans.reject(CurrentUser.require(), id);
    }

    @PostMapping("/{id}/repay")
    public LoanView repay(@PathVariable UUID id, @Valid @RequestBody LoanAmount request) {
        return loans.repay(CurrentUser.require(), id, request.amountMinor());
    }

    public record LoanAmount(@NotNull @Positive Long amountMinor) {
    }
}
