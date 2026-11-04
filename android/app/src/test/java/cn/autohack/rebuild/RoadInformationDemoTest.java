package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RoadInformationDemoTest {
    @Test public void displayContainsCurrentRoadAndNextInstruction() {
        String text = RoadInformationDemo.displayText();
        assertTrue(text.contains("Current road: Market Road"));
        assertTrue(text.contains("Turn right onto Main Street"));
        assertTrue(text.contains("300 metres"));
    }

    @Test public void announcementIsACompleteSingleInstruction() {
        assertEquals("Turn right onto Main Street in 300 metres.",
                RoadInformationDemo.announcementText());
    }
}
