package cn.autohack.rebuild;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Safe, offline-first shell for the independent HondaHack rebuild. */
public final class MainActivity extends Activity {
    private LinearLayout content;
    private TextView meterDisplay;
    private TextView speedLabel;
    private TextView sizeLabel;
    private int meterColor = 0xFF263746;
    private int meterTextSize = 32;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(content);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("HondaHack Rebuild");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Independent safe baseline · offline demo mode");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        content.addView(subtitle);

        addSection("Device capability (read-only)");
        TextView facts = new TextView(this);
        facts.setText(new CapabilitySnapshot().toDisplayString());
        facts.setTextSize(14);
        facts.setPadding(dp(14), dp(12), dp(14), dp(12));
        facts.setBackgroundColor(0xFFEFF2F5);
        content.addView(facts);

        addSection("Feature preview");
        addMeterPreview();
        addKeyMappingDemo();
        addFeature("Navigation state", "Waiting for a supported navigation integration");
        addFeature("Audio focus", "No audio changes until explicitly enabled");
        addFeature("Storage diagnostics", "Read-only checks; no system file modifications");

        Button refresh = new Button(this);
        refresh.setText("Refresh device information");
        refresh.setOnClickListener(v -> recreate());
        content.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView safety = new TextView(this);
        safety.setText("Safety baseline: no root commands, Xposed hooks, signature spoofing, remote revocation, silent installs, or system-partition writes.");
        safety.setTextSize(12);
        safety.setPadding(0, dp(18), 0, 0);
        content.addView(safety);
    }

    private void addSection(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(18);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(20), 0, dp(8));
        content.addView(label);
    }

    private void addMeterPreview() {
        addSection("Offline meter editor");
        try {
            MeterPreset preset = MeterPresetRepository.loadDemo(this);
            android.content.SharedPreferences prefs =
                    getSharedPreferences("meter_editor", MODE_PRIVATE);
            meterColor = prefs.getInt("background", 0xFF263746);
            meterTextSize = prefs.getInt("text_size", preset.primaryView.textSize);
            int initialSpeed = prefs.getInt("speed", 0);

            meterDisplay = new TextView(this);
            meterDisplay.setGravity(Gravity.CENTER);
            meterDisplay.setPadding(dp(14), dp(18), dp(14), dp(18));
            meterDisplay.setTextColor(0xFFFFFFFF);
            meterDisplay.setBackgroundColor(meterColor);
            LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
            displayParams.bottomMargin = dp(12);
            content.addView(meterDisplay, displayParams);

            speedLabel = new TextView(this);
            content.addView(speedLabel);
            SeekBar speed = new SeekBar(this);
            speed.setMax(240);
            speed.setProgress(initialSpeed);
            content.addView(speed, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            sizeLabel = new TextView(this);
            content.addView(sizeLabel);
            SeekBar size = new SeekBar(this);
            size.setMax(38);
            size.setProgress(Math.max(0, Math.min(38, meterTextSize - 18)));
            content.addView(size, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            Button theme = new Button(this);
            theme.setText("Change meter theme");
            theme.setOnClickListener(v -> {
                if (meterColor == 0xFF263746) meterColor = 0xFF101820;
                else if (meterColor == 0xFF101820) meterColor = 0xFF173D32;
                else meterColor = 0xFF263746;
                meterDisplay.setBackgroundColor(meterColor);
                prefs.edit().putInt("background", meterColor).apply();
            });
            content.addView(theme, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            Runnable update = () -> {
                int value = speed.getProgress();
                int rpm = 850 + value * 32;
                meterDisplay.setText(preset.name + "\n" + value + " km/h"
                        + "\nRPM: " + rpm + " · Fuel: 68% · Coolant: 82 °C");
                meterDisplay.setTextSize(meterTextSize);
                speedLabel.setText("Demo speed: " + value + " km/h");
                sizeLabel.setText("Meter text size: " + meterTextSize + " sp");
            };
            speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                    update.run();
                    if (user) prefs.edit().putInt("speed", value).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                    meterTextSize = value + 18;
                    update.run();
                    if (user) prefs.edit().putInt("text_size", meterTextSize).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            update.run();
        } catch (Exception error) {
            addFeature("Instrument meter", "Demo preset unavailable: " + error.getMessage());
        }
    }

    private void addKeyMappingDemo() {
        addSection("Steering key mapping · simulator");
        TextView status = new TextView(this);
        status.setText("No physical buttons are intercepted. Tap a sample action below.");
        status.setTextSize(14);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackgroundColor(0xFFF5F6F7);
        content.addView(status);
        String[] labels = {"Volume +", "Next track", "Show meter", "Voice assistant"};
        String[] actions = {"Increase demo volume", "Skip to next demo track",
                "Toggle demo meter visibility", "Open voice assistant demo"};
        for (int i = 0; i < labels.length; i++) {
            final String action = actions[i];
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setOnClickListener(v -> {
                status.setText("Simulated mapping action: " + action
                        + "\nThis does not send commands to the car.");
                getSharedPreferences("key_mapping_demo", MODE_PRIVATE)
                        .edit().putString("last_action", action).apply();
            });
            content.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void addFeature(String name, String detail) {
        TextView row = new TextView(this);
        row.setText(name + "\n" + detail);
        row.setTextSize(14);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundColor(0xFFF5F6F7);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        content.addView(row, lp);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
