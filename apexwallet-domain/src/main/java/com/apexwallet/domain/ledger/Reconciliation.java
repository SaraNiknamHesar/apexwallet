package com.apexwallet.domain.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Compares a cached balance with the fold of the journal. A planted difference must show up. */
public final class Reconciliation {

    private Reconciliation() {
    }

    public static ReconciliationReport compare(Map<String, Long> cached, Map<String, Long> folded) {
        TreeSet<String> ids = new TreeSet<>();
        ids.addAll(cached.keySet());
        ids.addAll(folded.keySet());
        List<String> mismatches = new ArrayList<>();
        long checksum = 0;
        for (String id : ids) {
            long cache = cached.getOrDefault(id, 0L);
            long fold = folded.getOrDefault(id, 0L);
            checksum = Math.addExact(checksum, fold);
            if (cache != fold) {
                mismatches.add(id + " cache=" + cache + " fold=" + fold);
            }
        }
        return new ReconciliationReport(List.copyOf(mismatches), checksum);
    }
}
