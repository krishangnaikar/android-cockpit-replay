package com.krishang.cockpit.gradle;

import com.krishang.cockpit.ReplayClockTest;
import com.krishang.cockpit.ReplayTraceTest;
import com.krishang.cockpit.TraceCsvTest;
import java.io.IOException;
import org.junit.Test;

public class CockpitRegressionTest {
    @Test
    public void playbackClock() {
        ReplayClockTest.main(new String[0]);
    }

    @Test
    public void batteryTrace() {
        ReplayTraceTest.main(new String[0]);
    }

    @Test
    public void traceCsv() throws IOException {
        TraceCsvTest.main(new String[0]);
    }
}
