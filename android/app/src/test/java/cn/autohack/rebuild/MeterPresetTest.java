package cn.autohack.rebuild;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class MeterPresetTest {
 @Test public void parsesAndRendersSupportedTelemetry() throws Exception {
  String json="{\"name\":\"Test\",\"views\":[{\"left\":0,\"top\":0,\"width\":100,\"height\":100,\"text\":\"{speed} km/h · {rpm} rpm\"}]}";
  MeterPreset p=MeterPreset.fromJson(json);assertEquals("Test",p.name);
  assertEquals("0 km/h · 900 rpm",p.primaryView.renderMock(0,900,50,80));assertEquals(1,p.views.size());
 }
 @Test public void parsesAllWidgetsAndAlignment() throws Exception {
  String json="{\"views\":[{\"left\":0,\"top\":0,\"width\":40,\"height\":30,\"text\":\"{speed}\"},{\"left\":50,\"top\":50,\"width\":40,\"height\":30,\"text\":\"{rpm}\",\"alignment\":\"right\",\"textSize\":20}]}";
  MeterPreset p=MeterPreset.fromJson(json);assertEquals(2,p.views.size());
  assertEquals("900",p.views.get(1).renderMock(0,900,50,80));assertEquals("right",p.views.get(1).alignment);
 }
 @Test(expected=JSONException.class) public void rejectsEmptyViews() throws Exception {MeterPreset.fromJson("{\"views\":[]}");}
 @Test(expected=JSONException.class) public void rejectsOutOfBoundsWidget() throws Exception {
  MeterPreset.fromJson("{\"views\":[{\"left\":90,\"top\":0,\"width\":20,\"height\":20,\"text\":\"{speed}\"}]}");
 }
 @Test(expected=JSONException.class) public void rejectsUnsupportedPlaceholder() throws Exception {
  MeterPreset.fromJson("{\"views\":[{\"left\":0,\"top\":0,\"width\":100,\"height\":100,\"text\":\"{gear}\"}]}");
 }
 @Test(expected=JSONException.class) public void rejectsMixedPlaceholder() throws Exception {
  MeterPreset.fromJson("{\"views\":[{\"left\":0,\"top\":0,\"width\":100,\"height\":100,\"text\":\"{speed} {gear}\"}]}");
 }
 @Test(expected=JSONException.class) public void rejectsInvalidAlignment() throws Exception {
  MeterPreset.fromJson("{\"views\":[{\"left\":0,\"top\":0,\"width\":100,\"height\":100,\"text\":\"{speed}\",\"alignment\":\"justify\"}]}");
 }
}