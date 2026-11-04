package cn.autohack.rebuild;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Validated, offline-only meter layout model. Coordinates use a 0..100 canvas. */
public final class MeterPreset {
    public final String name;
    public final ViewSpec primaryView;

    private MeterPreset(String name, ViewSpec primaryView) {
        this.name = name;
        this.primaryView = primaryView;
    }

    public static MeterPreset fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String name = root.optString("name", "Custom");
        JSONArray views = root.optJSONArray("views");
        if (views == null || views.length() == 0) {
            throw new JSONException("Preset must contain at least one view");
        }
        JSONObject first = views.getJSONObject(0);
        int left = bounded(first, "left", 0, 100);
        int top = bounded(first, "top", 0, 100);
        int width = bounded(first, "width", 1, 100);
        int height = bounded(first, "height", 1, 100);
        if (left + width > 100 || top + height > 100) {
            throw new JSONException("View bounds exceed the 0..100 canvas");
        }
        String template = first.optString("text", "{speed}");
        String unsupported = template.replace("{speed}", "").replace("{rpm}", "")
                .replace("{fuel}", "").replace("{coolant}", "");
        if (!template.matches(".*\\\\{(speed|rpm|fuel|coolant)\\\\}.*")
                || unsupported.contains("{") || unsupported.contains("}")) {
            throw new JSONException("Template contains an unsupported telemetry token");
        }
        return new MeterPreset(name, new ViewSpec(left, top, width, height,
                template, first.optString("alignment", "center"),
                first.optInt("textSize", 32), first.optString("textColor", "#ffffffff")));
    }

    private static int bounded(JSONObject object, String key, int min, int max)
            throws JSONException {
        int value = object.getInt(key);
        if (value < min || value > max) {
            throw new JSONException(key + " outside allowed range");
        }
        return value;
    }

    public static final class ViewSpec {
        public final int left, top, width, height, textSize;
        public final String template, alignment, textColor;

        private ViewSpec(int left, int top, int width, int height, String template,
                         String alignment, int textSize, String textColor) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.template = template;
            this.alignment = alignment;
            this.textSize = Math.max(10, Math.min(textSize, 96));
            this.textColor = textColor;
        }

        public String renderMock(int speed, int rpm, int fuel, int coolant) {
            return template.replace("{speed}", String.valueOf(speed))
                    .replace("{rpm}", String.valueOf(rpm))
                    .replace("{fuel}", String.valueOf(fuel))
                    .replace("{coolant}", String.valueOf(coolant));
        }
    }
}
