package cn.autohack.rebuild;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ObdReadOnlyCommandsTest {
    @Test public void exposesOnlyFourStandardReadOnlyPids() {
        assertEquals("010D", ObdReadOnlyCommands.commandAt(0));
        assertEquals("010C", ObdReadOnlyCommands.commandAt(1));
        assertEquals("0105", ObdReadOnlyCommands.commandAt(2));
        assertEquals("012F", ObdReadOnlyCommands.commandAt(3));
        assertNull(ObdReadOnlyCommands.commandAt(-1));
        assertNull(ObdReadOnlyCommands.commandAt(4));
        assertTrue(ObdReadOnlyCommands.isAllowed("010D"));
        assertTrue(ObdReadOnlyCommands.isAllowed(" 010C "));
        assertFalse(ObdReadOnlyCommands.isAllowed("04"));
        assertTrue(ObdReadOnlyCommands.isAllowed("0100"));
        assertTrue(ObdReadOnlyCommands.isAllowed("0120"));
        assertTrue(ObdReadOnlyCommands.isAllowed("0140"));
        assertFalse(ObdReadOnlyCommands.isAllowed("0104"));
        assertFalse(ObdReadOnlyCommands.isAllowed("04"));
        assertFalse(ObdReadOnlyCommands.isAllowed("ATZ"));
        assertFalse(ObdReadOnlyCommands.isAllowed(null));
        assertTrue(ObdReadOnlyCommands.isDiagnosticReadCommand("03"));
        assertTrue(ObdReadOnlyCommands.isDiagnosticReadCommand(" 03 "));
        assertFalse(ObdReadOnlyCommands.isDiagnosticReadCommand("04"));
        assertFalse(ObdReadOnlyCommands.isDiagnosticReadCommand("010D"));
        assertFalse(ObdReadOnlyCommands.isDiagnosticReadCommand("ATZ"));
    }

    @Test public void adapterSetupCommandsAreAlsoExplicitlyAllowlisted() {
        assertTrue(ObdReadOnlyCommands.isAdapterSetupCommand("ATZ"));
        assertTrue(ObdReadOnlyCommands.isAdapterSetupCommand("ATE0"));
        assertTrue(ObdReadOnlyCommands.isAdapterSetupCommand("ATSP0"));
        assertFalse(ObdReadOnlyCommands.isAdapterSetupCommand("ATSH7E0"));
        assertFalse(ObdReadOnlyCommands.isAdapterSetupCommand("ATPP"));
        assertFalse(ObdReadOnlyCommands.isAdapterSetupCommand("010D"));
    }
}
