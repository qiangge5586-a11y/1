package cn.autohack.rebuild;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

/** Safe, offline-first shell for the independent HondaHack rebuild. */
public final class MainActivity extends Activity {
    private LinearLayout content;
    private MeterCanvasView meterDisplay;
    private int meterColor = 0xFF263746;
    private int meterTextSize = 32;
    private int meterTextColor = 0xFFFFFFFF;
    private AudioFocusController audioFocusController;
    private TextView audioFocusStatus;
    private TextView startupVideoStatus;
    private String pendingPresetExportName;
    private android.net.Uri pendingApkUri;
    private android.speech.SpeechRecognizer speechRecognizer;
    private TextView voiceAssistantStatus;

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

        android.content.SharedPreferences startupPrefs =
                getSharedPreferences("startup_preferences", MODE_PRIVATE);
        android.widget.CheckBox openMeterOnLaunch = new android.widget.CheckBox(this);
        openMeterOnLaunch.setText("Open meter editor automatically on launch");
        openMeterOnLaunch.setChecked(startupPrefs.getBoolean("open_meter_on_launch", false));
        openMeterOnLaunch.setOnCheckedChangeListener((button, checked) ->
                startupPrefs.edit().putBoolean("open_meter_on_launch", checked).apply());
        content.addView(openMeterOnLaunch);
        TextView startupNote = new TextView(this);
        startupNote.setText("Optional local startup preference. It only scrolls to the offline meter editor; it does not start the app at device boot.");
        startupNote.setTextSize(12);
        content.addView(startupNote);
        addStartupVideoSettings();

        addSection("Device capability (read-only)");
        TextView facts = new TextView(this);
        facts.setText(new CapabilitySnapshot().toDisplayString());
        facts.setTextSize(14);
        facts.setPadding(dp(14), dp(12), dp(14), dp(12));
        facts.setBackgroundColor(0xFFEFF2F5);
        content.addView(facts);

        addSection("Feature preview");
        int meterSectionIndex = content.getChildCount();
        addMeterPreview();
        addPresetManagement();
        addKeyMappingDemo();
        addNavigationDemo();
        addLocalApkInstaller();
        addAudioFocusDemo();
        addStorageDiagnostics();
        addDisplayControls();
        addOnDeviceVoiceAssistant();

        Button refresh = new Button(this);
        refresh.setText("Refresh device information");
        refresh.setOnClickListener(v -> recreate());
        content.addView(refresh, matchWrap());

        TextView safety = new TextView(this);
        safety.setText("Safety baseline: no root commands, Xposed hooks, signature spoofing, remote revocation, silent installs, or system-partition writes.");
        safety.setTextSize(12);
        safety.setPadding(0, dp(18), 0, 0);
        content.addView(safety);

