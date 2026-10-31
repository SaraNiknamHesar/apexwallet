package com.apexwallet.app.transfer;

import com.apexwallet.app.api.TransferCommand;
import com.apexwallet.app.api.TransferView;
import com.apexwallet.app.idempotency.IdempotencyResult;
import com.apexwallet.app.idempotency.IdempotencyService;
import com.apexwallet.app.identity.ApexPrincipal;
import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class TransferFacade {

    private final TransferService transfers;
    private final IdempotencyService idempotency;

    public TransferFacade(TransferService transfers, IdempotencyService idempotency) {
        this.transfers = transfers;
        this.idempotency = idempotency;
    }

    public IdempotencyResult submit(ApexPrincipal actor, String key, TransferCommand command) {
        if (key == null || key.isBlank()) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "X-Idempotency-Key is required");
        }
        return idempotency.execute(actor.username(), key.trim(), command.hash(), () -> {
            TransferView view = transfers.transfer(actor, command);
            int status = "PENDING_APPROVAL".equals(view.status()) ? 202 : 201;
            return new IdempotencyResult(status, json(view), false);
        });
    }

    static String json(TransferView view) {
        return "{\"transactionId\":\"" + view.transactionId()
                + "\",\"status\":\"" + view.status()
                + "\",\"fromBalanceMinor\":" + view.fromBalanceMinor()
                + ",\"toBalanceMinor\":" + view.toBalanceMinor() + "}";
    }
}
