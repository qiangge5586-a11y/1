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
        addAudioFocusDemo();
        addFeature("Storage diagnostics", "Read-only checks; no system file modifications");

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

    @Override protected void onStop() {
        if (audioFocusController != null) audioFocusController.release();
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
        String[] actions={"Load","Rename","Duplicate","Export JSON","Delete"};
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
                    startActivity(android.content.Intent.createChooser(share,"Export preset JSON"));
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
                    + "
" + packageName);
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
        row.setText(name + "
" + detail);
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
