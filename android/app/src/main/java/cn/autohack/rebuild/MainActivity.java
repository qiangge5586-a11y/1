package cn.autohack.rebuild;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Safe, offline-first shell for the independent HondaHack rebuild. */
public final class MainActivity extends Activity {
    private LinearLayout content;

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
        addFeature("Steering key mapping", "Demo only — no vehicle key interception");
        addMeterPreview();
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
        try {
            MeterPreset preset = MeterPresetRepository.loadDemo(this);
            TextView preview = new TextView(this);
            preview.setText(preset.name + "\n" +
                    preset.primaryView.renderMock(0, 850, 68, 82) +
                    "\nRPM: 850 · Fuel: 68% · Coolant: 82 °C");
            preview.setTextSize(20);
            preview.setGravity(Gravity.CENTER);
            preview.setPadding(dp(14), dp(20), dp(14), dp(20));
            preview.setTextColor(0xFFFFFFFF);
            preview.setBackgroundColor(0xFF263746);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
            lp.bottomMargin = dp(8);
            content.addView(preview, lp);
        } catch (Exception error) {
            addFeature("Instrument meter", "Demo preset unavailable: " + error.getMessage());
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
