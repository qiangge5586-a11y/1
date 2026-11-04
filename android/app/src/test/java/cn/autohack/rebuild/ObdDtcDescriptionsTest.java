package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ObdDtcDescriptionsTest {
    @Test public void describesCommonGenericPowertrainCodes() {
        assertTrue(ObdDtcDescriptions.describe("P0133").contains("氧传感器"));
        assertTrue(ObdDtcDescriptions.describe("P0300").contains("失火"));
        assertTrue(ObdDtcDescriptions.describe("P0420").contains("催化"));
        assertTrue(ObdDtcDescriptions.describe("p0171").contains("过稀"));
    }

    @Test public void unknownValidCodesReturnCautiousFallback() {
        assertTrue(ObdDtcDescriptions.describe("P0999").contains("暂无内置"));
        assertTrue(ObdDtcDescriptions.describe("C1234").contains("暂无内置"));
        assertTrue(ObdDtcDescriptions.describe("B1234").contains("暂无内置"));
        assertTrue(ObdDtcDescriptions.describe("U1234").contains("暂无内置"));
    }

    @Test public void malformedCodesAreRejected() {
        assertEquals("无效故障码", ObdDtcDescriptions.describe(null));
        assertEquals("无效故障码", ObdDtcDescriptions.describe(""));
        assertEquals("无效故障码", ObdDtcDescriptions.describe("P8133"));
        assertEquals("无效故障码", ObdDtcDescriptions.describe("P01ZZ"));
        assertEquals("无效故障码", ObdDtcDescriptions.describe("010D"));
    }
}
