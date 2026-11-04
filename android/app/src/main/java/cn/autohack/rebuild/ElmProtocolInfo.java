package cn.autohack.rebuild;

/** Sanitizes the ELM327's read-only ATDP/ATDPN protocol identification responses. */
public final class ElmProtocolInfo {
    private ElmProtocolInfo() {}

    public static String describe(String protocolDescription, String protocolNumber) {
        String description = clean(protocolDescription);
        String number = clean(protocolNumber);
        if (description.isEmpty() && number.isEmpty()) return "protocol not reported";
        if (description.isEmpty()) return "protocol " + number;
        if (number.isEmpty()) return description;
        return description + " (" + number + ")";
    }

    private static String clean(String value) {
        if (value == null) return "";
        String normalized = value.replace('>', ' ').replace('\r', ' ').replace('\n', ' ')
                .trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || "?".equals(normalized)) return "";
        if (normalized.length() > 80) normalized = normalized.substring(0, 80);
        return normalized;
    }
}
