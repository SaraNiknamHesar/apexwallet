package com.apexwallet.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AgentQueryLog {

    private final int limit;
    private int used;
    private final List<String> lines = new ArrayList<>();

    public AgentQueryLog(int limit) {
        this.limit = limit;
    }

    public boolean query(String principal, String text, Instant at) {
        if (used >= limit) {
            return false;
        }
        used++;
        lines.add(at + " " + principal + " " + Masker.mask(text));
        return true;
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }
}
