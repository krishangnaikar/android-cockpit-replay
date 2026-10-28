package com.krishang.cockpit;

/** Standalone regression checks. Run main with a JDK, including when the Android SDK is unavailable. */
public final class ReplayTraceTest {
    public static void main(String[] args) {
        ReplayTrace demo = ReplayTrace.demo();
        check(demo.durationMs() == 15_000, "demo duration");
        sample(demo, Long.MIN_VALUE, 80, 0, false);
        sample(demo, 5_000, 80, 0, false);
        sample(demo, 5_001, 80, 0, true);
        sample(demo, 7_000, 80, 0, true);
        sample(demo, 9_999, 80, 0, true);
        sample(demo, 10_000, 77, 10_000, false);
        sample(demo, Long.MAX_VALUE, 77, 10_000, false);
        sample(demo, 7_000, 80, 0, true);
        sample(demo, 10_000, 77, 10_000, false);

        long[] times = {0, 1, 4};
        int[] levels = {0, 100, 60};
        ReplayTrace custom = new ReplayTrace(times, levels, 7, 0);
        times[1] = 5;
        levels[1] = 50;
        sample(custom, 1, 100, 1, false);
        sample(custom, 2, 100, 1, true);
        sample(custom, 4, 60, 4, false);
        sample(custom, 7, 60, 4, true);
        sample(new ReplayTrace(new long[]{0}, new int[]{0}, 0, 0), 7, 0, 0, false);

        invalid(null, levels, 7, 0);
        invalid(times, null, 7, 0);
        invalid(new long[0], new int[0], 7, 0);
        invalid(new long[]{0, 1}, new int[]{100}, 7, 0);
        invalid(new long[]{1}, new int[]{100}, 7, 0);
        invalid(new long[]{0, 1, 1}, levels, 7, 0);
        invalid(new long[]{0, 4, 1}, levels, 7, 0);
        invalid(new long[]{0, 8}, new int[]{90, 80}, 7, 0);
        invalid(new long[]{0}, new int[]{101}, 7, 0);
        invalid(new long[]{0}, new int[]{-1}, 7, 0);
        invalid(new long[]{0}, new int[]{100}, -1, 0);
        invalid(new long[]{0}, new int[]{100}, 7, -1);
        System.out.println("ReplayTraceTest passed");
    }

    private static void sample(ReplayTrace trace, long position, int percent, long time, boolean stale) {
        ReplayTrace.Sample result = trace.at(position);
        check(result.batteryPercent == percent && result.sampleTimeMs == time && result.stale == stale,
                "unexpected reading at " + position);
    }

    private static void invalid(long[] times, int[] levels, long duration, long limit) {
        try {
            new ReplayTrace(times, levels, duration, limit);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("accepted an invalid trace");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
