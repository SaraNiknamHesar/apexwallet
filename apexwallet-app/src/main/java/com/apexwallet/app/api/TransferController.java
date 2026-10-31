package com.apexwallet.app.api;

import com.apexwallet.app.idempotency.IdempotencyResult;
import com.apexwallet.app.identity.CurrentUser;
import com.apexwallet.app.transfer.TransferFacade;
import com.apexwallet.app.transfer.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "transfers")
@SecurityRequirement(name = "bearer")
public class TransferController {

    private final TransferFacade facade;
    private final TransferService transfers;

    public TransferController(TransferFacade facade, TransferService transfers) {
        this.facade = facade;
        this.transfers = transfers;
    }

    @PostMapping
    @Operation(summary = "Move money. Requires X-Idempotency-Key. Above the threshold the transfer waits for a different supervisor.")
    @ApiResponse(responseCode = "201", description = "POSTED")
    @ApiResponse(responseCode = "202", description = "PENDING_APPROVAL")
    @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_CONFLICT or IDEMPOTENCY_IN_PROGRESS")
    @ApiResponse(responseCode = "422", description = "INSUFFICIENT_FUNDS or CURRENCY_MISMATCH")
    public ResponseEntity<String> transfer(@RequestHeader(value = "X-Idempotency-Key", required = false) String key,
                                            @Valid @RequestBody TransferRequest request) {
        IdempotencyResult result = facade.submit(CurrentUser.require(), key, request.toCommand());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.status()).contentType(MediaType.APPLICATION_JSON);
        if (result.replay()) {
            builder.header("Idempotency-Replayed", "true");
        }
        return builder.body(result.body());
    }

    @GetMapping("/pending")
    @Operation(summary = "Transfers waiting for a supervisor.")
    public List<ApprovalView> pending() {
        return transfers.queue(CurrentUser.require());
    }

    @PostMapping("/resolve")
    @Operation(summary = "Confirmation of Payee: which account owns this IBAN?")
    public TransferService.PayeeView resolve(@Valid @RequestBody ResolveRequest request) {
        return transfers.resolve(CurrentUser.require(), request.iban());
    }

    @PostMapping("/{id}/approve")
    public TransferView approve(@PathVariable UUID id) {
        return transfers.approve(CurrentUser.require(), id);
    }

    @PostMapping("/{id}/reject")
    public TransferView reject(@PathVariable UUID id) {
        return transfers.reject(CurrentUser.require(), id);
    }

    public record ResolveRequest(@jakarta.validation.constraints.NotBlank String iban) {
    }
}
