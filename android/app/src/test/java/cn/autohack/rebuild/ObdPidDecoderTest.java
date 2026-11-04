package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ObdPidDecoderTest {
    @Test public void decodesCommonStandardPids() {
        assertEquals(Integer.valueOf(40), ObdPidDecoder.speedKph("41 0D 28"));
        assertEquals(Integer.valueOf(1726), ObdPidDecoder.engineRpm("41 0C 1A F8"));
        assertEquals(Integer.valueOf(83), ObdPidDecoder.coolantTempC("41 05 7B"));
        assertEquals(Integer.valueOf(50), ObdPidDecoder.fuelLevelPercent("41 2F 80"));
        assertEquals(Integer.valueOf(50), ObdPidDecoder.engineLoadPercent("41 04 80"));
        assertEquals(Integer.valueOf(100), ObdPidDecoder.throttlePercent("41 11 FF"));
    }

    @Test public void decodesAdditionalTemperatureAirAndPressurePids() {
        assertEquals(Float.valueOf(50.0f), ObdPidDecoder.mafGramsPerSecond("41 10 13 88"));
        assertEquals(Integer.valueOf(40), ObdPidDecoder.intakeTempC("41 0F 50"));
        assertEquals(Integer.valueOf(60), ObdPidDecoder.oilTempC("41 5C 64"));
        assertEquals(Integer.valueOf(90), ObdPidDecoder.intakeManifoldPressureKpa("41 0B 5A"));
        assertEquals(Integer.valueOf(101), ObdPidDecoder.barometricPressureKpa("41 33 65"));
        assertEquals(Float.valueOf(0.0f), ObdPidDecoder.timingAdvanceDegrees("41 0E 80"));
        assertEquals(Float.valueOf(50.0f), ObdPidDecoder.fuelRateLitersPerHour("41 5E 03 E8"));
    }

    @Test public void handlesElmEchoCanHeadersAndPrompt() {
        assertEquals(Integer.valueOf(1726),
                ObdPidDecoder.engineRpm("010C\rSEARCHING...\r7E8 04 41 0C 1A F8\r>"));
        assertEquals(Integer.valueOf(40),
                ObdPidDecoder.speedKph("010D\r41 0D 28\r>"));
    }

    @Test public void unsupportedOrMalformedResponsesReturnNull() {
        assertNull(ObdPidDecoder.speedKph("NO DATA"));
        assertNull(ObdPidDecoder.engineRpm("UNABLE TO CONNECT"));
        assertNull(ObdPidDecoder.engineRpm("41 0C 1A"));
        assertNull(ObdPidDecoder.coolantTempC("7F 01 11"));
        assertNull(Elm327ResponseParser.parsePidBytes(null, 0x0C, 2));
        assertNull(Elm327ResponseParser.parsePidBytes("41 0C 1A F8", 0x100, 2));
    }
}
