package cn.autohack.rebuild;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative parser for a small, local-only voice command subset. */
public final class VoiceCommandParser {
    public enum Type {
        SHOW_METER,
        OPEN_APP,
        NAVIGATE_TO,
        NAVIGATE_HOME,
        NAVIGATION_UNAVAILABLE,
        MUSIC_UNAVAILABLE,
        UNKNOWN
    }

    public static final class Command {
        public final Type type;
        public final String target;

        Command(Type type, String target) {
            this.type = type;
            this.target = target;
        }
    }

    private VoiceCommandParser() {}

    public static Command parse(String recognizedText) {
        if (recognizedText == null || recognizedText.trim().isEmpty()) {
            return new Command(Type.UNKNOWN, "");
        }
        String text = recognizedText.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        if (containsAny(text, "打开仪表盘", "显示仪表盘", "打开仪表", "显示仪表",
                "showmeter", "openmeter")) {
            return new Command(Type.SHOW_METER, "");
        }
        String navigationText = text.replaceAll("^[，,。.!！?？、]+|[，,。.!！?？、]+$", "");
        Matcher destination = Pattern.compile("^(?:请帮我|帮我|请)?(?:导航带我去|导航去|导航到|带我去|带我到|我要去|前往|去)(.+)$")
                .matcher(navigationText);
        if (destination.matches()) {
            String target = cleanDestination(destination.group(1));
            if (isValidDestination(target)) {
                return new Command(Type.NAVIGATE_TO, target);
            }
        }
        if (text.contains("回家")) {
            return new Command(Type.NAVIGATE_HOME, "");
        }
        if (containsAny(text, "打开导航", "开始导航")) {
            return new Command(Type.NAVIGATION_UNAVAILABLE, "");
        }
        if (containsAny(text, "播放", "来一首", "音乐", "听歌", "playmusic")) {
            return new Command(Type.MUSIC_UNAVAILABLE, "");
        }
        Matcher matcher = Pattern.compile("^(?:打开应用|打开|运行|启动)(.+)$").matcher(text);
        if (matcher.matches()) {
            String target = matcher.group(1).trim();
            if (!target.isEmpty()) return new Command(Type.OPEN_APP, target);
        }
        return new Command(Type.UNKNOWN, "");
    }

    private static String cleanDestination(String value) {
        String target = value == null ? "" : value;
        target = target.replaceAll("[，,。.!！?？、]+$", "");
        String[] politeEndings = {"可以吗", "好吗", "吧", "呀", "啊", "呢"};
        for (String ending : politeEndings) {
            if (target.endsWith(ending) && target.length() > ending.length()) {
                target = target.substring(0, target.length() - ending.length());
                break;
            }
        }
        return target;
    }

    private static boolean isValidDestination(String target) {
        if (target == null) return false;
        String value = target.trim();
        if (value.length() < 2 || value.length() > 80) return false;
        return !containsAny(value, "打开导航", "开始导航", "导航", "随便", "不知道");
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) return true;
        }
        return false;
    }
}
