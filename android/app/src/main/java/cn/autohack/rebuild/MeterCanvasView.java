package cn.autohack.rebuild;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Simple normalized-coordinate canvas for the offline meter editor. */
public final class MeterCanvasView extends View {
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int backgroundColor = 0xFF263746;
    private int textColor = 0xFFFFFFFF;
    private int textSizeSp = 32;
    private int left = 5, top = 5, width = 90, height = 90;
    private String text = "0 km/h";

    public MeterCanvasView(Context context) {
        super(context);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
    }

    public void setPreview(String text, int textSizeSp, int textColor, int backgroundColor,
                           int left, int top, int width, int height) {
        this.text = text == null ? "" : text;
        this.textSizeSp = Math.max(10, Math.min(textSizeSp, 96));
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.width = Math.max(1, Math.min(width, 100));
        this.height = Math.max(1, Math.min(height, 100));
        this.left = Math.max(0, Math.min(left, 100 - this.width));
        this.top = Math.max(0, Math.min(top, 100 - this.height));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(backgroundColor);

        float scaleX = getWidth() / 100f;
        float scaleY = getHeight() / 100f;
        RectF bounds = new RectF(left * scaleX, top * scaleY,
                (left + width) * scaleX, (top + height) * scaleY);
        panelPaint.setColor(0x332A2A2A);
        canvas.drawRoundRect(bounds, 12f, 12f, panelPaint);

        textPaint.setColor(textColor);
        textPaint.setTextSize(textSizeSp * getResources().getDisplayMetrics().scaledDensity);
        String[] lines = text.split("\n");
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent;
        float firstBaseline = bounds.centerY()
                - (lines.length - 1) * lineHeight / 2f
                - (metrics.ascent + metrics.descent) / 2f;
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], bounds.centerX(),
                    firstBaseline + i * lineHeight, textPaint);
        }
    }
}
