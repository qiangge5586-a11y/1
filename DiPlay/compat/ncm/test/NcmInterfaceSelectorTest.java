package com.shilapi.xcertplay.compat.ncm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NcmInterfaceSelectorTest {
    private NcmInterfaceSelectorTest() {
    }

    public static void main(String[] args) {
        selectsCdcNcmAndAlternateZeroClaimInterface();
        prefersAlternateSettingOne();
        keepsFirstOfMultipleAlternateSettingOneCandidates();
        fallsBackWhenAlternateSettingOneIsAbsent();
        rejectsMissingControlInterface();
        rejectsAmbiguousBulkEndpoints();
        rejectsMissingBulkDirection();
        statusEndpointMustBeUnique();
        keepsFirstMatchingControlInterface();
        System.out.println("NcmInterfaceSelectorTest: PASS (9 cases)");
    }

    private static void selectsCdcNcmAndAlternateZeroClaimInterface() {
        NcmInterfaceSelector.UsbInterface control = iface(3, 2, 13, 0,
                ep(128, 3), ep(0, 2));
        NcmInterfaceSelector.UsbInterface data0 = iface(4, 10, 0, 0);
        NcmInterfaceSelector.UsbInterface data1 = iface(4, 10, 0, 1,
                ep(128, 2), ep(0, 2));
        NcmInterfaceSelector.Selection s = find(control, data0, data1);
        check(s != null, "selection exists");
        check(s.getControl() == control, "control interface selected");
        check(s.getData() == data1, "data alternate 1 selected");
        check(s.getDataClaim() == data0, "alternate 0 sibling selected for claim");
        check(s.getStatusIn() == control.getEndpoints().get(0), "unique interrupt IN");
        check(s.getBulkIn() == data1.getEndpoints().get(0), "bulk IN endpoint");
        check(s.getBulkOut() == data1.getEndpoints().get(1), "bulk OUT endpoint");
    }

    private static void prefersAlternateSettingOne() {
        NcmInterfaceSelector.UsbInterface control = iface(0, 2, 13, 0);
        NcmInterfaceSelector.UsbInterface alt2 = iface(7, 10, 0, 2,
                ep(128, 2), ep(0, 2));
        NcmInterfaceSelector.UsbInterface alt1 = iface(8, 10, 0, 1,
                ep(128, 2), ep(0, 2));
        check(find(control, alt2, alt1).getData() == alt1, "alternate 1 preferred");
    }

    private static void keepsFirstOfMultipleAlternateSettingOneCandidates() {
        NcmInterfaceSelector.UsbInterface control = iface(0, 2, 13, 0);
        NcmInterfaceSelector.UsbInterface first = iface(7, 10, 0, 1,
                ep(128, 2), ep(0, 2));
        NcmInterfaceSelector.UsbInterface second = iface(8, 10, 0, 1,
                ep(128, 2), ep(0, 2));
        check(find(control, first, second).getData() == first,
                "first alternate-setting 1 candidate retained");
    }

    private static void fallsBackWhenAlternateSettingOneIsAbsent() {
        NcmInterfaceSelector.UsbInterface control = iface(0, 2, 13, 0);
        NcmInterfaceSelector.UsbInterface alt0 = iface(7, 10, 0, 0,
                ep(128, 2), ep(0, 2));
        NcmInterfaceSelector.UsbInterface alt2 = iface(8, 10, 0, 2,
                ep(128, 2), ep(0, 2));
        check(find(control, alt0, alt2).getData() == alt0,
                "first eligible data candidate used when alt 1 absent");
    }

    private static void rejectsMissingControlInterface() {
        NcmInterfaceSelector.UsbInterface data = iface(1, 10, 0, 1,
                ep(128, 2), ep(0, 2));
        check(find(data) == null, "control interface required");
    }

    private static void rejectsAmbiguousBulkEndpoints() {
        NcmInterfaceSelector.UsbInterface control = iface(0, 2, 13, 0);
        NcmInterfaceSelector.UsbInterface data = iface(1, 10, 0, 1,
                ep(128, 2), ep(128, 2), ep(0, 2));
        check(find(control, data) == null, "duplicate bulk IN rejected");
    }

    private static void rejectsMissingBulkDirection() {
        NcmInterfaceSelector.UsbInterface control = iface(0, 2, 13, 0);
        NcmInterfaceSelector.UsbInterface data = iface(1, 10, 0, 1,
                ep(128, 2), ep(0, 3));
        check(find(control, data) == null, "bulk OUT required");
    }

    private static void statusEndpointMustBeUnique() {
        NcmInterfaceSelector.UsbInterface control = iface(0, 2, 13, 0,
                ep(128, 3), ep(128, 3));
        NcmInterfaceSelector.UsbInterface data = iface(1, 10, 0, 1,
                ep(128, 2), ep(0, 2));
        NcmInterfaceSelector.Selection s = find(control, data);
        check(s != null, "data selection remains valid");
        check(s.getStatusIn() == null, "ambiguous status endpoint omitted");
    }

    private static void keepsFirstMatchingControlInterface() {
        NcmInterfaceSelector.UsbInterface first = iface(1, 2, 13, 0);
        NcmInterfaceSelector.UsbInterface second = iface(2, 2, 13, 0);
        NcmInterfaceSelector.UsbInterface data = iface(3, 10, 0, 1,
                ep(128, 2), ep(0, 2));
        check(find(first, second, data).getControl() == first, "first matching control retained");
    }

    private static NcmInterfaceSelector.Selection find(NcmInterfaceSelector.UsbInterface... values) {
        return NcmInterfaceSelector.find(Arrays.asList(values));
    }

    private static NcmInterfaceSelector.UsbInterface iface(int id, int clazz, int subclass,
            int alternate, NcmInterfaceSelector.Endpoint... endpoints) {
        return new NcmInterfaceSelector.UsbInterface(id, clazz, subclass, alternate,
                new ArrayList<NcmInterfaceSelector.Endpoint>(Arrays.asList(endpoints)));
    }

    private static NcmInterfaceSelector.Endpoint ep(int direction, int type) {
        return new NcmInterfaceSelector.Endpoint(direction, type);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
