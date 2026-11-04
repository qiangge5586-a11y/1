package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ChineseTextTest {
    @Test public void translatesMainAppAndSafetyLabels() {
        assertEquals("本田黑科技", ChineseText.translate("HondaHack Rebuild"));
        assertEquals("启动应用选择 · 仅手动启动",
                ChineseText.translate("Startup app selection · manual launch only"));
        assertTrue(ChineseText.translate("Safety baseline: no root commands, Xposed hooks, signature spoofing, remote revocation, silent installs, or system-partition writes.")
                .contains("不执行 Root 命令"));
    }

    @Test public void translatesDynamicObdAndVoiceStatus() {
        assertTrue(ChineseText.translate("Check-engine light (MIL): ON").contains("发动机故障灯（MIL）：亮"));
        assertTrue(ChineseText.translate("Stored DTC count (PID 01): 3").contains("已存储故障码数量"));
        assertTrue(ChineseText.translate("Recognized: navigate home").startsWith("已识别："));
    }

    @Test public void translatesPresetActionsWithoutChangingUserText() {
        String[] labels = ChineseText.translateArray(new String[]{"Load", "Rename", "Duplicate", "Delete"});
        assertArrayEquals(new String[]{"加载", "重命名", "复制", "删除"}, labels);
        assertEquals("我的仪表预设", ChineseText.translate("我的仪表预设"));
    }
}
