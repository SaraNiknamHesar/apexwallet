package com.apexwallet.app.metrics;

import java.util.Arrays;

public final class LatencyReport {

    private LatencyReport() {
    }

    public static long percentile(long[] samples, double fraction) {
        long[] copy = Arrays.copyOf(samples, samples.length);
        Arrays.sort(copy);
        int index = (int) Math.ceil(fraction * copy.length) - 1;
        return copy[Math.max(0, Math.min(copy.length - 1, index))];
    }
}
