package cn.autohack.rebuild;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PresetLibraryTest {
    private static final String PRESET = "{\"name\":\"Default\",\"views\":[{\"left\":0,\"top\":0,\"width\":80,\"height\":40,\"text\":\"{speed} km/h\"}]}";

    @Test public void savesRenamesDuplicatesAndDeletes() throws Exception {
        String library = PresetLibrary.put("", "Daily", PRESET);
        assertEquals("[\"Daily\"]", PresetLibrary.names(library));
        library = PresetLibrary.rename(library, "Daily", "Night");
        library = PresetLibrary.duplicate(library, "Night", "Night copy");
        assertEquals("[\"Night\",\"Night copy\"]", PresetLibrary.names(library));
        library = PresetLibrary.delete(library, "Night");
        assertEquals("[\"Night copy\"]", PresetLibrary.names(library));
    }

    @Test public void exportReturnsValidPreset() throws Exception {
        String library = PresetLibrary.put("", "Daily", PRESET);
        JSONObject exported = new JSONObject(PresetLibrary.exportPreset(library, "Daily"));
        assertEquals("Daily", exported.getString("name"));
        assertEquals(1, exported.getJSONArray("views").length());
    }

    @Test(expected=org.json.JSONException.class)
    public void rejectsInvalidImportedPreset() throws Exception {
        PresetLibrary.importPreset("{\"name\":\"bad\",\"views\":[]}");
    }

    @Test(expected=org.json.JSONException.class)
    public void rejectsDuplicateName() throws Exception {
        String library = PresetLibrary.put("", "Daily", PRESET);
        PresetLibrary.duplicate(library, "Daily", "Daily");
    }
}