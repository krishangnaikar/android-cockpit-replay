package com.krishang.cockpit;

/** Runs the same dashboard scenario with direct seeks and continuous playback. */
public final class ReplayAcceptanceTest {
    public static void main(String[] args) {
        ReplayTrace trace = ReplayTrace.demo();
        ReplayClock clock = new ReplayClock(trace.durationMs());
        clock.seek(7_000, 0);
        expectReading(trace, clock, 0, 7_000, 80, 0, true);
        clock.seek(10_000, 1);
        expectReading(trace, clock, 1, 10_000, 77, 10_000, false);

        clock.seek(0, 2);
        clock.play(100);
        expectReading(trace, clock, 5_100, 5_000, 80, 0, false);
        expectReading(trace, clock, 5_101, 5_001, 80, 0, true);
        clock.pause(7_100);
        expectReading(trace, clock, 20_000, 7_000, 80, 0, true);
        clock.play(20_000);
        expectReading(trace, clock, 23_000, 10_000, 77, 10_000, false);
        clock.pause(23_000);
        clock.seek(0, 23_000);
        expectReading(trace, clock, 24_000, 0, 80, 0, false);
        System.out.println("ReplayAcceptanceTest passed");
    }

    private static void expectReading(ReplayTrace trace, ReplayClock clock, long nowMs,
            long expectedPosition, int battery, long sampleTime, boolean stale) {
        long position = clock.position(nowMs);
        ReplayTrace.Sample reading = trace.at(position);
        if (position != expectedPosition || reading.batteryPercent != battery
                || reading.sampleTimeMs != sampleTime || reading.stale != stale) {
            throw new AssertionError("At " + nowMs + " ms got position=" + position
                    + ", battery=" + reading.batteryPercent + ", sample=" + reading.sampleTimeMs
                    + ", stale=" + reading.stale);
        }
    }
}
