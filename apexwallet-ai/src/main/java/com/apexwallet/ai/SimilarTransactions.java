package com.apexwallet.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SimilarTransactions {

    private SimilarTransactions() {
    }

    public static List<String> rank(String query, List<String> descriptions) {
        String needle = query.toLowerCase(Locale.ROOT);
        return descriptions.stream()
                .sorted(Comparator.comparingInt((String item) -> overlap(needle, item.toLowerCase(Locale.ROOT))).reversed())
                .filter(item -> overlap(needle, item.toLowerCase(Locale.ROOT)) > 0)
                .toList();
    }

    private static int overlap(String query, String item) {
        int score = 0;
        for (String token : query.split(" ")) {
            if (!token.isBlank() && item.contains(token)) {
                score++;
            }
        }
        return score;
    }

    public static List<String> empty() {
        return new ArrayList<>();
    }
}
