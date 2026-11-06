package com.shilapi.xcertplay.compat.usb;

import com.shilapi.xcertplay.compat.ncm.NcmInterfaceSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class UsbNcmReadOnlyMatcherTest {
    private UsbNcmReadOnlyMatcherTest() {
    }

    public static void main(String[] args) {
        mapsPassiveSnapshotToCdcNcmSelection();
        findsFirstMatchingConfiguration();
        rejectsNonNcmAndAmbiguousSnapshots();
        descriptorCollectionsAreImmutable();
        unknownAlternateSettingRemainsExplicit();
        preservesEndpointAddressAndPacketSize();
        System.out.println("UsbNcmReadOnlyMatcherTest: PASS (6 cases)");
    }

    private static void mapsPassiveSnapshotToCdcNcmSelection() {
        UsbConfigurationSnapshot config = config(1,
                iface(2, 2, 13, 0, ep(0x81, 3, 128, 3, 16, 8)),
                iface(4, 10, 0, 0),
                iface(4, 10, 0, 1, ep(0x82, 2, 128, 2, 512, 0), ep(0x03, 2, 0, 2, 512, 0)));
        NcmInterfaceSelector.Selection selection = UsbNcmReadOnlyMatcher.find(config);
        check(selection != null, "CDC-NCM selected from passive snapshot");
        check(selection.getData().getAlternateSetting() == 1, "alternate setting 1 selected");
        check(selection.getDataClaim().getAlternateSetting() == 0, "alternate setting 0 claim sibling");
        check(selection.getBulkIn().getDirection() == 128, "bulk IN selected");
        check(selection.getBulkOut().getDirection() == 0, "bulk OUT selected");
    }

    private static void findsFirstMatchingConfiguration() {
        UsbConfigurationSnapshot nonNcm = config(1, iface(0, 3, 1, 0));
        UsbConfigurationSnapshot ncm = config(2,
                iface(2, 2, 13, 0),
                iface(3, 10, 0, 1, ep(0x81, 2, 128, 2, 512, 0), ep(0x02, 2, 0, 2, 512, 0)));
        UsbNcmReadOnlyMatcher.Match match =
                UsbNcmReadOnlyMatcher.findFirst(Arrays.asList(nonNcm, ncm));
        check(match != null && match.getConfigurationIndex() == 1, "first NCM configuration index");
        check(match.getConfiguration().getId() == 2, "matching configuration retained");
    }

    private static void rejectsNonNcmAndAmbiguousSnapshots() {
        UsbConfigurationSnapshot noControl = config(1,
                iface(3, 10, 0, 1, ep(0x81, 2, 128, 2, 512, 0), ep(0x02, 2, 0, 2, 512, 0)));
        UsbConfigurationSnapshot duplicateIn = config(2,
                iface(2, 2, 13, 0),
                iface(3, 10, 0, 1, ep(0x81, 2, 128, 2, 512, 0),
                        ep(0x82, 2, 128, 2, 512, 0), ep(0x02, 2, 0, 2, 512, 0)));
        check(UsbNcmReadOnlyMatcher.find(noControl) == null, "missing control rejected");
        check(UsbNcmReadOnlyMatcher.find(duplicateIn) == null, "duplicate bulk IN rejected");
    }

    private static void descriptorCollectionsAreImmutable() {
        List<UsbConfigurationSnapshot.InterfaceSnapshot> interfaces =
                new ArrayList<UsbConfigurationSnapshot.InterfaceSnapshot>();
        interfaces.add(iface(0, 2, 13, 0));
        UsbConfigurationSnapshot config = new UsbConfigurationSnapshot(1, null, 128, 50, interfaces);
        interfaces.clear();
        check(config.getInterfaceCount() == 1, "defensive interface copy");
        try {
            config.getInterfaces().clear();
            throw new AssertionError("interface list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void unknownAlternateSettingRemainsExplicit() {
        UsbConfigurationSnapshot config = config(1,
                iface(2, 2, 13, -1),
                iface(4, 10, 0, -1, ep(0x82, 2, 128, 2, 512, 0),
                        ep(0x03, 2, 0, 2, 512, 0)));
        check(config.getInterfaces().get(0).isAlternateSettingKnown() == false,
                "API17 unknown alternate setting represented explicitly");
        NcmInterfaceSelector.Selection selection = UsbNcmReadOnlyMatcher.find(config);
        check(selection != null, "descriptor shape may still be identified");
        check(selection.getData().getAlternateSetting() == -1,
                "matcher must not fabricate alternate setting zero");
        check(selection.getDataClaim() == selection.getData(),
                "without known alt-zero sibling, claim descriptor remains selected data");
    }

    private static void preservesEndpointAddressAndPacketSize() {
        UsbConfigurationSnapshot config = config(1,
                iface(2, 2, 13, 0),
                iface(4, 10, 0, 1,
                        ep(0x86, 2, 128, 2, 1024, 0),
                        ep(0x07, 2, 0, 2, 1024, 0)));
        NcmInterfaceSelector.Selection selection = UsbNcmReadOnlyMatcher.find(config);
        check(selection != null, "NCM selection exists");
        check(selection.getBulkIn().getAddress() == 0x86, "bulk IN address retained");
        check(selection.getBulkIn().getMaxPacketSize() == 1024, "bulk IN packet size retained");
        check(selection.getBulkOut().getAddress() == 0x07, "bulk OUT address retained");
        check(selection.getBulkOut().getMaxPacketSize() == 1024, "bulk OUT packet size retained");
    }

    private static UsbConfigurationSnapshot config(int id,
            UsbConfigurationSnapshot.InterfaceSnapshot... interfaces) {
        return new UsbConfigurationSnapshot(id, "fixture", 128, 50, Arrays.asList(interfaces));
    }

    private static UsbConfigurationSnapshot.InterfaceSnapshot iface(int id, int clazz,
            int subclass, int alternate, UsbConfigurationSnapshot.EndpointSnapshot... endpoints) {
        return new UsbConfigurationSnapshot.InterfaceSnapshot(id, clazz, subclass, 0,
                alternate, Arrays.asList(endpoints));
    }

    private static UsbConfigurationSnapshot.EndpointSnapshot ep(int address, int attributes,
            int direction, int type, int maxPacket, int interval) {
        return new UsbConfigurationSnapshot.EndpointSnapshot(address, attributes, direction,
                type, maxPacket, interval);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
