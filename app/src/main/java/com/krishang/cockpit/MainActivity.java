package com.krishang.cockpit;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.IOException;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = 0xff10171b;
    private static final int PANEL = 0xff1b282e;
    private static final int WHITE = 0xffeef5f4;
    private static final int MUTED = 0xffb1c2c5;
    private static final int MINT = 0xff79e2c2;
    private static final int AMBER = 0xffffc065;
    private ReplayTrace trace = ReplayTrace.demo();
    private ReplayClock clock = new ReplayClock(trace.durationMs());
    private String traceKey = "demo";
    private int selectionVersion;
    private boolean visible;
    private Bundle restoring;
    private TextView sourceName, checkpointTitle;
    private LinearLayout transport, checkpoints;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::render;
    private SeekBar timeline;
    private TextView battery, freshness, sampleInfo, playhead;
    private Button play;
    private boolean resumeAfterScrub;
    private boolean resumeWhenVisible;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFitsSystemWindows(true);
        LinearLayout page = column();
        page.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(page);
        page.addView(label("Cockpit replay", 30, WHITE, true));
        sourceName = label("Demo battery trace", 16, MUTED, false);
        sourceName.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(sourceName);
        LinearLayout sources = row();
        addButton(sources, action("Open CSV", false, v -> pickTrace()), false);
        addButton(sources, action("Use demo", false, v -> loadSelection(null, true)), true);
        addSpaced(page, sources, 8);

        LinearLayout card = column();
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(surface(PANEL));
        addSpaced(page, card, 20);
        card.addView(label("BATTERY CHARGE", 13, MUTED, true));
        battery = label("", 54, WHITE, true);
        card.addView(battery);
        freshness = label("", 17, MINT, true);
        freshness.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(freshness);
        sampleInfo = label("", 15, MUTED, false);
        addSpaced(card, sampleInfo, 8);

        addSpaced(page, label("Replay timeline", 20, WHITE, true), 24);
        playhead = label("", 17, MINT, true);
        playhead.setTypeface(Typeface.MONOSPACE);
        addSpaced(page, playhead, 4);
        timeline = new SeekBar(this);
        timeline.setMax((int) trace.durationMs());
        timeline.setContentDescription("Replay position");
        timeline.setProgressTintList(ColorStateList.valueOf(MINT));
        timeline.setThumbTintList(ColorStateList.valueOf(MINT));
        timeline.setMinimumHeight(dp(48));
        page.addView(timeline);
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) seek(progress);
            }
            public void onStartTrackingTouch(SeekBar bar) {
                long now = SystemClock.elapsedRealtime();
                resumeAfterScrub = clock.isPlaying(now);
                clock.pause(now);
                render();
            }
            public void onStopTrackingTouch(SeekBar bar) {
                if (resumeAfterScrub && bar.getProgress() < bar.getMax()) {
                    clock.play(SystemClock.elapsedRealtime());
                }
                resumeAfterScrub = false;
                render();
            }
        });
        transport = row();
        play = action("Play", true, v -> {
            long now = SystemClock.elapsedRealtime();
            if (clock.isPlaying(now)) clock.pause(now);
            else clock.play(now);
            render();
        });
        addButton(transport, play, false);
        addButton(transport, action("Rewind", false, v -> inspect(0)), true);
        page.addView(transport);

        checkpointTitle = label("Inspect checkpoints", 20, WHITE, true);
        addSpaced(page, checkpointTitle, 24);
        checkpoints = row();
        addButton(checkpoints, action("7 s · Stale", false, v -> inspect(7000)), false);
        addButton(checkpoints, action("10 s · Recovery", false, v -> inspect(10000)), true);
        addSpaced(page, checkpoints, 8);
        setContentView(scroll);
        restoring = state;
        loadSelection(null, false);
        render();
    }

    private void pickTrace() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        try {
            startActivityForResult(pick, 1);
        } catch (ActivityNotFoundException missing) {
            sourceName.setText("No document picker is available on this device.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 1 && result == RESULT_OK && data != null && data.getData() != null) {
            loadSelection(data.getData(), false);
        }
    }

    private void loadSelection(Uri uri, boolean reset) {
        final int version = ++selectionVersion;
        Context app = getApplicationContext();
        enablePlayback(false);
        sourceName.setText("Loading trace…");
        if (uri != null || reset) restoring = null;
        TraceDocuments.IO.execute(() -> {
            TraceDocuments loaded;
            String message = null;
            try {
                if (reset) loaded = TraceDocuments.reset(app);
                else if (uri != null) loaded = TraceDocuments.importUri(app, uri);
                else loaded = TraceDocuments.restore(app);
            } catch (IOException | SecurityException | IllegalArgumentException error) {
                message = "Trace error: " + error.getMessage();
                try {
                    loaded = TraceDocuments.restore(app);
                } catch (IOException | SecurityException unreadable) {
                    loaded = TraceDocuments.demo();
                }
            }
            final TraceDocuments chosen = loaded;
            final String errorMessage = message;
            handler.post(() -> {
                if (version != selectionVersion || isDestroyed() || isFinishing()) return;
                boolean preserve = errorMessage != null && traceKey.equals(chosen.key);
                trace = chosen.trace;
                traceKey = chosen.key;
                if (!preserve) {
                    clock = new ReplayClock(trace.durationMs());
                    resumeAfterScrub = false;
                    resumeWhenVisible = false;
                }
                if (restoring != null && traceKey.equals(restoring.getString("traceKey"))) {
                    long now = SystemClock.elapsedRealtime();
                    clock.seek(restoring.getLong("position"), now);
                    resumeWhenVisible = restoring.getBoolean("playing") && clock.position(now) < trace.durationMs();
                    if (visible && resumeWhenVisible) {
                        clock.play(now);
                        resumeWhenVisible = false;
                    }
                }
                restoring = null;
                boolean demo = "demo".equals(traceKey);
                timeline.setMax((int) trace.durationMs());
                checkpointTitle.setVisibility(demo ? View.VISIBLE : View.GONE);
                checkpoints.setVisibility(demo ? View.VISIBLE : View.GONE);
                String current = demo ? "Demo battery trace" : "Imported battery trace";
                sourceName.setText(errorMessage == null ? current : current + "\n" + errorMessage);
                enablePlayback(true);
                render();
            });
        });
    }

    private void enablePlayback(boolean enabled) {
        timeline.setEnabled(enabled);
        for (int i = 0; i < transport.getChildCount(); i++) {
            transport.getChildAt(i).setEnabled(enabled);
        }
        for (int i = 0; i < checkpoints.getChildCount(); i++) {
            checkpoints.getChildAt(i).setEnabled(enabled);
        }
    }

    private void seek(long position) {
        clock.seek(position, SystemClock.elapsedRealtime());
        render();
    }

    private void inspect(long position) {
        clock.pause(SystemClock.elapsedRealtime());
        seek(position);
    }

    private void render() {
        handler.removeCallbacks(tick);
        long now = SystemClock.elapsedRealtime();
        long position = clock.position(now);
        ReplayTrace.Sample sample = trace.at(position);
        battery.setText(String.format(Locale.US, "%d%%", sample.batteryPercent));
        String status = sample.stale ? "Stale signal" : "Fresh signal";
        if (!status.contentEquals(freshness.getText())) {
            freshness.setText(status);
            freshness.setTextColor(sample.stale ? AMBER : MINT);
        }
        sampleInfo.setText("Last sample " + seconds(sample.sampleTimeMs) + "   |   Age "
                + seconds(position - sample.sampleTimeMs) + "\nFreshness limit 5 s");
        playhead.setText(seconds(position) + "  /  " + seconds(trace.durationMs()));
        timeline.setProgress((int) position);
        boolean running = clock.isPlaying(now);
        play.setText(running ? "Pause" : position == trace.durationMs() ? "Replay" : "Play");
        if (running) handler.postDelayed(tick, 100);
    }

    private static String seconds(long millis) {
        return String.format(Locale.US, "%.3f s", millis / 1000.0);
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        long now = SystemClock.elapsedRealtime();
        if (resumeWhenVisible && clock.position(now) < trace.durationMs()) clock.play(now);
        resumeWhenVisible = false;
        render();
    }

    @Override protected void onPause() {
        visible = false;
        long now = SystemClock.elapsedRealtime();
        resumeWhenVisible = clock.isPlaying(now)
                || (resumeAfterScrub && clock.position(now) < trace.durationMs());
        resumeAfterScrub = false;
        clock.pause(now);
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (restoring != null) {
            state.putString("traceKey", restoring.getString("traceKey"));
            state.putLong("position", restoring.getLong("position"));
            state.putBoolean("playing", restoring.getBoolean("playing"));
            return;
        }
        state.putString("traceKey", traceKey);
        long now = SystemClock.elapsedRealtime();
        state.putLong("position", clock.position(now));
        state.putBoolean("playing", clock.position(now) < trace.durationMs()
                && (clock.isPlaying(now) || resumeWhenVisible || resumeAfterScrub));
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button action(String text, boolean primary, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(primary ? BACKGROUND : WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(primary ? MINT : PANEL));
        button.setMinHeight(dp(48));
        button.setOnClickListener(click);
        return button;
    }

    private void addButton(LinearLayout row, Button button, boolean gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = gap ? dp(8) : 0;
        row.addView(button, params);
    }

    private void addSpaced(LinearLayout parent, View child, int spacing) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(spacing);
        parent.addView(child, params);
    }

    private GradientDrawable surface(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(14));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
