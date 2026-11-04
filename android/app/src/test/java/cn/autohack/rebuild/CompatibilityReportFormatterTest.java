package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CompatibilityReportFormatterTest {
    private String format(boolean detailed) {
        return CompatibilityReportFormatter.format("Honda", "Test Head Unit", "device-x",
                "product-y", "board-z", "hardware-q", "vendor/build:fingerprint",
                "Android 10", 29, new String[]{"armeabi-v7a"}, 1280, 720, 240, 1.5f,
                detailed);
    }

    @Test public void defaultReportOmitsDetailedFirmwareIdentifiers() {
        String report = format(false);
        assertTrue(report.contains("Manufacturer: Honda"));
        assertTrue(report.contains("Display: 1280 × 720"));
        assertTrue(report.contains("Detailed firmware identifiers: excluded"));
        assertFalse(report.contains("vendor/build:fingerprint"));
        assertFalse(report.contains("board-z"));
        assertFalse(report.contains("hardware-q"));
    }

    @Test public void detailedReportIncludesIdentifiersOnlyWhenRequested() {
        String report = format(true);
        assertTrue(report.contains("Device: device-x"));
        assertTrue(report.contains("Product: product-y"));
        assertTrue(report.contains("Board: board-z"));
        assertTrue(report.contains("Hardware: hardware-q"));
        assertTrue(report.contains("Build fingerprint: vendor/build:fingerprint"));
    }

    @Test public void missingValuesAreRenderedAsUnknown() {
        String report = CompatibilityReportFormatter.format(null, "", null, null,
                null, null, null, null, 0, null, 0, 0, 0, 0f, true);
        assertTrue(report.contains("Manufacturer: unknown"));
        assertTrue(report.contains("Model: unknown"));
        assertTrue(report.contains("Build fingerprint: unknown"));
        assertTrue(report.contains("ABIs: []"));
    }
}
