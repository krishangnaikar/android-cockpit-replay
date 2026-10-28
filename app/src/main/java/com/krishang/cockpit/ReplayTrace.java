package com.krishang.cockpit;

import java.util.Arrays;

/** Immutable battery trace indexed by virtual milliseconds from the start of playback. */
public final class ReplayTrace {
    private final long[] timesMs;
    private final int[] percentages;
    private final long durationMs;
    private final long freshnessLimitMs;

    public static final class Sample {
        public final int batteryPercent;
        public final long sampleTimeMs;
        public final boolean stale;

        private Sample(int batteryPercent, long sampleTimeMs, boolean stale) {
            this.batteryPercent = batteryPercent;
            this.sampleTimeMs = sampleTimeMs;
            this.stale = stale;
        }
    }

    /** The first sample anchors time zero, so every position has a defined reading. */
    public ReplayTrace(long[] timesMs, int[] percentages, long durationMs, long freshnessLimitMs) {
        if (timesMs == null || percentages == null || timesMs.length == 0
                || timesMs.length != percentages.length) {
            throw new IllegalArgumentException("Supply one percentage per sample timestamp.");
        }
        this.timesMs = timesMs.clone();
        this.percentages = percentages.clone();
        if (this.timesMs[0] != 0 || durationMs < 0 || freshnessLimitMs < 0) {
            throw new IllegalArgumentException("Start at zero and use nonnegative time limits.");
        }
        for (int i = 0; i < this.timesMs.length; i++) {
            if ((i > 0 && this.timesMs[i] <= this.timesMs[i - 1]) || this.timesMs[i] > durationMs) {
                throw new IllegalArgumentException("Timestamps must increase within the trace duration.");
            }
            if (this.percentages[i] < 0 || this.percentages[i] > 100) {
                throw new IllegalArgumentException("Battery percentages must be between 0 and 100.");
            }
        }
        this.durationMs = durationMs;
        this.freshnessLimitMs = freshnessLimitMs;
    }

    public static ReplayTrace demo() {
        return new ReplayTrace(new long[]{0, 10_000}, new int[]{80, 77}, 15_000, 5_000);
    }

    public long durationMs() {
        return durationMs;
    }

    public Sample at(long positionMs) {
        long position = Math.max(0, Math.min(positionMs, durationMs));
        int index = Arrays.binarySearch(timesMs, position);
        if (index < 0) {
            // A miss returns -(insertion point) - 1. Use the sample immediately before it.
            index = -(index + 1) - 1;
        }
        return new Sample(percentages[index], timesMs[index], position - timesMs[index] > freshnessLimitMs);
    }
}
