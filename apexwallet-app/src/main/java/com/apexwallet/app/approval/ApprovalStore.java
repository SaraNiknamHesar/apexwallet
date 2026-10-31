package com.apexwallet.app.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalStore {

    void save(PendingTransfer transfer);

    Optional<PendingTransfer> find(UUID id);

    List<PendingTransfer> pending();
}
