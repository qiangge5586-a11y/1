package cn.autohack.rebuild;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated offline meter layout. Coordinates use a normalized 0..100 canvas. */
public final class MeterPreset {
 public final String name;
 public final List<ViewSpec> views;
 public final ViewSpec primaryView;
 private MeterPreset(String name,List<ViewSpec> views){this.name=name;this.views=Collections.unmodifiableList(new ArrayList<>(views));this.primaryView=views.get(0);}
 public static MeterPreset fromJson(String json)throws JSONException{
  JSONObject root=new JSONObject(json); String name=root.optString("name","Custom");
  JSONArray source=root.optJSONArray("views");
  if(source==null||source.length()==0)throw new JSONException("Preset must contain at least one view");
  if(source.length()>32)throw new JSONException("Preset cannot contain more than 32 views");
  List<ViewSpec> parsed=new ArrayList<>();
  for(int i=0;i<source.length();i++){
   JSONObject item=source.getJSONObject(i);
   int left=bounded(item,"left",0,100),top=bounded(item,"top",0,100);
   int width=bounded(item,"width",1,100),height=bounded(item,"height",1,100);
   if(left+width>100||top+height>100)throw new JSONException("View "+i+" bounds exceed canvas");
   String template=item.optString("text","{speed}");
   String unsupported=template.replace("{speed}","").replace("{rpm}","").replace("{fuel}","").replace("{coolant}","");
   if(!template.matches(".*\\{(speed|rpm|fuel|coolant)\\}.*")||unsupported.contains("{")||unsupported.contains("}"))throw new JSONException("Unsupported telemetry token in view "+i);
   String alignment=item.optString("alignment","center").toLowerCase();
   if(!alignment.equals("left")&&!alignment.equals("center")&&!alignment.equals("right"))throw new JSONException("Invalid alignment in view "+i);
   String color=item.optString("textColor","#ffffffff");
   if(!color.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))throw new JSONException("Invalid textColor in view "+i);
   parsed.add(new ViewSpec(left,top,width,height,template,alignment,item.optInt("textSize",32),color));
  }
  return new MeterPreset(name,parsed);
 }
 private static int bounded(JSONObject o,String k,int min,int max)throws JSONException{int v=o.getInt(k);if(v<min||v>max)throw new JSONException(k+" outside allowed range");return v;}
 public static final class ViewSpec {
  public final int left,top,width,height,textSize; public final String template,alignment,textColor;
  public ViewSpec(int l,int t,int w,int h,String text,String align,int size,String color){
   left=Math.max(0,Math.min(l,99));top=Math.max(0,Math.min(t,99));width=Math.max(1,Math.min(w,100-left));height=Math.max(1,Math.min(h,100-top));
   template=text==null?"{speed}":text;alignment=align==null?"center":align;textSize=Math.max(10,Math.min(size,96));textColor=color==null?"#ffffffff":color;
  }
  public String renderMock(int speed,int rpm,int fuel,int coolant){return template.replace("{speed}",String.valueOf(speed)).replace("{rpm}",String.valueOf(rpm)).replace("{fuel}",String.valueOf(fuel)).replace("{coolant}",String.valueOf(coolant));}
 }
}