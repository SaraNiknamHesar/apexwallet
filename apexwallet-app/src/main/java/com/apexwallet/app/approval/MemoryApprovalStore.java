package com.apexwallet.app.approval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class MemoryApprovalStore implements ApprovalStore {

    private final ConcurrentHashMap<UUID, PendingTransfer> rows = new ConcurrentHashMap<>();

    @Override
    public void save(PendingTransfer transfer) {
        rows.put(transfer.id(), transfer);
    }

    @Override
    public Optional<PendingTransfer> find(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public List<PendingTransfer> pending() {
        List<PendingTransfer> waiting = new ArrayList<>();
        for (PendingTransfer row : rows.values()) {
            if (row.status() == ApprovalStatus.PENDING) {
                waiting.add(row);
            }
        }
        return List.copyOf(waiting);
    }
}
