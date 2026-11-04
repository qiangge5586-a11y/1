package cn.autohack.rebuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Decodes standard SAE J1979 stored, pending and permanent DTC responses. */
public final class ObdDtcDecoder {
    private static final String[] CATEGORIES = {"P", "C", "B", "U"};
    private static final int MAX_RESPONSE_HEX_CHARS = 256;

    private ObdDtcDecoder() {}

    public static List<String> decodeStoredCodes(String response) {
        return decode(response, "43");
    }

    public static List<String> decodePendingCodes(String response) {
        return decode(response, "47");
    }

    public static List<String> decodePermanentCodes(String response) {
        return decode(response, "4A");
    }

    private static List<String> decode(String response, String positiveMarker) {
        if (response == null) return Collections.emptyList();
        String compact = response.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
        int marker = compact.indexOf(positiveMarker);
        if (marker < 0) return Collections.emptyList();

        String payload = compact.substring(marker + positiveMarker.length());
        if (payload.isEmpty() || payload.length() > MAX_RESPONSE_HEX_CHARS
                || payload.length() % 4 != 0) {
            return Collections.emptyList();
        }

        Set<String> codes = new LinkedHashSet<>();
        try {
            for (int offset = 0; offset < payload.length(); offset += 4) {
                int first = Integer.parseInt(payload.substring(offset, offset + 2), 16);
                int second = Integer.parseInt(payload.substring(offset + 2, offset + 4), 16);
                if (first == 0 && second == 0) continue;
                int category = (first >>> 6) & 0x03;
                int firstDigit = (first >>> 4) & 0x03;
                int secondDigit = first & 0x0F;
                String code = CATEGORIES[category] + firstDigit
                        + Integer.toHexString(secondDigit).toUpperCase(Locale.ROOT)
                        + String.format(Locale.ROOT, "%02X", second);
                codes.add(code);
            }
        } catch (NumberFormatException invalid) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(codes));
    }
}
