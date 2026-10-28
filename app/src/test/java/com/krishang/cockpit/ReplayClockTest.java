package com.krishang.cockpit;

/** Standalone JVM checks; no device or wall-clock sleeps required. */
public final class ReplayClockTest {
    public static void main(String[] args) {
        ReplayClock clock = new ReplayClock(15_000);
        check(!clock.isPlaying(100), "starts paused");
        clock.play(100);
        clock.play(200);
        check(clock.position(7_100) == 7_000, "repeated play preserves elapsed time");
        clock.pause(7_101);
        check(clock.position(20_000) == 7_001, "pause freezes the playhead");
        clock.seek(5_000, 20_000);
        check(!clock.isPlaying(20_000), "paused seek stays paused");
        clock.play(21_000);
        clock.seek(2_000, 22_000);
        check(clock.position(23_000) == 3_000, "live seek reanchors the moving playhead");
        check(clock.isPlaying(34_999), "playing until the final millisecond");
        check(!clock.isPlaying(35_000), "stops at the exact end");
        check(clock.position(36_000) == 15_000, "holds the last frame");
        clock.play(37_000);
        check(clock.position(37_250) == 250, "play at end starts a new replay");
        clock.seek(Long.MAX_VALUE, 38_000);
        check(!clock.isPlaying(38_000), "overshooting seek stops at the end");
        clock.seek(-10, 39_000);
        check(clock.position(39_000) == 0, "negative seek clamps to zero");

        ReplayClock empty = new ReplayClock(0);
        empty.play(0);
        check(!empty.isPlaying(100) && empty.position(100) == 0, "zero-length trace");
        ReplayClock large = new ReplayClock(Long.MAX_VALUE);
        large.seek(Long.MAX_VALUE - 5, 0);
        large.play(0);
        check(large.position(10) == Long.MAX_VALUE, "elapsed time cannot overflow the end");
        System.out.println("ReplayClockTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
