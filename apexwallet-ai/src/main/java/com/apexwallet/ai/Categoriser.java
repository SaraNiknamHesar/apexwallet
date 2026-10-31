package com.apexwallet.ai;

import java.util.Set;

public final class Categoriser {

    public static final Set<String> ALLOWED = Set.of(
            "GROCERIES", "UTILITIES", "SUBSCRIPTIONS", "SALARY", "TRANSFER", "OTHER");

    private Categoriser() {
    }

    public static String categorise(ModelPort model, String description) {
        try {
            String raw = model.complete(Masker.mask(description));
            if (raw != null && ALLOWED.contains(raw.trim())) {
                return raw.trim();
            }
        } catch (RuntimeException ignored) {
            return "OTHER";
        }
        return "OTHER";
    }
}
