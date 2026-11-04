package cn.autohack.rebuild;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.List;

/** Draws every meter widget on one normalized preview canvas. */
public final class MeterCanvasView extends View {
 private final Paint panel=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
 private int background=0xFF263746; private List<Widget> widgets;
 public MeterCanvasView(Context context){super(context);}
 public void setWidgets(List<Widget> value,int color){widgets=value;background=color;invalidate();}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);canvas.drawColor(background);if(widgets==null)return;
  float sx=getWidth()/100f,sy=getHeight()/100f;
  for(Widget widget:widgets){MeterPreset.ViewSpec s=widget.spec;
   RectF r=new RectF(s.left*sx,s.top*sy,(s.left+s.width)*sx,(s.top+s.height)*sy);
   panel.setColor(0x332A2A2A);canvas.drawRoundRect(r,10f,10f,panel);
   try{text.setColor(android.graphics.Color.parseColor(s.textColor));}catch(IllegalArgumentException e){text.setColor(0xffffffff);}
   text.setTextSize(s.textSize*getResources().getDisplayMetrics().scaledDensity);
   text.setTextAlign("left".equals(s.alignment)?Paint.Align.LEFT:("right".equals(s.alignment)?Paint.Align.RIGHT:Paint.Align.CENTER));
   String[] lines=widget.value.split("\\n");Paint.FontMetrics fm=text.getFontMetrics();float lh=fm.descent-fm.ascent;
   float baseline=r.centerY()-(lines.length-1)*lh/2f-(fm.ascent+fm.descent)/2f;
   float x="left".equals(s.alignment)?r.left+6f:("right".equals(s.alignment)?r.right-6f:r.centerX());
   for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],x,baseline+i*lh,text);
  }
 }
 public static final class Widget {public final MeterPreset.ViewSpec spec;public final String value;public Widget(MeterPreset.ViewSpec s,String v){spec=s;value=v==null?"":v;}}
}