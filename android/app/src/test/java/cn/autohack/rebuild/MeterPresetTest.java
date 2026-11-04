package cn.autohack.rebuild;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MeterPresetTest {
    @Test public void parsesAndRendersSupportedTelemetry() throws Exception {
        String json = "{\"name\":\"Test\",\"views\":[{\"left\":0,\"top\":0,\"width\":100,\"height\":100,\"text\":\"{speed} km/h · {rpm} rpm\"}]}";
        MeterPreset preset = MeterPreset.fromJson(json);
        assertEquals("Test", preset.name);
        assertEquals("0 km/h · 900 rpm",
                preset.primaryView.renderMock(0, 900, 50, 80));
    }

    @Test(expected = JSONException.class)
    public void rejectsEmptyViews() throws Exception {
        MeterPreset.fromJson("{\"name\":\"Empty\",\"views\":[]}");
    }

    @Test(expected = JSONException.class)
    public void rejectsBoundsOutsideCanvas() throws Exception {
        MeterPreset.fromJson("{\"views\":[{\"left\":90,\"top\":0,\"width\":20,\"height\":20,\"text\":\"{speed}\"}]}");
    }

    @Test(expected = JSONException.class)
    public void rejectsUnsupportedTelemetryPlaceholder() throws Exception {
        MeterPreset.fromJson("{\"views\":[{\"left\":0,\"top\":0,\"width\":100,\"height\":100,\"text\":\"{gear}\"}]}");
    }

    @Test(expected = JSONException.class)
    public void rejectsMixedSupportedAndUnsupportedTelemetry() throws Exception {
        MeterPreset.fromJson("{\"views\":[{\"left\":0,\"top\":0,\"width\":100,\"height\":100,\"text\":\"{speed} {gear}\"}]}");
    }
}
