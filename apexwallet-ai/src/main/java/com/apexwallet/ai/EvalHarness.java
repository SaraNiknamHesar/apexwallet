package com.apexwallet.ai;

import java.util.List;

public final class EvalHarness {

    private EvalHarness() {
    }

    public static double accuracy(ModelPort model, List<Sample> golden) {
        int correct = 0;
        for (Sample sample : golden) {
            if (sample.expected().equals(Categoriser.categorise(model, sample.description()))) {
                correct++;
            }
        }
        return golden.isEmpty() ? 1 : (double) correct / golden.size();
    }

    public record Sample(String description, String expected) {
    }
}
