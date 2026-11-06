package com.shilapi.xcertplay.compat.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

/**
 * Passive Bluetooth status inspection for legacy Android releases.
 * This class never enables Bluetooth, starts discovery, pairs, or connects.
 */
public final class BluetoothReadOnlyProbe {
    public static final class Result {
        private final boolean adapterPresent;
        private final boolean enabled;
        private final int state;
        private final String name;
        private final String status;

        private Result(boolean present, boolean on, int currentState,
                String adapterName, String message) {
            adapterPresent = present;
            enabled = on;
            state = currentState;
            name = adapterName;
            status = message;
        }

        public boolean isAdapterPresent() { return adapterPresent; }
        public boolean isEnabled() { return enabled; }
        public int getState() { return state; }
        public String getName() { return name; }
        public String getStatus() { return status; }

        public String getStateLabel() {
            switch (state) {
                case BluetoothAdapter.STATE_OFF: return "已关闭";
                case BluetoothAdapter.STATE_TURNING_ON: return "正在开启";
                case BluetoothAdapter.STATE_ON: return "已开启";
                case BluetoothAdapter.STATE_TURNING_OFF: return "正在关闭";
                default: return "未知状态（" + state + "）";
            }
        }
    }

    public Result inspect(Context context) {
        if (context == null) {
            return new Result(false, false, -1, null, "上下文不可用");
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                return new Result(false, false, -1, null, "设备没有蓝牙适配器");
            }
            int state = adapter.getState();
            boolean enabled = adapter.isEnabled();
            String name = null;
            if (enabled) {
                try {
                    name = adapter.getName();
                } catch (SecurityException ignored) {
                    // The adapter state is still valid if the name is restricted.
                }
            }
            return new Result(true, enabled, state, name,
                    enabled ? "蓝牙已开启（只读状态）" : "蓝牙未开启（未尝试开启）");
        } catch (SecurityException denied) {
            return new Result(true, false, -1, null, "系统拒绝读取蓝牙状态");
        } catch (RuntimeException unavailable) {
            return new Result(false, false, -1, null, "蓝牙服务暂不可用");
        }
    }
}
