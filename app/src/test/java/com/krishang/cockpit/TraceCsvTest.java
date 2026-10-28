package com.krishang.cockpit;

import java.io.IOException;
import java.io.StringReader;

/** JDK-only parser regression suite. */
public final class TraceCsvTest {
    private static final String HEADER = "time_ms,battery_percent\n";

    public static void main(String[] args) throws IOException {
        ReplayTrace imported = read("time_ms,battery_percent\r\n0,99\r\n10000, 70\r\n");
        check(imported.durationMs() == 15_001, "tail");
        check(imported.at(7_000).stale, "gap");
        check(imported.at(10_000).batteryPercent == 70 && !imported.at(10_000).stale, "recovery");
        check(!imported.at(15_000).stale && imported.at(15_001).stale, "end boundary");
        check(read(HEADER + "0,0").durationMs() == 5_001, "one sample");
        check(read(HEADER + "0,0\n2147478646,100").durationMs() == Integer.MAX_VALUE, "max duration");
        rejects("", "Line 1");
        rejects(HEADER, "Line 2");
        rejects(HEADER + "1,90", "Line 2");
        rejects(HEADER + "0,0\n0,1", "Line 3");
        rejects(HEADER + "0,0\n3,2\n2,1", "Line 4");
        rejects(HEADER + "0,1.5", "Line 2");
        rejects(HEADER + "0,-1", "Line 2");
        rejects(HEADER + "0,101", "Line 2");
        rejects(HEADER + "0,2,3", "Line 2");
        rejects(HEADER + "0,", "Line 2");
        rejects(HEADER + "0,0\n\n", "Line 3");
        rejects(HEADER + "0,0\n2147478647,70", "Line 3");
        rejects(HEADER + "0,99999999999999999999", "Line 2");
        rejects(HEADER + "0,\u00e9", "ASCII");
        StringBuilder large = new StringBuilder(HEADER);
        while (large.length() <= TraceCsv.MAX_BYTES) large.append('0');
        rejects(large.toString(), "256 KiB");
        StringBuilder many = new StringBuilder(HEADER);
        for (int i = 0; i < TraceCsv.MAX_SAMPLES; i++) many.append(i).append(",75\n");
        check(read(many.toString()).at(9_999).sampleTimeMs == 9_999, "max samples");
        rejects(many.append("10000,75\n").toString(), "10,000 samples");
        System.out.println("TraceCsvTest passed");
    }

    private static ReplayTrace read(String text) throws IOException {
        return TraceCsv.read(new StringReader(text));
    }

    private static void rejects(String text, String expectedMessage) {
        try {
            read(text);
        } catch (IOException error) {
            check(error.getMessage().contains(expectedMessage), error.getMessage());
            return;
        }
        throw new AssertionError("accepted invalid input");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
