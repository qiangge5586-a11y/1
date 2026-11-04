package cn.autohack.rebuild;

/** Validates a user-entered home destination stored locally for voice navigation. */
public final class SavedHomeDestination {
    public static final int MAX_LENGTH = 160;

    private SavedHomeDestination() {}

    public static String normalize(String value) {
        String raw = value == null ? "" : value;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isISOControl(ch) && !Character.isWhitespace(ch)) {
                throw new IllegalArgumentException("Destination contains unsupported control characters");
            }
        }
        String result = raw.trim().replaceAll("\\s+", " ");
        if (result.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Destination is too long");
        }
        return result;
    }

    public static boolean isValid(String value) {
        try {
            String normalized = normalize(value);
            return !normalized.isEmpty();
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
