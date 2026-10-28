package com.krishang.cockpit;

import java.io.IOException;
import java.io.Reader;

/** Reads ASCII battery CSV. The caller owns the reader. */
public final class TraceCsv {
    public static final int MAX_BYTES = 262_144;
    public static final int MAX_SAMPLES = 10_000;
    private static final long FRESHNESS_MS = 5_000;
    private static final long TAIL_MS = FRESHNESS_MS + 1;

    private TraceCsv() {}

    /**
     * The header is time_ms,battery_percent. The first timestamp is zero, then timestamps
     * increase strictly. After the last sample the timeline extends 5,001 ms, so seeking
     * to the end shows when that final sample first becomes stale.
     */
    public static ReplayTrace read(Reader source) throws IOException {
        StringBuilder csv = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = source.read(buffer)) != -1) {
            if (csv.length() + count > MAX_BYTES) {
                throw new IOException("Trace exceeds the 256 KiB size limit.");
            }
            for (int i = 0; i < count; i++) {
                if (buffer[i] > 127) {
                    throw new IOException("Use an ASCII CSV without a byte-order mark.");
                }
            }
            csv.append(buffer, 0, count);
        }
        // Bound memory before splitting or allocating per-row strings, including hostile blank lines.
        String[] lines = csv.toString().split("\\r\\n|\\n|\\r", MAX_SAMPLES + 3);
        if (!lines[0].trim().equals("time_ms,battery_percent")) {
            throw new IOException("Line 1: expected header time_ms,battery_percent.");
        }
        int end = lines.length;
        if (end > 1 && lines[end - 1].isEmpty()) end--;
        int samples = end - 1;
        if (samples < 1) throw new IOException("Line 2: provide at least one battery sample.");
        if (samples > MAX_SAMPLES) throw new IOException("Trace exceeds 10,000 samples.");
        long[] times = new long[samples];
        int[] levels = new int[samples];
        for (int i = 0; i < samples; i++) {
            String[] fields = lines[i + 1].split(",", 3);
            String prefix = "Line " + (i + 2) + ": ";
            if (fields.length != 2) throw new IOException(prefix + "expected two comma-separated values.");
            long time = number(fields[0], prefix);
            long level = number(fields[1], prefix);
            if ((i == 0 && time != 0) || (i > 0 && time <= times[i - 1])) {
                throw new IOException(prefix + "timestamps must start at zero and increase strictly.");
            }
            if (time > Integer.MAX_VALUE - TAIL_MS) {
                throw new IOException(prefix + "timestamp exceeds the supported timeline length.");
            }
            if (level > 100) throw new IOException(prefix + "battery percentage must be 0 through 100.");
            times[i] = time;
            levels[i] = (int) level;
        }
        return new ReplayTrace(times, levels, times[samples - 1] + TAIL_MS, FRESHNESS_MS);
    }

    private static long number(String field, String prefix) throws IOException {
        String value = field.trim();
        if (value.isEmpty()) throw new IOException(prefix + "expected an unsigned decimal integer.");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') throw new IOException(prefix + "expected an unsigned decimal integer.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IOException(prefix + "number is too large.", error);
        }
    }
}
