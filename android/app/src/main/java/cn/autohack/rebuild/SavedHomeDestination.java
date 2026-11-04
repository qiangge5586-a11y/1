package cn.autohack.rebuild;

/** Validates a user-entered home destination stored locally for voice navigation. */
public final class SavedHomeDestination {
    public static final int MAX_LENGTH = 160;

    private SavedHomeDestination() {}

    public static String normalize(String value) {
        String raw = value == null ? "" : value;
        StringBuilder normalized = new StringBuilder();
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            boolean whitespace = Character.isWhitespace(ch);
            boolean isoControl = ch <= 0x1F || (ch >= 0x7F && ch <= 0x9F);
            if (isoControl && !whitespace) {
                throw new IllegalArgumentException(
                        "Destination contains unsupported control characters");
            }
            if (whitespace) {
                if (normalized.length() > 0) pendingSpace = true;
                continue;
            }
            if (pendingSpace) normalized.append(' ');
            normalized.append(ch);
            pendingSpace = false;
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Destination is too long");
        }
        return normalized.toString();
    }

    public static boolean isValid(String value) {
        try {
            return !normalize(value).isEmpty();
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
