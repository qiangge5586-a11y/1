package cn.autohack.rebuild;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small offline dictionary of common generic OBD-II DTC descriptions; not repair instructions. */
public final class ObdDtcDescriptions {
    private static final Map<String, String> DEFINITIONS;

    static {
        Map<String, String> values = new HashMap<>();
        values.put("P0101", "空气流量计电路范围/性能异常");
        values.put("P0113", "进气温度传感器电路高输入");
        values.put("P0128", "冷却液温度低于节温器调节温度");
        values.put("P0133", "氧传感器响应缓慢（第1排/传感器1）");
        values.put("P0171", "混合气过稀（第1排）");
        values.put("P0300", "检测到随机/多个气缸失火");
        values.put("P0420", "催化转换器效率低于阈值（第1排）");
        values.put("P0442", "蒸发排放系统检测到小泄漏");
        values.put("P0455", "蒸发排放系统检测到大泄漏");
        values.put("P0500", "车速传感器信号异常");
        values.put("P0700", "变速箱控制系统请求故障指示灯");
        DEFINITIONS = Collections.unmodifiableMap(values);
    }

    private ObdDtcDescriptions() {}

    public static String describe(String code) {
        if (code == null) return "无效故障码";
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[PCBU][0-3][0-9A-F]{3}")) return "无效故障码";
        String description = DEFINITIONS.get(normalized);
        return description == null
                ? "暂无内置通用说明，请以本车型维修资料或专业诊断为准"
                : description;
    }
}
