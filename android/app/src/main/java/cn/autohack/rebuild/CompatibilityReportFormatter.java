package cn.autohack.rebuild;

import java.util.Arrays;

/** Formats a compatibility report with optional firmware-identifying details. */
public final class CompatibilityReportFormatter {
    private CompatibilityReportFormatter() {}

    public static String format(String manufacturer, String model, String device,
            String product, String board, String hardware, String fingerprint,
            String release, int sdk, String[] abis, int widthPixels, int heightPixels,
            int densityDpi, float density, boolean includeDetailedIdentifiers) {
        StringBuilder report = new StringBuilder();
        report.append("Manufacturer: ").append(safe(manufacturer))
                .append("\nModel: ").append(safe(model))
                .append("\nAndroid: ").append(safe(release)).append(" (API ").append(sdk).append(")")
                .append("\nABIs: ").append(Arrays.toString(abis == null ? new String[0] : abis))
                .append("\nDisplay: ").append(widthPixels).append(" × ").append(heightPixels)
                .append("\nDensity: ").append(densityDpi).append(" dpi")
                .append("\nScreen density scale: ").append(density)
                .append("\nRoot access: not tested (no commands executed)");
        if (includeDetailedIdentifiers) {
            report.append("\n\nDetailed device identifiers (review before sharing)")
                    .append("\nDevice: ").append(safe(device))
                    .append("\nProduct: ").append(safe(product))
                    .append("\nBoard: ").append(safe(board))
                    .append("\nHardware: ").append(safe(hardware))
                    .append("\nBuild fingerprint: ").append(safe(fingerprint));
        } else {
            report.append("\n\nDetailed firmware identifiers: excluded by privacy setting");
        }
        return report.toString();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }
}
