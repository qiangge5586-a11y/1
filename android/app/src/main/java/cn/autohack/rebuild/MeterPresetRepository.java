package cn.autohack.rebuild;

import android.content.Context;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads bundled demo presets only; it never reads vehicle or system files. */
public final class MeterPresetRepository {
    private MeterPresetRepository() {}

    public static MeterPreset loadDemo(Context context) throws IOException, JSONException {
        try (InputStream input = context.getAssets().open("meter_presets.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return MeterPreset.fromJson(new String(output.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
