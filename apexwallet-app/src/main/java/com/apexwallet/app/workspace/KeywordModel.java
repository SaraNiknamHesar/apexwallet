package com.apexwallet.app.workspace;

import com.apexwallet.ai.ModelPort;

/** Offline rules. A payment never calls this, and nothing here contacts a hosted model. */
public final class KeywordModel implements ModelPort {

    @Override
    public String complete(String maskedPrompt) {
        String text = maskedPrompt == null ? "" : maskedPrompt.toLowerCase();
        if (text.contains("grocery") || text.contains("market")) {
            return "GROCERIES";
        }
        if (text.contains("bill") || text.contains("electric") || text.contains("utility")) {
            return "UTILITIES";
        }
        if (text.contains("subscription") || text.contains("netflix")) {
            return "SUBSCRIPTIONS";
        }
        if (text.contains("salary") || text.contains("payroll")) {
            return "SALARY";
        }
        if (text.contains("rent") || text.contains("transfer")) {
            return "TRANSFER";
        }
        return "OTHER";
    }
}
