package com.shilapi.xcertplay;

import android.app.Activity;
import android.widget.Button;
import com.shilapi.xcertplay.compat.cluster.ClusterProjectionController;
import com.shilapi.xcertplay.compat.usb.UsbHostReadOnlyScanner;
import com.shilapi.xcertplay.compat.usb.UsbConfigurationSnapshot;
import com.shilapi.xcertplay.compat.usb.UsbNcmReadOnlyMatcher;
import com.shilapi.xcertplay.compat.ncm.NcmInterfaceSelector;
import java.util.List;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Chinese diagnostic dashboard shell. Values are explicitly marked as
 * reference baseline or not yet measured; probes run only after an explicit user tap.
 */
public final class MainActivity extends Activity {
    private ClusterProjectionController clusterProjectionController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService diagnosticExecutor;
    private volatile boolean activityDestroyed;
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
        activityDestroyed = false;
        diagnosticExecutor = Executors.newSingleThreadExecutor();

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
                + "仅手动只读诊断会调用固定白名单 su 命令；不会修改系统 Wi-Fi 或热点配置。");

        section(root, "ROOT / 热点底层只读诊断",
                "仅在你手动点击后，使用固定白名单命令读取 ROOT 身份、Wi-Fi/网络服务、接口表及热点相关文件/程序元数据。不会读取配置文件内容，不会写文件、启停服务或改变 Wi-Fi 状态。");
        final TextView rootProbeStatus = text("ROOT 只读诊断：尚未执行", 14, MUTED);
        root.addView(rootProbeStatus);
        final Button rootProbe = new Button(this);
        rootProbe.setText("执行 ROOT / 热点只读检查");
        rootProbe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rootProbe.setEnabled(false);
                rootProbeStatus.setText("正在执行固定白名单只读命令，请等待……");
                Thread worker = new Thread(new Runnable() {
                    public void run() {
                        StringBuilder out = new StringBuilder();
                        try {
                            com.shilapi.xcertplay.network.safety.HotspotReadOnlyExecutor executor =
                                    new com.shilapi.xcertplay.network.safety.HotspotReadOnlyExecutor(
                                            new com.shilapi.xcertplay.network.safety.Api17SuReadOnlyRunner(),
                                            5000L, 32768);
                            com.shilapi.xcertplay.network.safety.HotspotReadOnlyOperation.Kind[] kinds =
                                    com.shilapi.xcertplay.network.safety.HotspotReadOnlyOperation.Kind.values();
                            for (int i = 0; i < kinds.length; i++) {
                                out.append("\n[").append(kinds[i].name()).append("]\n");
                                try {
                                    com.shilapi.xcertplay.network.safety.HotspotReadOnlyExecutor.Result result =
                                            executor.execute(kinds[i]);
                                    String value = result.output.trim();
                                    if (value.length() > 3500) {
                                        value = value.substring(0, 3500) + "\n...[输出已截断]";
                                    }
                                    out.append(value.length() == 0 ? "(无输出)" : value);
                                } catch (Exception failure) {
                                    out.append("不可用：").append(failure.getMessage());
                                }
                                out.append("\n");
                            }
                        } catch (Throwable failure) {
                            out.append("ROOT 只读诊断失败：")
                                    .append(failure.getClass().getSimpleName()).append(": ")
                                    .append(failure.getMessage());
                        }
                        final String report = out.toString();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                rootProbeStatus.setText(report);
                                rootProbe.setEnabled(true);
                            }
                        });
                    }
                }, "honda-hotspot-read-only-probe");
                worker.setDaemon(true);
                worker.start();
            }
        });
        root.addView(rootProbe, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        section(root, "适配进度",
                "已完成：静态分析、只读解析器、备份记录模型、模拟测试\n"
                + "进行中：API 17 运行时兼容性验证\n"
                + "待完成：USB / 蓝牙 / CarPlay 功能逐项移植与实机测试");

        section(root, "USB 设备枚举（只读）",
                "可手动读取 USB Host 当前公开的接口与端点描述信息。不会打开设备、申请通信权限或传输数据。API 17 不公开 alternate setting 字段，相关值会标记为未知。");
        final TextView usbStatus = text("USB 状态：尚未扫描", 14, MUTED);
        root.addView(usbStatus);
        final Button usbScan = new Button(this);
        usbScan.setText("扫描 USB 设备（只读）");
        usbScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usbScan.setEnabled(false);
                usbStatus.setText("正在后台枚举 USB 元数据……");
                diagnosticExecutor.execute(new Runnable() {
                    @Override public void run() {
                        String reportValue;
                        try {
                            UsbHostReadOnlyScanner.ScanResult scan =
                                    new UsbHostReadOnlyScanner().scan(getApplicationContext());
                            StringBuilder display = new StringBuilder(scan.getStatus());
                            List<com.shilapi.xcertplay.compat.usb.UsbDeviceDescriptor> devices = scan.getDevices();
                            for (int i = 0; i < devices.size(); i++) {
                                com.shilapi.xcertplay.compat.usb.UsbDeviceDescriptor d = devices.get(i);
                                display.append("\n设备 ").append(i + 1)
                                        .append(": VID=").append(String.format("%04X", d.getVendorId()))
                                        .append(" PID=").append(String.format("%04X", d.getProductId()))
                                        .append(" 类别=").append(d.getDeviceClass())
                                        .append(" 接口数=").append(d.getInterfaceCount())
                                        .append(" 接口集合数=").append(d.getInterfaceGroupCount());
                                for (int c = 0; c < d.getConfigurations().size(); c++) {
                                    UsbConfigurationSnapshot configuration = d.getConfigurations().get(c);
                                    display.append("\n  接口列表（配置元数据未知） 接口=").append(configuration.getInterfaceCount());
                                    NcmInterfaceSelector.Selection ncm = UsbNcmReadOnlyMatcher.find(configuration);
                                    if (ncm != null) {
                                        display.append("\n  CDC-NCM接口形态候选：控制接口#")
                                                .append(ncm.getControl().getId())
                                                .append(" 数据接口#").append(ncm.getData().getId())
                                                .append(" alt=").append(ncm.getData().getAlternateSetting() < 0
                                                        ? "未知" : String.valueOf(ncm.getData().getAlternateSetting()))
                                                .append(" claim-alt=").append(ncm.getDataClaim().getAlternateSetting() < 0
                                                        ? "未知" : String.valueOf(ncm.getDataClaim().getAlternateSetting()))
                                                .append(ncm.getData().getAlternateSetting() < 0
                                                        ? "（旧版API未公开alt信息，结果不完整）" : "");
                                    }
                                    for (UsbConfigurationSnapshot.InterfaceSnapshot iface : configuration.getInterfaces()) {
                                        display.append("\n    IF#").append(iface.getId())
                                                .append(" class=").append(iface.getInterfaceClass())
                                                .append("/").append(iface.getInterfaceSubclass())
                                                .append(" alt=").append(iface.getAlternateSetting() < 0 ? "未知" : String.valueOf(iface.getAlternateSetting()))
                                                .append(" EP=").append(iface.getEndpointCount());
                                    }
                                }
                            }
                            reportValue = display.toString();
                        } catch (Throwable failure) {
                            reportValue = "USB 扫描失败：" + failure.getClass().getSimpleName()
                                    + (failure.getMessage() == null ? "" : " - " + failure.getMessage());
                        }
                        final String report = reportValue;
                        mainHandler.post(new Runnable() {
                            @Override public void run() {
                                if (activityDestroyed) return;
                                usbStatus.setText(report);
                                usbScan.setEnabled(true);
                            }
                        });
                    }
                });
            }
        });
        root.addView(usbScan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        section(root, "蓝牙状态（只读）",
                "只读取蓝牙适配器是否存在、开关状态和设备名称；不会开启蓝牙、扫描、配对或连接。");
        final TextView bluetoothStatus = text("蓝牙状态：尚未检测", 14, MUTED);
        root.addView(bluetoothStatus);
        final Button bluetoothCheck = new Button(this);
        bluetoothCheck.setText("检测蓝牙状态（只读）");
        bluetoothCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothCheck.setEnabled(false);
                bluetoothStatus.setText("正在后台读取蓝牙适配器状态……");
                diagnosticExecutor.execute(new Runnable() {
                    @Override public void run() {
                        String detailValue;
                        try {
                            com.shilapi.xcertplay.compat.bluetooth.BluetoothReadOnlyProbe.Result r =
                                    new com.shilapi.xcertplay.compat.bluetooth.BluetoothReadOnlyProbe()
                                            .inspect(getApplicationContext());
                            String value = r.getStatus() + "\n适配器状态：" + r.getStateLabel();
                            if (r.getName() != null) value += "\n设备名称：" + r.getName();
                            value += "\n适配器：" + (r.isAdapterPresent() ? "存在" : "未检测到");
                            detailValue = value;
                        } catch (Throwable failure) {
                            detailValue = "蓝牙检测失败：" + failure.getClass().getSimpleName()
                                    + (failure.getMessage() == null ? "" : " - " + failure.getMessage());
                        }
                        final String detail = detailValue;
                        mainHandler.post(new Runnable() {
                            @Override public void run() {
                                if (activityDestroyed) return;
                                bluetoothStatus.setText(detail);
                                bluetoothCheck.setEnabled(true);
                            }
                        });
                    }
                });
            }
        });
        root.addView(bluetoothCheck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        section(root, "仪表盘投屏（外接屏验证）",
                "使用 Android 4.2.2 / API 17 的 Presentation 接口，向系统识别到的独立外接显示屏输出导航样例。不会镜像其他应用，不会发送 CAN 指令，也不会修改车辆配置。");
        final TextView clusterStatus = text("外接显示屏：尚未检测", 14, MUTED);
        root.addView(clusterStatus);
        clusterProjectionController = new ClusterProjectionController(this);
        Button clusterScan = new Button(this);
        clusterScan.setText("检测可投屏显示屏");
        clusterScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int count = clusterProjectionController.getPresentationDisplayCount();
                clusterStatus.setText("系统公开的外接演示屏数量：" + count
                        + (count == 0 ? "\n未发现可用外接屏；不能据此判断原车仪表有无视频输入。" : ""));
            }
        });
        root.addView(clusterScan, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button clusterStart = new Button(this);
        clusterStart.setText("输出导航样例到外接屏");
        clusterStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clusterStatus.setText(clusterProjectionController.showDemo());
            }
        });
        root.addView(clusterStart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button clusterStop = new Button(this);
        clusterStop.setText("停止外接屏显示");
        clusterStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clusterProjectionController.dismiss();
                clusterStatus.setText("外接屏显示已关闭。");
            }
        });
        root.addView(clusterStop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView footer = text("安全提示：本验证版不是完整 DiPlay 复刻版，请勿作为车机正式升级包使用。",
                13, MUTED);
        footer.setPadding(dp(2), dp(4), dp(2), dp(12));
        root.addView(footer);

        setContentView(scroll);
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        if (diagnosticExecutor != null) diagnosticExecutor.shutdownNow();
        if (clusterProjectionController != null) clusterProjectionController.dismiss();
        super.onDestroy();
    }
}
