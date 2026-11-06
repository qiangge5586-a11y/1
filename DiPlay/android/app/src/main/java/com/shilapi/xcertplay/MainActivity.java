package com.shilapi.xcertplay;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Minimal Chinese-language shell for the isolated Honda compatibility build. */
public final class MainActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(18));
        root.setBackgroundColor(0xff101820);

        TextView title = new TextView(this);
        title.setText("DiPlay 思域适配验证版");
        title.setTextColor(0xffffffff);
        title.setTextSize(26);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(this);
        status.setText("独立工程已启动\n\n目标设备：Honda MY16ADA\n系统基准：Android 4.2.2 / API 17\n处理器：ARMv7\n\n当前阶段：安全组件集成与兼容性验证\nROOT 热点操作：尚未启用\n设备写入：未执行");
        status.setTextColor(0xffd6e2ea);
        status.setTextSize(18);
        status.setLineSpacing(dp(5), 1.0f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(20);
        root.addView(status, statusParams);

        setContentView(root);
    }
}
