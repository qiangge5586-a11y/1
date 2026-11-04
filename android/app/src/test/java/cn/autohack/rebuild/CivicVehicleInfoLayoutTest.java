package cn.autohack.rebuild;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class CivicVehicleInfoLayoutTest {
    private List<MeterPreset.ViewSpec> sample() {
        List<MeterPreset.ViewSpec> result = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            result.add(new MeterPreset.ViewSpec(i, 10, 10, 18,
                    "field-" + i, "center", 11, "#ffffffff"));
        }
        return result;
    }

    @Test public void originalDefaultShowsFirstThreeVehicleFields() {
        List<MeterPreset.ViewSpec> visible = CivicVehicleInfoLayout.apply(
                sample(), CivicVehicleInfoLayout.defaultSelection());
        assertEquals(9, visible.size());
        assertEquals("field-0", visible.get(0).template);
        assertEquals("field-5", visible.get(5).template);
        assertEquals("field-6", visible.get(6).template);
        assertEquals("field-7", visible.get(7).template);
        assertEquals("field-8", visible.get(8).template);
    }

    @Test public void allSixFieldsAreEvenlyReflowedWithinCanvas() {
        List<MeterPreset.ViewSpec> visible = CivicVehicleInfoLayout.apply(
                sample(), new boolean[]{true, true, true, true, true, true});
        assertEquals(12, visible.size());
        int previousRight = 0;
        for (int i = 6; i < visible.size(); i++) {
            MeterPreset.ViewSpec view = visible.get(i);
            assertTrue(view.left >= previousRight);
            assertTrue(view.left + view.width <= 100);
            previousRight = view.left + view.width;
        }
    }

    @Test public void deselectedFieldsAreRemovedAndRemainingFieldsReflow() {
        List<MeterPreset.ViewSpec> visible = CivicVehicleInfoLayout.apply(
                sample(), new boolean[]{false, true, false, true, false, true});
        assertEquals(9, visible.size());
        assertEquals("field-7", visible.get(6).template);
        assertEquals("field-9", visible.get(7).template);
        assertEquals("field-11", visible.get(8).template);
        assertEquals(1, visible.get(6).left);
        assertTrue(visible.get(8).left + visible.get(8).width <= 100);
    }

    @Test public void emptySelectionHidesAllSixVehicleFields() {
        List<MeterPreset.ViewSpec> visible = CivicVehicleInfoLayout.apply(
                sample(), new boolean[]{false, false, false, false, false, false});
        assertEquals(6, visible.size());
    }

    @Test public void shortInputIsReturnedWithoutFailure() {
        List<MeterPreset.ViewSpec> input = sample().subList(0, 2);
        assertEquals(input, CivicVehicleInfoLayout.apply(input, null));
    }
}
