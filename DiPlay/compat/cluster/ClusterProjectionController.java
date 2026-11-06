package com.shilapi.xcertplay.compat.cluster;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * API-17 external-display renderer prototype.
 * It renders a navigation-style demo page only; it does not mirror another app,
 * read live navigation data, or communicate with the vehicle instrument cluster.
 */
public final class ClusterProjectionController {
    private final Context context;
    private final DisplayManager displayManager;
    private NavigationPresentation presentation;

    public ClusterProjectionController(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        this.context = context;
        this.displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    }

    public int getPresentationDisplayCount() {
        if (displayManager == null) return 0;
        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        return displays == null ? 0 : displays.length;
    }

    public String showDemo() {
        if (displayManager == null) return "系统未提供 DisplayManager";
        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if (displays == null || displays.length == 0) {
            return "未发现系统公开的外接演示屏幕。当前无法向原车仪表输出；仪表是否支持独立视频输入仍需确认。";
        }
        dismiss();
        Display target = displays[0];
        try {
            presentation = new NavigationPresentation(context, target);
            presentation.show();
            return "已向系统公开的外接屏发送导航样例画面： " + target.getName()
                    + "。这是演示数据，不是实时导航，也不代表原车仪表已接通。";
        } catch (RuntimeException failure) {
            presentation = null;
            return "外接屏打开失败：" + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : " - " + failure.getMessage());
        }
    }

    public void dismiss() {
        if (presentation != null) {
            try { presentation.dismiss(); } catch (RuntimeException ignored) { }
            presentation = null;
        }
    }

    private static final class NavigationPresentation extends Presentation {
        NavigationPresentation(Context context, Display display) {
            super(context, display);
        }

        @Override
        protected void onCreate(android.os.Bundle state) {
            super.onCreate(state);
            LinearLayout page = new LinearLayout(getContext());
            page.setOrientation(LinearLayout.VERTICAL);
            page.setGravity(Gravity.CENTER);
            page.setPadding(24, 18, 24, 18);
            page.setBackgroundColor(0xff07131f);

            TextView direction = new TextView(getContext());
            direction.setText("↗");
            direction.setTextSize(76);
            direction.setTextColor(0xff62b5e5);
            direction.setGravity(Gravity.CENTER);
            page.addView(direction, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 2));

            TextView distance = new TextView(getContext());
            distance.setText("300 m");
            distance.setTextSize(42);
            distance.setTextColor(0xffffffff);
            distance.setGravity(Gravity.CENTER);
            page.addView(distance, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 2));

            TextView road = new TextView(getContext());
            road.setText("前方路口右转\n导航投屏测试画面");
            road.setTextSize(22);
            road.setTextColor(0xffd7e4ef);
            road.setGravity(Gravity.CENTER);
            page.addView(road, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 2));

            TextView note = new TextView(getContext());
            note.setText("演示数据 · 非实时导航");
            note.setTextSize(13);
            note.setTextColor(0xff9fb0bd);
            note.setGravity(Gravity.CENTER);
            page.addView(note, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            setContentView(page);
        }
    }
}
