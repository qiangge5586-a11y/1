package cn.autohack.rebuild;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** JSON-backed named preset collection. Validation is delegated to MeterPreset. */
public final class PresetLibrary {
    private PresetLibrary() {}

    public static String put(String libraryJson, String name, String presetJson) throws JSONException {
        String clean = requireName(name);
        MeterPreset.fromJson(presetJson);
        JSONObject source = new JSONObject(presetJson);
        source.put("name", clean);
        JSONObject library = parseLibrary(libraryJson);
        JSONArray old = library.optJSONArray("presets");
        JSONArray next = new JSONArray();
        boolean replaced = false;
        if (old != null) for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.getJSONObject(i);
            if (clean.equals(item.optString("name"))) {
                if (!replaced) { next.put(source); replaced = true; }
            } else next.put(item);
        }
        if (!replaced) next.put(source);
        library.put("presets", next);
        return library.toString();
    }

    public static String rename(String libraryJson, String oldName, String newName) throws JSONException {
        String clean = requireName(newName);
        JSONObject library = parseLibrary(libraryJson);
        JSONArray items = library.getJSONArray("presets");
        if (contains(items, clean)) throw new JSONException("A preset with that name already exists");
        boolean found = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (oldName.equals(item.optString("name"))) { item.put("name", clean); found = true; break; }
        }
        if (!found) throw new JSONException("Preset not found");
        return library.toString();
    }

    public static String duplicate(String libraryJson, String sourceName, String copyName) throws JSONException {
        String clean = requireName(copyName);
        JSONObject library = parseLibrary(libraryJson);
        JSONArray items = library.getJSONArray("presets");
        if (contains(items, clean)) throw new JSONException("A preset with that name already exists");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (sourceName.equals(item.optString("name"))) {
                JSONObject copy = new JSONObject(item.toString());
                copy.put("name", clean);
                items.put(copy);
                return library.toString();
            }
        }
        throw new JSONException("Preset not found");
    }

    public static String delete(String libraryJson, String name) throws JSONException {
        JSONObject library = parseLibrary(libraryJson);
        JSONArray old = library.getJSONArray("presets");
        JSONArray next = new JSONArray();
        boolean found = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.getJSONObject(i);
            if (name.equals(item.optString("name"))) found = true; else next.put(item);
        }
        if (!found) throw new JSONException("Preset not found");
        library.put("presets", next);
        return library.toString();
    }

    public static String exportPreset(String libraryJson, String name) throws JSONException {
        JSONArray items = parseLibrary(libraryJson).getJSONArray("presets");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (name.equals(item.optString("name"))) {
                MeterPreset.fromJson(item.toString());
                return item.toString(2);
            }
        }
        throw new JSONException("Preset not found");
    }

    public static String importPreset(String presetJson) throws JSONException {
        MeterPreset preset = MeterPreset.fromJson(presetJson);
        JSONObject normalized = new JSONObject(presetJson);
        normalized.put("name", requireName(preset.name));
        return normalized.toString();
    }

    public static String names(String libraryJson) throws JSONException {
        JSONArray items = parseLibrary(libraryJson).getJSONArray("presets");
        JSONArray names = new JSONArray();
        for (int i = 0; i < items.length(); i++) names.put(items.getJSONObject(i).optString("name"));
        return names.toString();
    }

    private static JSONObject parseLibrary(String json) throws JSONException {
        JSONObject object = (json == null || json.trim().isEmpty())
                ? new JSONObject() : new JSONObject(json);
        if (!object.has("presets")) object.put("presets", new JSONArray());
        if (!(object.opt("presets") instanceof JSONArray)) throw new JSONException("Invalid preset library");
        return object;
    }

    private static boolean contains(JSONArray items, String name) throws JSONException {
        for (int i = 0; i < items.length(); i++)
            if (name.equals(items.getJSONObject(i).optString("name"))) return true;
        return false;
    }

    private static String requireName(String name) throws JSONException {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 40) throw new JSONException("Name must be 1–40 characters");
        return clean;
    }
}