        scroll.post(() -> {
            if (startupPrefs.getBoolean("open_meter_on_launch", false)
                    && meterSectionIndex < content.getChildCount()) {
                scroll.smoothScrollTo(0, content.getChildAt(meterSectionIndex).getTop());
            }
        });
    }

    private void addStartupVideoSettings() {
        addSection("Startup video · local preview");
        android.content.SharedPreferences prefs =
                getSharedPreferences("startup_preferences", MODE_PRIVATE);
        android.widget.CheckBox enabled = new android.widget.CheckBox(this);
        enabled.setText("Enable local startup-video preview controls");
        enabled.setChecked(prefs.getBoolean("startup_video_enabled", false));
        enabled.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean("startup_video_enabled", checked).apply());
        content.addView(enabled);

        startupVideoStatus = new TextView(this);
        updateStartupVideoStatus(prefs.getString("startup_video_uri", null));
        content.addView(startupVideoStatus);

        Button choose = new Button(this);
        choose.setText("Choose local video");
        choose.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, 7601);
        });
        content.addView(choose, matchWrap());

        Button preview = new Button(this);
        preview.setText("Preview selected video");
        preview.setOnClickListener(v -> {
            if (!enabled.isChecked()) {
                toast("Enable local video preview first");
                return;
            }
            String value = prefs.getString("startup_video_uri", null);
            if (value == null || value.isEmpty()) {
                toast("Choose a video first");
                return;
            }
            showStartupVideoPreview(android.net.Uri.parse(value));
        });
        content.addView(preview, matchWrap());

        Button clear = new Button(this);
        clear.setText("Clear selected video");
        clear.setOnClickListener(v -> {
            String value = prefs.getString("startup_video_uri", null);
            if (value != null) {
                try {
                    getContentResolver().releasePersistableUriPermission(
                            android.net.Uri.parse(value),
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {}
            }
            prefs.edit().remove("startup_video_uri").apply();
            updateStartupVideoStatus(null);
        });
        content.addView(clear, matchWrap());

        TextView explanation = new TextView(this);
        explanation.setText("The selected video is stored as a local document reference. Playback starts only after you tap Preview. No boot receiver, automatic playback, network access, or background service is used.");
        explanation.setTextSize(12);
        content.addView(explanation);
    }

    private void updateStartupVideoStatus(String value) {
        if (startupVideoStatus == null) return;
        if (value == null || value.isEmpty()) {
            startupVideoStatus.setText("Selected video: none");
            return;
        }
        android.net.Uri uri = android.net.Uri.parse(value);
        String displayName = value;
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (column >= 0) displayName = cursor.getString(column);
            }
        } catch (RuntimeException ignored) {}
        startupVideoStatus.setText("Selected video: " + displayName);
    }

    private void showStartupVideoPreview(android.net.Uri uri) {
        android.widget.VideoView video = new android.widget.VideoView(this);
        android.widget.MediaController controls = new android.widget.MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        video.setVideoURI(uri);
        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.addView(video, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Local video preview")
                .setView(frame)
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnDismissListener(ignored -> video.stopPlayback());
        dialog.show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode,
            android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 7605) {
            android.net.Uri uri = pendingApkUri;
            if (uri == null) {
                String saved = getSharedPreferences("apk_installer", MODE_PRIVATE)
                        .getString("pending_uri", null);
                if (saved != null) uri = android.net.Uri.parse(saved);
            }
            pendingApkUri = null;
            getSharedPreferences("apk_installer", MODE_PRIVATE).edit().remove("pending_uri").apply();
            if (uri != null && android.os.Build.VERSION.SDK_INT >= 26
                    && getPackageManager().canRequestPackageInstalls()) {
                launchApkInstaller(uri);
            } else {
                toast("Install permission was not enabled. No APK was installed.");
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == 7603) pendingPresetExportName = null;
            if (requestCode == 7604) pendingApkUri = null;
            return;
        }
        android.net.Uri uri = data.getData();
        if (requestCode == 7601) {
            try {
                int granted = data.getFlags() & android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (granted == 0) throw new SecurityException("Read permission was not granted");
                getContentResolver().takePersistableUriPermission(uri, granted);
            } catch (SecurityException ignored) {
                toast("The document provider did not grant persistent access. Choose a file from a supported provider.");
                return;
            }
            getSharedPreferences("startup_preferences", MODE_PRIVATE).edit()
                    .putString("startup_video_uri", uri.toString()).apply();
            updateStartupVideoStatus(uri.toString());
        } else if (requestCode == 7602) {
            try {
                String source = readJsonDocument(uri);
                String normalized = PresetLibrary.importPreset(source);
                org.json.JSONObject item = new org.json.JSONObject(normalized);
                String name = item.optString("name", "Imported");
                android.content.SharedPreferences p = getSharedPreferences("named_meter_presets", MODE_PRIVATE);
                p.edit().putString("library",
                        PresetLibrary.put(p.getString("library", ""), name, normalized)).apply();
                toast("Imported preset: " + name);
            } catch (Exception e) {
                toast("JSON import failed: " + e.getMessage());
            }
        } else if (requestCode == 7603) {
            String name = pendingPresetExportName;
            pendingPresetExportName = null;
            if (name == null) return;
            try {
                android.content.SharedPreferences p = getSharedPreferences("named_meter_presets", MODE_PRIVATE);
                String json = PresetLibrary.exportPreset(p.getString("library", ""), name);
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) throw new java.io.IOException("Unable to open destination");
                    output.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    output.flush();
                }
                toast("Saved JSON file: " + name);
            } catch (Exception e) {
                toast("JSON export failed: " + e.getMessage());
            }
        } else if (requestCode == 7604) {
            int readGrant = data.getFlags() & android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (readGrant != 0) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, readGrant);
                } catch (SecurityException ignored) {}
            }
            getSharedPreferences("apk_installer", MODE_PRIVATE).edit()
                    .putString("pending_uri", uri.toString()).apply();
            launchApkInstaller(uri);
        }
    }

    private void launchApkInstaller(android.net.Uri uri) {
        if (android.os.Build.VERSION.SDK_INT >= 26
                && !getPackageManager().canRequestPackageInstalls()) {
            pendingApkUri = uri;
            android.content.Intent settings = new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(settings, 7605);
            } catch (RuntimeException e) {
                pendingApkUri = null;
                toast("Open this app's Install unknown apps setting manually, then retry.");
            }
            return;
        }
        getSharedPreferences("apk_installer", MODE_PRIVATE).edit().remove("pending_uri").apply();
        android.content.Intent install = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(install);
        } catch (RuntimeException e) {
            toast("Unable to open the system package installer: " + e.getMessage());
        }
    }

    private String readJsonDocument(android.net.Uri uri) throws java.io.IOException {
        try (java.io.InputStream input = getContentResolver().openInputStream(uri);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            if (input == null) throw new java.io.IOException("Unable to open selected file");
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 1024 * 1024) throw new java.io.IOException("JSON file exceeds 1 MB");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (getSharedPreferences("display_preferences", MODE_PRIVATE)
                .getBoolean("keep_screen_awake", false)) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 7606) {
            if (grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startOnDeviceVoiceRecognition();
            } else if (voiceAssistantStatus != null) {
                voiceAssistantStatus.setText("Microphone permission denied; voice recognition did not start.");
            }
        }
    }

    @Override protected void onStop() {
        releaseVoiceRecognizer();
        if (audioFocusController != null) audioFocusController.release();
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onStop();
    }

    private void addAudioFocusDemo() {
        addSection("Audio focus · explicit demo");
        audioFocusController = new AudioFocusController(this);
        TextView note = new TextView(this);
        note.setText("Audio focus is requested only after you enable this option and tap Request. No sound is played and system volume is not changed. Focus is released when this screen stops.");
        note.setTextSize(13); content.addView(note);
        android.widget.CheckBox optIn = new android.widget.CheckBox(this);
        optIn.setText("I allow this app to request temporary navigation audio focus");
        content.addView(optIn);
        audioFocusStatus = new TextView(this);
        audioFocusStatus.setText("Audio focus: not requested");
        content.addView(audioFocusStatus);
        Button request = new Button(this); request.setText("Request temporary audio focus");
        request.setOnClickListener(v -> {
            if (!optIn.isChecked()) { audioFocusStatus.setText("Enable the opt-in checkbox first."); return; }
            boolean granted = audioFocusController.requestTransientFocus(change -> runOnUiThread(() -> {
                String state = change == android.media.AudioManager.AUDIOFOCUS_GAIN ? "gained"
                        : change == android.media.AudioManager.AUDIOFOCUS_LOSS ? "lost"
                        : change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ? "temporarily lost"
                        : change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ? "may duck" : "changed";
                audioFocusStatus.setText("Audio focus: " + state);
            }));
            audioFocusStatus.setText(granted ? "Audio focus: granted (temporary)" : "Audio focus: not granted");
        });
        content.addView(request, matchWrap());
        Button release = new Button(this); release.setText("Release audio focus");
        release.setOnClickListener(v -> { audioFocusController.release(); audioFocusStatus.setText("Audio focus: released"); });
        content.addView(release, matchWrap());
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
        addSection("Multi-widget offline meter editor");
        try {
            MeterPreset preset = MeterPresetRepository.loadDemo(this);
            android.content.SharedPreferences prefs = getSharedPreferences("meter_editor", MODE_PRIVATE);
            meterColor = prefs.getInt("background", 0xFF263746);
            int savedCount = Math.max(1, Math.min(32, prefs.getInt("widget_count", preset.views.size())));
            java.util.List<MeterPreset.ViewSpec> specs = new java.util.ArrayList<>();
            for (int i = 0; i < savedCount; i++) {
                MeterPreset.ViewSpec base = i < preset.views.size() ? preset.views.get(i)
                        : new MeterPreset.ViewSpec(10,10,35,20,"{rpm} rpm","center",20,"#ffffd54f");
                specs.add(new MeterPreset.ViewSpec(prefs.getInt("left_" + i, base.left),
                        prefs.getInt("top_" + i, base.top), prefs.getInt("width_" + i, base.width),
                        prefs.getInt("height_" + i, base.height), prefs.getString("template_" + i, base.template),
                        prefs.getString("align_" + i, base.alignment),
                        prefs.getInt("size_" + i, base.textSize),
                        prefs.getString("color_" + i, base.textColor)));
            }
            int[] speedValue = {prefs.getInt("speed", 0)};
            MeterCanvasView display = new MeterCanvasView(this);
            LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
            displayParams.bottomMargin = dp(12);
            content.addView(display, displayParams);

            TextView status = new TextView(this);
            content.addView(status);
            TextView speedLabel = new TextView(this);
            SeekBar speed = new SeekBar(this);
            speed.setMax(240);
            speed.setProgress(Math.max(0, Math.min(240, speedValue[0])));
            addLabeledControl("Demo speed", speedLabel, speed);

            String[] labels = new String[specs.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = "Widget " + (i + 1);
            Spinner selector = new Spinner(this);
            selector.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels));
            content.addView(selector, matchWrap());

            TextView sizeLabel = new TextView(this), leftLabel = new TextView(this),
                    topLabel = new TextView(this), widthLabel = new TextView(this),
                    heightLabel = new TextView(this);
            SeekBar size = new SeekBar(this), left = new SeekBar(this), top = new SeekBar(this),
                    width = new SeekBar(this), height = new SeekBar(this);
            size.setMax(86); left.setMax(99); top.setMax(99); width.setMax(99); height.setMax(99);
            addLabeledControl("Text size", sizeLabel, size);
            addLabeledControl("Horizontal position", leftLabel, left);
            addLabeledControl("Vertical position", topLabel, top);
            addLabeledControl("Widget width", widthLabel, width);
            addLabeledControl("Widget height", heightLabel, height);
            int[] selected = {0};

            Runnable render = () -> {
                java.util.List<MeterCanvasView.Widget> widgets = new java.util.ArrayList<>();
                int value = speed.getProgress(), rpm = 850 + value * 32;
                for (MeterPreset.ViewSpec spec : specs) {
                    widgets.add(new MeterCanvasView.Widget(spec, spec.renderMock(value, rpm, 68, 82)));
                }
                display.setWidgets(widgets, meterColor);
                status.setText("Preset: " + preset.name + " · " + specs.size() + " widgets");
                speedLabel.setText("Demo speed: " + value + " km/h");
                MeterPreset.ViewSpec s = specs.get(selected[0]);
                sizeLabel.setText("Text size: " + s.textSize + " sp");
                leftLabel.setText("Horizontal position: " + s.left + "%");
                topLabel.setText("Vertical position: " + s.top + "%");
                widthLabel.setText("Widget width: " + s.width + "%");
                heightLabel.setText("Widget height: " + s.height + "%");
            };
            Runnable loadSelected = () -> {
                MeterPreset.ViewSpec s = specs.get(selected[0]);
                size.setProgress(s.textSize - 10);
                left.setProgress(s.left); top.setProgress(s.top);
                width.setProgress(s.width - 1); height.setProgress(s.height - 1);
                render.run();
            };
            selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, android.view.View view,
                        int position, long id) { selected[0] = position; loadSelected.run(); }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
            speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                    render.run(); if (user) prefs.edit().putInt("speed", value).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            android.widget.SeekBar.OnSeekBarChangeListener editor = new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                    if (!user) return;
                    MeterPreset.ViewSpec prior = specs.get(selected[0]);
                    int l = bar == left ? value : left.getProgress();
                    int t = bar == top ? value : top.getProgress();
                    int w = bar == width ? value + 1 : width.getProgress() + 1;
                    int h = bar == height ? value + 1 : height.getProgress() + 1;
                    int z = bar == size ? value + 10 : size.getProgress() + 10;
                    MeterPreset.ViewSpec next = new MeterPreset.ViewSpec(l,t,w,h,
                            prior.template,prior.alignment,z,prior.textColor);
                    specs.set(selected[0], next);
                    prefs.edit().putInt("left_" + selected[0],next.left)
                            .putInt("top_" + selected[0],next.top).putInt("width_" + selected[0],next.width)
                            .putInt("height_" + selected[0],next.height).putInt("size_" + selected[0],next.textSize).apply();
                    render.run();
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            };
            size.setOnSeekBarChangeListener(editor); left.setOnSeekBarChangeListener(editor);
            top.setOnSeekBarChangeListener(editor); width.setOnSeekBarChangeListener(editor);
            height.setOnSeekBarChangeListener(editor);

            Button add = new Button(this);
            add.setText("Add widget");
            add.setOnClickListener(v -> {
                if (specs.size() >= 32) { status.setText("Maximum 32 widgets reached"); return; }
                specs.add(new MeterPreset.ViewSpec(10,10,35,20,"{rpm} rpm","center",20,"#ffffd54f"));
                String[] updated = new String[specs.size()];
                for (int i=0;i<updated.length;i++) updated[i]="Widget "+(i+1);
                selector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,updated));
                selected[0] = specs.size()-1;
                selector.setSelection(selected[0]);
                loadSelected.run();
                status.setText("Widget added. Press Save widget layout to store it.");
            });
            content.addView(add, matchWrap());
            Button remove = new Button(this);
            remove.setText("Remove selected widget");
            remove.setOnClickListener(v -> {
                if (specs.size() == 1) { status.setText("At least one widget must remain"); return; }
                specs.remove(selected[0]);
                String[] updated = new String[specs.size()];
                for (int i=0;i<updated.length;i++) updated[i]="Widget "+(i+1);
                selector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,updated));
                selected[0]=Math.min(selected[0],specs.size()-1);
                selector.setSelection(selected[0]);
                loadSelected.run();
            });
            content.addView(remove, matchWrap());

            Button theme = new Button(this);
            theme.setText("Change meter background");
            theme.setOnClickListener(v -> {
                meterColor = meterColor == 0xFF263746 ? 0xFF101820
                        : (meterColor == 0xFF101820 ? 0xFF173D32 : 0xFF263746);
                prefs.edit().putInt("background",meterColor).apply(); render.run();
            });
            content.addView(theme, matchWrap());
            Button save = new Button(this);
            save.setText("Save widget layout");
            save.setOnClickListener(v -> {
                android.content.SharedPreferences.Editor edit = prefs.edit();
                edit.putInt("widget_count", specs.size());
                for (int i=0;i<specs.size();i++) {
                    MeterPreset.ViewSpec s=specs.get(i);
                    edit.putInt("left_"+i,s.left).putInt("top_"+i,s.top)
                            .putInt("width_"+i,s.width).putInt("height_"+i,s.height)
                            .putInt("size_"+i,s.textSize).putString("color_"+i,s.textColor)
                            .putString("template_"+i,s.template).putString("align_"+i,s.alignment);
                }
                edit.apply(); status.setText("Saved "+specs.size()+" widgets on this device");
            });
            content.addView(save, matchWrap());
            Button reset = new Button(this);
            reset.setText("Reset meter editor");
            reset.setOnClickListener(v -> { prefs.edit().clear().apply(); recreate(); });
            content.addView(reset, matchWrap());
            loadSelected.run();
        } catch (Exception error) {
            addFeature("Instrument meter", "Demo preset unavailable: " + error.getMessage());
        }
    }


    private void addPresetManagement() {
        addSection("Named meter presets");
        Button save = new Button(this);
        save.setText("Save current layout as named preset");
        save.setOnClickListener(v -> askPresetName("Preset name", name -> {
            try {
                String json = currentPresetJson(name);
                android.content.SharedPreferences p = getSharedPreferences("named_meter_presets", MODE_PRIVATE);
                p.edit().putString("library", PresetLibrary.put(p.getString("library", ""), name, json)).apply();
                toast("Saved: " + name);
            } catch (Exception e) { toast("Save failed: " + e.getMessage()); }
        }));
        content.addView(save, matchWrap());
        Button manage = new Button(this);
        manage.setText("Manage / export saved presets");
        manage.setOnClickListener(v -> managePresets());
        content.addView(manage, matchWrap());
        Button importButton = new Button(this);
        importButton.setText("Import preset JSON (paste)");
        importButton.setOnClickListener(v -> {
            android.widget.EditText field = new android.widget.EditText(this);
            field.setHint("Paste a JSON preset");
            field.setMinLines(4);
            new android.app.AlertDialog.Builder(this).setTitle("Import preset")
                .setView(field).setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (d,w) -> {
                    try {
                        String json = PresetLibrary.importPreset(field.getText().toString());
                        org.json.JSONObject item = new org.json.JSONObject(json);
                        String name = item.optString("name", "Imported");
                        android.content.SharedPreferences p = getSharedPreferences("named_meter_presets", MODE_PRIVATE);
                        p.edit().putString("library", PresetLibrary.put(p.getString("library", ""), name, json)).apply();
                        toast("Imported: " + name);
                    } catch (Exception e) { toast("Import failed: " + e.getMessage()); }
                }).show();
        });
        content.addView(importButton, matchWrap());
        Button importFile = new Button(this);
        importFile.setText("Import preset JSON file");
        importFile.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/json", "text/plain"});
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, 7602);
        });
        content.addView(importFile, matchWrap());
    }

    private String currentPresetJson(String name) throws Exception {
        MeterPreset base = MeterPresetRepository.loadDemo(this);
        android.content.SharedPreferences p = getSharedPreferences("meter_editor", MODE_PRIVATE);
        int count = Math.max(1, Math.min(32, p.getInt("widget_count", base.views.size())));
        org.json.JSONObject root = new org.json.JSONObject(); root.put("name", name);
        org.json.JSONArray views = new org.json.JSONArray();
        for (int i=0;i<count;i++) {
            MeterPreset.ViewSpec s = i < base.views.size() ? base.views.get(i)
                : new MeterPreset.ViewSpec(10,10,35,20,"{rpm} rpm","center",20,"#ffffd54f");
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("left",p.getInt("left_"+i,s.left)); item.put("top",p.getInt("top_"+i,s.top));
            item.put("width",p.getInt("width_"+i,s.width)); item.put("height",p.getInt("height_"+i,s.height));
            item.put("text",p.getString("template_"+i,s.template)); item.put("alignment",p.getString("align_"+i,s.alignment));
            item.put("textSize",p.getInt("size_"+i,s.textSize)); item.put("textColor",p.getString("color_"+i,s.textColor));
            views.put(item);
        }
        root.put("views",views); return root.toString();
    }

    private void managePresets() {
        android.content.SharedPreferences p = getSharedPreferences("named_meter_presets", MODE_PRIVATE);
        try {
            org.json.JSONArray names = new org.json.JSONArray(PresetLibrary.names(p.getString("library","")));
            String[] labels = new String[names.length()];
            for(int i=0;i<names.length();i++) labels[i]=names.getString(i);
            if(labels.length==0){toast("No saved presets");return;}
            new android.app.AlertDialog.Builder(this).setTitle("Select preset")
                .setItems(labels,(d,index)->presetActions(labels[index])).show();
        } catch(Exception e){toast(e.getMessage());}
    }

    private void presetActions(String name) {
        String[] actions={"Load","Rename","Duplicate","Share JSON","Save JSON file","Delete"};
        new android.app.AlertDialog.Builder(this).setTitle(name).setItems(actions,(d,which)->{
            android.content.SharedPreferences p=getSharedPreferences("named_meter_presets",MODE_PRIVATE);
            String library=p.getString("library","");
            try {
                if(which==0){
                    org.json.JSONObject root=new org.json.JSONObject(PresetLibrary.exportPreset(library,name));
                    org.json.JSONArray views=root.getJSONArray("views");
                    android.content.SharedPreferences.Editor edit=getSharedPreferences("meter_editor",MODE_PRIVATE).edit();
                    edit.putInt("widget_count",views.length());
                    for(int i=0;i<views.length();i++){
                        org.json.JSONObject item=views.getJSONObject(i);
                        edit.putInt("left_"+i,item.getInt("left")).putInt("top_"+i,item.getInt("top"))
                            .putInt("width_"+i,item.getInt("width")).putInt("height_"+i,item.getInt("height"))
                            .putInt("size_"+i,item.optInt("textSize",32)).putString("template_"+i,item.optString("text","{speed}"))
                            .putString("align_"+i,item.optString("alignment","center")).putString("color_"+i,item.optString("textColor","#ffffffff"));
                    }
                    edit.apply(); recreate();
                } else if(which==1) askPresetName("New name",next->{try{p.edit().putString("library",PresetLibrary.rename(library,name,next)).apply();toast("Renamed");}catch(Exception e){toast(e.getMessage());}});
                else if(which==2) askPresetName("Copy name",next->{try{p.edit().putString("library",PresetLibrary.duplicate(library,name,next)).apply();toast("Duplicated");}catch(Exception e){toast(e.getMessage());}});
                else if(which==3){
                    android.content.Intent share=new android.content.Intent(android.content.Intent.ACTION_SEND);
                    share.setType("application/json");share.putExtra(android.content.Intent.EXTRA_TEXT,PresetLibrary.exportPreset(library,name));
                    startActivity(android.content.Intent.createChooser(share,"Share preset JSON"));
                } else if(which==4) {
                    pendingPresetExportName = name;
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(android.content.Intent.EXTRA_TITLE, name.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
                    intent.addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    startActivityForResult(intent, 7603);
                } else {p.edit().putString("library",PresetLibrary.delete(library,name)).apply();toast("Deleted");}
            }catch(Exception e){toast("Operation failed: "+e.getMessage());}
        }).show();
    }
    private interface NameResult { void accept(String name); }
    private void askPresetName(String title,NameResult callback){
        android.widget.EditText field=new android.widget.EditText(this);field.setSingleLine(true);
        new android.app.AlertDialog.Builder(this).setTitle(title).setView(field)
            .setNegativeButton("Cancel",null).setPositiveButton("OK",(d,w)->callback.accept(field.getText().toString())).show();
    }
    private void toast(String value){android.widget.Toast.makeText(this,value,android.widget.Toast.LENGTH_LONG).show();}

    private void addLabeledControl(String title, TextView label, SeekBar control) {
        label.setText(title);
        label.setTextSize(14);
        content.addView(label);
        content.addView(control, matchWrap());
    }

    private void addOnDeviceVoiceAssistant() {
        addSection("Voice assistant · on-device recognition");
        TextView note = new TextView(this);
        note.setText("Speech recognition starts only after you tap Start and grant microphone permission. This prototype uses Android's on-device recognizer only (Android 12+ where available); it does not fall back to cloud recognition, listen in the background, or send vehicle commands.");
        note.setTextSize(12);
        content.addView(note);

        voiceAssistantStatus = new TextView(this);
        voiceAssistantStatus.setText("Voice assistant: stopped");
        content.addView(voiceAssistantStatus);
        Button start = new Button(this);
        start.setText("Start on-device voice recognition");
        start.setOnClickListener(v -> requestOnDeviceVoiceRecognition());
        content.addView(start, matchWrap());

        Button stop = new Button(this);
        stop.setText("Stop listening");
        stop.setOnClickListener(v -> {
            releaseVoiceRecognizer();
            voiceAssistantStatus.setText("Voice assistant: stopped");
        });
        content.addView(stop, matchWrap());
    }

    private void requestOnDeviceVoiceRecognition() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            voiceAssistantStatus.setText("On-device recognition requires Android 12 or later.");
            return;
        }
        if (!android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            voiceAssistantStatus.setText("No on-device speech recognition service is available on this device.");
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7606);
            return;
        }
        startOnDeviceVoiceRecognition();
    }

    private void startOnDeviceVoiceRecognition() {
        if (android.os.Build.VERSION.SDK_INT < 31
                || !android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            if (voiceAssistantStatus != null)
                voiceAssistantStatus.setText("On-device speech recognition is unavailable.");
            return;
        }
        releaseVoiceRecognizer();
        try {
            speechRecognizer = android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) {
                    voiceAssistantStatus.setText("Listening on device…");
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    voiceAssistantStatus.setText("Processing on device…");
                }
                @Override public void onError(int error) {
                    voiceAssistantStatus.setText("Recognition stopped (code " + error + "). Tap Start to retry.");
                }
                @Override public void onResults(android.os.Bundle results) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(
                            android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    voiceAssistantStatus.setText(matches == null || matches.isEmpty()
                            ? "No speech recognized." : "Recognized: " + matches.get(0));
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) {
                    java.util.ArrayList<String> matches = partialResults.getStringArrayList(
                            android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty())
                        voiceAssistantStatus.setText("Recognizing: " + matches.get(0));
                }
                @Override public void onEvent(int eventType, android.os.Bundle params) {}
            });
            android.content.Intent intent = new android.content.Intent(
                    android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            speechRecognizer.startListening(intent);
            voiceAssistantStatus.setText("Starting on-device recognition…");
        } catch (RuntimeException e) {
            releaseVoiceRecognizer();
            voiceAssistantStatus.setText("Unable to start on-device recognition: " + e.getMessage());
        }
    }

    private void releaseVoiceRecognizer() {
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (RuntimeException ignored) {}
            try { speechRecognizer.destroy(); } catch (RuntimeException ignored) {}
            speechRecognizer = null;
        }
    }

    private void addDisplayControls() {
        addSection("Display controls · app window only");
        android.content.SharedPreferences prefs =
                getSharedPreferences("display_preferences", MODE_PRIVATE);

        android.widget.CheckBox keepAwake = new android.widget.CheckBox(this);
        keepAwake.setText("Keep screen awake while this app is visible");
        keepAwake.setChecked(prefs.getBoolean("keep_screen_awake", false));
        keepAwake.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean("keep_screen_awake", checked).apply();
            if (checked) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
        content.addView(keepAwake);

        android.widget.CheckBox useSystem = new android.widget.CheckBox(this);
        useSystem.setText("Use system screen brightness");
        useSystem.setChecked(prefs.getBoolean("use_system_brightness", true));
        content.addView(useSystem);

        TextView brightnessLabel = new TextView(this);
        SeekBar brightness = new SeekBar(this);
        brightness.setMax(100);
        brightness.setProgress(Math.max(1, Math.min(100, prefs.getInt("app_brightness", 70))));
        content.addView(brightnessLabel);
        content.addView(brightness, matchWrap());
        brightness.setEnabled(!useSystem.isChecked());
        Runnable apply = () -> {
            boolean system = useSystem.isChecked();
            brightness.setEnabled(!system);
            int value = brightness.getProgress();
            applyWindowBrightness(value, system);
            brightnessLabel.setText(system ? "Brightness: system default"
                    : "App window brightness: " + Math.max(1, value) + "%");
            prefs.edit().putBoolean("use_system_brightness", system)
                    .putInt("app_brightness", value).apply();
        };
        useSystem.setOnCheckedChangeListener((button, checked) -> apply.run());
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && !useSystem.isChecked()) apply.run();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        Button reset = new Button(this);
        reset.setText("Restore system brightness");
        reset.setOnClickListener(v -> useSystem.setChecked(true));
        content.addView(reset, matchWrap());
        apply.run();

        TextView note = new TextView(this);
        note.setText("Brightness affects only this app's window. Keep-awake is active only while this screen is visible and is cleared when the app screen stops. No global display setting is changed.");
        note.setTextSize(12);
        content.addView(note);
    }

    private void applyWindowBrightness(int value, boolean useSystem) {
        android.view.WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = useSystem
                ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                : Math.max(1, Math.min(100, value)) / 100.0f;
        getWindow().setAttributes(attributes);
    }

    private void addStorageDiagnostics() {
        addSection("Storage diagnostics · read-only");
        TextView report = new TextView(this);
        report.setTextSize(13);
        report.setPadding(dp(12), dp(12), dp(12), dp(12));
        report.setBackgroundColor(0xFFF5F6F7);
        Runnable refresh = () -> report.setText(buildStorageReport());
        refresh.run();
        content.addView(report, matchWrap());

        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh storage diagnostics");
        refreshButton.setOnClickListener(v -> refresh.run());
        content.addView(refreshButton, matchWrap());

        Button share = new Button(this);
        share.setText("Share storage report");
        share.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_TEXT, buildStorageReport());
            startActivity(android.content.Intent.createChooser(intent, "Share storage diagnostics"));
        });
        content.addView(share, matchWrap());

        TextView note = new TextView(this);
        note.setText("This diagnostic checks app-accessible storage only. It does not request all-files access, change platform.xml, remount partitions, or modify shared storage.");
        note.setTextSize(12);
        content.addView(note);
    }

    private String buildStorageReport() {
        StringBuilder report = new StringBuilder();
        report.append("HondaHack Rebuild · storage report\n");
        report.append("Android API: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        report.append("Shared storage state: ")
                .append(android.os.Environment.getExternalStorageState()).append("\n");
        report.append("Internal app files: ").append(getFilesDir().getAbsolutePath()).append("\n");
        report.append("Internal available: ").append(formatStorageBytes(getFilesDir().getUsableSpace())).append("\n");
        report.append("App cache available: ").append(formatStorageBytes(getCacheDir().getUsableSpace())).append("\n");
        java.io.File external = getExternalFilesDir(null);
        if (external == null) {
            report.append("App-specific external storage: unavailable\n");
        } else {
            report.append("App-specific external: ").append(external.getAbsolutePath()).append("\n");
            report.append("External available: ").append(formatStorageBytes(external.getUsableSpace())).append("\n");
        }
        report.append("External storage emulated: ")
                .append(android.os.Environment.isExternalStorageEmulated()).append("\n");
        report.append("All-files access: not requested by this app\n");
        report.append("System partition changes: none");
        return report.toString();
    }

    private String formatStorageBytes(long bytes) {
        if (bytes < 0) return "unknown";
        if (bytes >= 1024L * 1024L * 1024L)
            return String.format(java.util.Locale.US, "%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0));
        if (bytes >= 1024L * 1024L)
            return String.format(java.util.Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0));
        return String.format(java.util.Locale.US, "%.1f KiB", bytes / 1024.0);
    }

    private void addLocalApkInstaller() {
        addSection("Local APK installer · system confirmation");
        TextView note = new TextView(this);
        note.setText("Choose an APK from a document provider. Android's package installer handles the installation and displays its own confirmation. This app never installs silently or uses root.");
        note.setTextSize(13);
        content.addView(note);
        Button choose = new Button(this);
        choose.setText("Choose APK to install");
        choose.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/vnd.android.package-archive", "application/octet-stream"});
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, 7604);
        });
        content.addView(choose, matchWrap());
    }

    private void addNavigationDemo() {
        addSection("Navigation app integration");
        TextView note = new TextView(this);
        note.setText("Only detects and opens installed navigation apps after you tap. It never force-stops or changes another app.");
        note.setTextSize(13);
        content.addView(note);

        String[][] candidates = {
                {"Amap Auto", "com.autonavi.amapauto"},
                {"Baidu Map Auto", "com.baidu.BaiduMap.auto"},
                {"Baidu Navi Auto", "com.baidu.naviauto"}
        };
        for (String[] candidate : candidates) {
            String label = candidate[0];
            String packageName = candidate[1];
            boolean installed = NavigationAppHelper.isLaunchable(this, packageName);
            TextView state = new TextView(this);
            state.setText(label + " · " + (installed ? "Launchable" : "Not installed / no launcher")
                    + "\n" + packageName);
            state.setTextSize(14);
            state.setPadding(dp(10), dp(8), dp(10), dp(4));
            content.addView(state);

            Button launch = new Button(this);
            launch.setText("Open " + label);
            launch.setEnabled(installed);
            launch.setOnClickListener(v -> {
                try {
                    NavigationAppHelper.launch(this, packageName);
                } catch (RuntimeException error) {
                    state.setText(label + " could not be opened: " + error.getMessage());
                }
            });
            content.addView(launch, matchWrap());
        }
    }

    private void addKeyMappingDemo() {
        addSection("Steering key mapping · simulator");
        TextView status = new TextView(this);
        status.setText("Assign actions to simulated steering keys. No physical key events are captured.");
        status.setTextSize(14);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackgroundColor(0xFFF5F6F7);
        content.addView(status);

        android.content.SharedPreferences prefs =
                getSharedPreferences("key_mapping_demo", MODE_PRIVATE);
        String[] keyNames = {"Steering key A", "Steering key B",
                "Steering key C", "Steering key D"};
        String[] defaults = {"Volume up", "Next track", "Show meter", "Voice assistant"};
        String[] actions = {"No action", "Volume up", "Volume down", "Next track",
                "Previous track", "Play / pause", "Show meter", "Voice assistant",
                "Open navigation"};
        for (int i = 0; i < keyNames.length; i++) {
            final String keyName = keyNames[i];
            final String preferenceKey = "mapping_" + i;
            TextView label = new TextView(this);
            label.setText(keyName);
            label.setTextSize(14);
            content.addView(label);

            Spinner selector = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, actions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            selector.setAdapter(adapter);
            String saved = prefs.getString(preferenceKey, defaults[i]);
            int selected = 0;
            for (int j = 0; j < actions.length; j++) {
                if (actions[j].equals(saved)) selected = j;
            }
            selector.setSelection(selected);
            selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent,
                        android.view.View view, int position, long id) {
                    prefs.edit().putString(preferenceKey, actions[position]).apply();
                    status.setText(keyName + " is assigned to: " + actions[position]
                            + "\nSimulation only — no command is sent to the car.");
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
            content.addView(selector, matchWrap());

            Button simulate = new Button(this);
            simulate.setText("Simulate " + keyName);
            simulate.setOnClickListener(v -> status.setText("Simulated " + keyName
                    + " → " + selector.getSelectedItem()
                    + "\nNo physical steering-wheel input was intercepted."));
            content.addView(simulate, matchWrap());
        }
    }

    private void addFeature(String name, String detail) {
        TextView row = new TextView(this);
        row.setText(name + "\n" + detail);
        row.setTextSize(14);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundColor(0xFFF5F6F7);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(8);
        content.addView(row, lp);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
