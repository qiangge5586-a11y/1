package com.shilapi.xcertplay;

import android.app.Activity;
import android.widget.Button;
import com.shilapi.xcertplay.compat.usb.UsbHostReadOnlyScanner;
import java.util.List;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Chinese diagnostic dashboard shell. Values are explicitly marked as
 * reference baseline or not yet measured; this screen performs no probes.
 */
public final class MainActivity extends Activity {
    private static final int BG = 0xff101820;
    private static final int PANEL = 0xff1b2a36;
    private static final int TEXT = 0xffe6edf3;
    private static final int MUTED = 0xff9fb0bd;
    private static final int ACCENT = 0xff62b5e5;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setLineSpacing(dp(3), 1.0f);
        return v;
    }

    private void section(LinearLayout parent, String heading, String body) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.setBackgroundColor(PANEL);
        TextView h = text(heading, 19, ACCENT);
        panel.addView(h, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = text(body, 15, TEXT);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(8);
        panel.addView(b, bp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.bottomMargin = dp(12);
        parent.addView(panel, pp);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(18));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("DiPlay 思域适配验证版", 25, TEXT);
        root.addView(title);
        TextView subtitle = text("设备诊断中心 · 中文版", 14, MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(5);
        sp.bottomMargin = dp(16);
        root.addView(subtitle, sp);

        section(root, "设备适配基准（来自检测报告）",
                "设备型号：MY16ADA\n系统：Android 4.2.2 / API 17\n"
                + "平台：Honda Andromeda / vcm30t30\n处理器：ARMv7\n屏幕：800 × 480 / 横屏\n"
                + "说明：以上为报告记录，不代表本次运行时重新检测结果。");

        section(root, "运行时检测状态",
                "ROOT 权限：尚未实时检测\nWi-Fi 服务：尚未实时检测\n"
                + "网络接口：尚未实时检测\n热点配置文件：尚未读取\n"
                + "hostapd / dnsmasq：尚未检测\nSELinux 属性：尚未检测");

        section(root, "热点安全保护",
                "状态：只完成安全模型与模拟测试\n"
                + "配置备份：未执行\n系统文件恢复：未启用\n"
                + "热点启动 / 停止：未启用\n"
                + "本版本不会调用 su，不会修改系统 Wi-Fi 或热点配置。");

        section(root, "适配进度",
                "已完成：静态分析、只读解析器、备份记录模型、模拟测试\n"
                + "进行中：API 17 运行时兼容性验证\n"
                + "待完成：USB / 蓝牙 / CarPlay 功能逐项移植与实机测试");

        section(root, "USB 设备枚举（只读）",
                "可手动读取系统 USB Host 设备描述信息。不会打开设备、申请通信权限、访问端点或传输数据。");
        final TextView usbStatus = text("USB 状态：尚未扫描", 14, MUTED);
        root.addView(usbStatus);
        Button usbScan = new Button(this);
        usbScan.setText("扫描 USB 设备（只读）");
        usbScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UsbHostReadOnlyScanner.ScanResult scan =
                        new UsbHostReadOnlyScanner().scan(MainActivity.this);
                StringBuilder display = new StringBuilder(scan.getStatus());
                List<com.shilapi.xcertplay.compat.usb.UsbDeviceDescriptor> devices =
                        scan.getDevices();
                for (int i = 0; i < devices.size(); i++) {
                    com.shilapi.xcertplay.compat.usb.UsbDeviceDescriptor d = devices.get(i);
                    display.append("\n设备 ").append(i + 1)
                            .append(": VID=").append(String.format("%04X", d.getVendorId()))
                            .append(" PID=").append(String.format("%04X", d.getProductId()))
                            .append(" 类别=").append(d.getDeviceClass())
                            .append(" 接口数=").append(d.getInterfaceCount());
                }
                usbStatus.setText(display.toString());
            }
        });
        root.addView(usbScan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        section(root, "蓝牙状态（只读）",
                "只读取蓝牙适配器是否存在、开关状态和设备名称；不会开启蓝牙、扫描、配对或连接。");
        final TextView bluetoothStatus = text("蓝牙状态：尚未检测", 14, MUTED);
        root.addView(bluetoothStatus);
        Button bluetoothCheck = new Button(this);
        bluetoothCheck.setText("检测蓝牙状态（只读）");
        bluetoothCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                com.shilapi.xcertplay.compat.bluetooth.BluetoothReadOnlyProbe.Result r =
                        new com.shilapi.xcertplay.compat.bluetooth.BluetoothReadOnlyProbe()
                                .inspect(MainActivity.this);
                String detail = r.getStatus();
                if (r.getName() != null) detail += "\n设备名称：" + r.getName();
                detail += "\n适配器：" + (r.isAdapterPresent() ? "存在" : "未检测到");
                bluetoothStatus.setText(detail);
            }
        });
        root.addView(bluetoothCheck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView footer = text("安全提示：本验证版不是完整 DiPlay 复刻版，请勿作为车机正式升级包使用。",
                13, MUTED);
        footer.setPadding(dp(2), dp(4), dp(2), dp(12));
        root.addView(footer);

        setContentView(scroll);
    }
}
