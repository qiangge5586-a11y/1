package cn.autohack.rebuild;

/** Builds a local-only diagnostic text report without VIN, adapter address or location fields. */
public final class ObdDiagnosticReport {
    private ObdDiagnosticReport() {}

    public static String build(String generatedAt, String connectionStatus,
            String supportedPidStatus, String engineStatus, String diagnosticCodeStatus) {
        StringBuilder report = new StringBuilder("HondaHack Rebuild · OBD-II Diagnostic Report");
        append(report, "Generated", generatedAt);
        append(report, "Connection / live values", connectionStatus);
        append(report, "ECU-supported dashboard PIDs", supportedPidStatus);
        append(report, "MIL / engine status", engineStatus);
        append(report, "Diagnostic trouble codes", diagnosticCodeStatus);
        report.append("\nSafety: standard read-only requests only; no DTC clearing or ECU writes.");
        report.append("\nPrivacy: this report does not include VIN, adapter MAC address or location.");
        return report.toString();
    }

    private static void append(StringBuilder report, String label, String value) {
        report.append("\n\n").append(label).append(":\n").append(sanitize(value));
    }

    private static String sanitize(String value) {
        if (value == null || value.trim().isEmpty()) return "Not available";
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < value.length() && clean.length() < 2048; i++) {
            char ch = value.charAt(i);
            if (ch == '\n' || ch == '\t' || !Character.isISOControl(ch)) clean.append(ch);
        }
        return clean.toString().trim();
    }
}
