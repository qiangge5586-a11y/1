package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class GpsCourseFormatterTest {
    @Test public void requiresBearingAndMeaningfulSpeed() {
        assertTrue(GpsCourseFormatter.shouldUseCourse(true, true, 5.0f));
        assertTrue(GpsCourseFormatter.shouldUseCourse(true, true, 30.0f));
        assertFalse(GpsCourseFormatter.shouldUseCourse(false, true, 30.0f));
        assertFalse(GpsCourseFormatter.shouldUseCourse(true, false, 30.0f));
        assertFalse(GpsCourseFormatter.shouldUseCourse(true, true, 4.9f));
    }

    @Test public void rejectsInvalidSpeedValues() {
        assertFalse(GpsCourseFormatter.shouldUseCourse(true, true, Float.NaN));
        assertFalse(GpsCourseFormatter.shouldUseCourse(true, true, Float.POSITIVE_INFINITY));
        assertFalse(GpsCourseFormatter.shouldUseCourse(true, true, -1.0f));
    }

    @Test public void formatsSpeedWithoutInventingUnavailableValues() {
        assertEquals("36 km/h", GpsCourseFormatter.formatSpeed(true, 36.2f));
        assertEquals("-- km/h", GpsCourseFormatter.formatSpeed(false, 50f));
        assertEquals("-- km/h", GpsCourseFormatter.formatSpeed(true, Float.NaN));
        assertEquals("-- km/h", GpsCourseFormatter.formatSpeed(true, -1f));
    }
}
