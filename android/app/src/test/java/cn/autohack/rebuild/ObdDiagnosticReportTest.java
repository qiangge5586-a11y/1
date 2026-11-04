package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ObdDiagnosticReportTest {
    @Test public void includesConnectionPidAndDtcSections() {
        String report = ObdDiagnosticReport.build(
                "2026-11-04 12:00",
                "Connected. ELM protocol: ISO 15765-4 (CAN 11/500)",
                "Speed · 010D\nEngine RPM · 010C",
                "MIL: ON · stored DTC count: 3",
                "Stored trouble codes:\nP0133 · 氧传感器响应缓慢");
        assertTrue(report.contains("Connection / live values"));
        assertTrue(report.contains("ISO 15765-4"));
        assertTrue(report.contains("010D"));
        assertTrue(report.contains("P0133"));
        assertTrue(report.contains("MIL: ON"));
        assertTrue(report.contains("read-only"));
    }

    @Test public void missingSectionsUseUnavailablePlaceholder() {
        String report = ObdDiagnosticReport.build(null, null, "", null, null);
        assertTrue(report.contains("Generated:\nNot available"));
        assertTrue(report.contains("Connection / live values:\nNot available"));
        assertTrue(report.contains("ECU-supported dashboard PIDs:\nNot available"));
    }

    @Test public void removesEmbeddedControlCharactersAndBoundsEachSection() {
        String report = ObdDiagnosticReport.build("now", "connected" + ((char) 0) + " state",
                repeat("X", 3000), "MIL unavailable", "none");
        assertFalse(report.contains(String.valueOf((char) 0)));
        assertTrue(report.length() < 2400);
    }

    @Test public void reportDoesNotAddVehicleOrAdapterIdentifiers() {
        String report = ObdDiagnosticReport.build("now", "Connected", "010D", "MIL off", "P0133");
        assertFalse(report.contains("1HGCV1F3"));
        assertFalse(report.contains("AA:BB:CC:DD:EE:FF"));
        assertTrue(report.contains("does not include VIN"));
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
