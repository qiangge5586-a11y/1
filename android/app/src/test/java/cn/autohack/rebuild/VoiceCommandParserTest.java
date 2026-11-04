package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VoiceCommandParserTest {
    @Test public void recognizesLocalMeterCommands() {
        assertEquals(VoiceCommandParser.Type.SHOW_METER,
                VoiceCommandParser.parse("打开仪表盘").type);
        assertEquals(VoiceCommandParser.Type.SHOW_METER,
                VoiceCommandParser.parse("显示仪表").type);
    }

    @Test public void extractsAppLaunchTargetWithoutLaunchingIt() {
        VoiceCommandParser.Command command = VoiceCommandParser.parse("打开应用 高德地图");
        assertEquals(VoiceCommandParser.Type.OPEN_APP, command.type);
        assertEquals("高德地图", command.target);
    }

    @Test public void parsesDestinationHandoffAndMarksUnsupportedShortcuts() {
        VoiceCommandParser.Command destination = VoiceCommandParser.parse("导航到公司");
        assertEquals(VoiceCommandParser.Type.NAVIGATE_TO, destination.type);
        assertEquals("公司", destination.target);
        assertEquals(VoiceCommandParser.Type.NAVIGATE_TO,
                VoiceCommandParser.parse("带我去机场").type);
        assertEquals(VoiceCommandParser.Type.NAVIGATE_HOME,
                VoiceCommandParser.parse("回家").type);
        assertEquals(VoiceCommandParser.Type.MUSIC_UNAVAILABLE,
                VoiceCommandParser.parse("播放周杰伦").type);
    }

    @Test public void normalizesPoliteNavigationPhrasesAndTrailingPunctuation() {
        VoiceCommandParser.Command command =
                VoiceCommandParser.parse("请帮我导航到公司吧。");
        assertEquals(VoiceCommandParser.Type.NAVIGATE_TO, command.type);
        assertEquals("公司", command.target);
        assertEquals("机场", VoiceCommandParser.parse("带我去机场好吗").target);
        assertEquals("火车站", VoiceCommandParser.parse("前往火车站！").target);
    }

    @Test public void rejectsEmptyGenericAndOverlongNavigationTargets() {
        assertEquals(VoiceCommandParser.Type.UNKNOWN,
                VoiceCommandParser.parse("导航到").type);
        assertEquals(VoiceCommandParser.Type.UNKNOWN,
                VoiceCommandParser.parse("导航到导航").type);
        StringBuilder longTarget = new StringBuilder("导航到");
        for (int i = 0; i < 81; i++) longTarget.append('甲');
        assertEquals(VoiceCommandParser.Type.UNKNOWN,
                VoiceCommandParser.parse(longTarget.toString()).type);
    }

    @Test public void unknownAndEmptyTextDoNotCreateActions() {
        assertEquals(VoiceCommandParser.Type.UNKNOWN,
                VoiceCommandParser.parse("").type);
        assertEquals(VoiceCommandParser.Type.UNKNOWN,
                VoiceCommandParser.parse("今天天气不错").type);
    }
}
