package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CompassHeadingTest {
    @Test public void mapsCardinalAndIntercardinalDirections() {
        assertEquals("N", CompassHeading.fromAzimuth(0));
        assertEquals("NE", CompassHeading.fromAzimuth(45));
        assertEquals("E", CompassHeading.fromAzimuth(90));
        assertEquals("SE", CompassHeading.fromAzimuth(135));
        assertEquals("S", CompassHeading.fromAzimuth(180));
        assertEquals("SW", CompassHeading.fromAzimuth(225));
        assertEquals("W", CompassHeading.fromAzimuth(270));
        assertEquals("NW", CompassHeading.fromAzimuth(315));
    }

    @Test public void normalizesNegativeAndOverflowAngles() {
        assertEquals("NW", CompassHeading.fromAzimuth(-45));
        assertEquals("N", CompassHeading.fromAzimuth(360));
        assertEquals("NE", CompassHeading.fromAzimuth(405));
        assertEquals("N", CompassHeading.fromAzimuth(720));
    }

    @Test public void roundsAtCompassSectorBoundaries() {
        assertEquals("N", CompassHeading.fromAzimuth(22.49f));
        assertEquals("NE", CompassHeading.fromAzimuth(22.5f));
        assertEquals("N", CompassHeading.fromAzimuth(337.49f));
        assertEquals("N", CompassHeading.fromAzimuth(337.5f));
    }

    @Test public void rejectsNonFiniteAngles() {
        assertEquals("--", CompassHeading.fromAzimuth(Float.NaN));
        assertEquals("--", CompassHeading.fromAzimuth(Float.POSITIVE_INFINITY));
    }
}
