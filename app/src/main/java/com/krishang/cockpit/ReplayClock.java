package com.krishang.cockpit;

/** A bounded replay clock driven by elapsed-realtime milliseconds from the caller. */
public final class ReplayClock {
    private final long durationMs;
    private long anchorPositionMs;
    private long anchorTimeMs;
    private boolean playing;

    public ReplayClock(long durationMs) {
        if (durationMs < 0) {
            throw new IllegalArgumentException("Duration must be nonnegative");
        }
        this.durationMs = durationMs;
    }

    public void play(long nowMs) {
        if (isPlaying(nowMs) || durationMs == 0) {
            return;
        }
        if (anchorPositionMs == durationMs) {
            anchorPositionMs = 0;
        }
        anchorTimeMs = nowMs;
        playing = true;
    }

    public void pause(long nowMs) {
        anchorPositionMs = position(nowMs);
        playing = false;
    }

    public void seek(long positionMs, long nowMs) {
        position(nowMs);
        anchorPositionMs = Math.max(0, Math.min(durationMs, positionMs));
        anchorTimeMs = nowMs;
        if (anchorPositionMs == durationMs) {
            playing = false;
        }
    }

    public long position(long nowMs) {
        if (nowMs < 0) {
            throw new IllegalArgumentException("Elapsed realtime must be nonnegative");
        }
        if (!playing) {
            return anchorPositionMs;
        }
        long elapsedMs = Math.max(0, nowMs - anchorTimeMs);
        if (elapsedMs >= durationMs - anchorPositionMs) {
            playing = false;
            anchorPositionMs = durationMs;
            return durationMs;
        }
        return anchorPositionMs + elapsedMs;
    }

    public boolean isPlaying(long nowMs) {
        position(nowMs);
        return playing;
    }
}
