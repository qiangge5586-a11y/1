package com.shilapi.xcertplay.compat.ncm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java model of the original DiPlay CDC-NCM interface selection policy.
 * This class only evaluates supplied descriptor fixtures; it never opens or
 * claims a USB interface and has no Android framework dependency.
 */
public final class NcmInterfaceSelector {
    public static final int CONTROL_CLASS = 2;
    public static final int CONTROL_SUBCLASS = 13;
    public static final int DATA_CLASS = 10;
    public static final int DIRECTION_OUT = 0;
    public static final int DIRECTION_IN = 128;
    public static final int TYPE_BULK = 2;
    public static final int TYPE_INTERRUPT = 3;
    public static final int DATA_ALTERNATE_SETTING = 1;

    private NcmInterfaceSelector() {
    }

    public static final class Endpoint {
        private final int direction;
        private final int type;
        private final int address;
        private final int maxPacketSize;

        public Endpoint(int direction, int type) {
            this(direction, type, -1, -1);
        }

        public Endpoint(int direction, int type, int address, int maxPacketSize) {
            if (address < -1 || address > 255) {
                throw new IllegalArgumentException("endpoint address out of range");
            }
            if (maxPacketSize < -1 || maxPacketSize > 65535) {
                throw new IllegalArgumentException("endpoint max packet size out of range");
            }
            this.direction = direction;
            this.type = type;
            this.address = address;
            this.maxPacketSize = maxPacketSize;
        }

        public int getDirection() { return direction; }
        public int getType() { return type; }
        public int getAddress() { return address; }
        public int getMaxPacketSize() { return maxPacketSize; }
    }

    public static final class UsbInterface {
        private final int id;
        private final int interfaceClass;
        private final int interfaceSubclass;
        private final int alternateSetting;
        private final List<Endpoint> endpoints;

        public UsbInterface(int id, int interfaceClass, int interfaceSubclass,
                int alternateSetting, List<Endpoint> endpoints) {
            if (endpoints == null) throw new NullPointerException("endpoints");
            this.id = id;
            this.interfaceClass = interfaceClass;
            this.interfaceSubclass = interfaceSubclass;
            this.alternateSetting = alternateSetting;
            this.endpoints = Collections.unmodifiableList(new ArrayList<Endpoint>(endpoints));
            for (Endpoint endpoint : this.endpoints) {
                if (endpoint == null) throw new IllegalArgumentException("null endpoint");
            }
        }

        public int getId() { return id; }
        public int getInterfaceClass() { return interfaceClass; }
        public int getInterfaceSubclass() { return interfaceSubclass; }
        public int getAlternateSetting() { return alternateSetting; }
        public List<Endpoint> getEndpoints() { return endpoints; }
    }

    public static final class Selection {
        private final UsbInterface control;
        private final UsbInterface data;
        private final UsbInterface dataClaim;
        private final Endpoint statusIn;
        private final Endpoint bulkIn;
        private final Endpoint bulkOut;

        private Selection(UsbInterface control, UsbInterface data, UsbInterface dataClaim,
                Endpoint statusIn, Endpoint bulkIn, Endpoint bulkOut) {
            this.control = control;
            this.data = data;
            this.dataClaim = dataClaim;
            this.statusIn = statusIn;
            this.bulkIn = bulkIn;
            this.bulkOut = bulkOut;
        }

        public UsbInterface getControl() { return control; }
        public UsbInterface getData() { return data; }
        public UsbInterface getDataClaim() { return dataClaim; }
        public Endpoint getStatusIn() { return statusIn; }
        public Endpoint getBulkIn() { return bulkIn; }
        public Endpoint getBulkOut() { return bulkOut; }
    }

    /**
     * Mirrors the readable original NcmFunctionDiscovery selection rules:
     * first CDC control interface; first eligible data interface after
     * preferring alternate setting 1; alt-0 sibling for claiming when present;
     * exactly one bulk IN and OUT; status IN only when exactly one exists.
     */
    public static Selection find(List<UsbInterface> interfaces) {
        if (interfaces == null) throw new NullPointerException("interfaces");
        UsbInterface control = null;
        for (UsbInterface candidate : interfaces) {
            if (candidate == null) throw new IllegalArgumentException("null interface");
            if (candidate.getInterfaceClass() == CONTROL_CLASS
                    && candidate.getInterfaceSubclass() == CONTROL_SUBCLASS) {
                control = candidate;
                break;
            }
        }
        if (control == null) return null;

        UsbInterface data = null;
        Endpoint bulkIn = null;
        Endpoint bulkOut = null;
        for (UsbInterface candidate : interfaces) {
            if (candidate.getInterfaceClass() != DATA_CLASS) continue;
            Endpoint[] pair = findUniqueBulkPair(candidate);
            if (pair == null) continue;
            if (data == null || (candidate.getAlternateSetting() == DATA_ALTERNATE_SETTING
                    && data.getAlternateSetting() != DATA_ALTERNATE_SETTING)) {
                data = candidate;
                bulkIn = pair[0];
                bulkOut = pair[1];
            }
        }
        if (data == null) return null;

        UsbInterface dataClaim = data;
        for (UsbInterface candidate : interfaces) {
            if (candidate.getId() == data.getId() && candidate.getAlternateSetting() == 0) {
                dataClaim = candidate;
                break;
            }
        }

        Endpoint statusIn = null;
        int statusCount = 0;
        for (Endpoint endpoint : control.getEndpoints()) {
            if (endpoint.getDirection() == DIRECTION_IN
                    && endpoint.getType() == TYPE_INTERRUPT) {
                statusIn = endpoint;
                statusCount++;
            }
        }
        if (statusCount != 1) statusIn = null;
        return new Selection(control, data, dataClaim, statusIn, bulkIn, bulkOut);
    }

    private static Endpoint[] findUniqueBulkPair(UsbInterface descriptor) {
        Endpoint in = null;
        Endpoint out = null;
        int inCount = 0;
        int outCount = 0;
        for (Endpoint endpoint : descriptor.getEndpoints()) {
            if (endpoint.getType() != TYPE_BULK) continue;
            if (endpoint.getDirection() == DIRECTION_IN) {
                in = endpoint;
                inCount++;
            } else if (endpoint.getDirection() == DIRECTION_OUT) {
                out = endpoint;
                outCount++;
            }
        }
        if (inCount != 1 || outCount != 1) return null;
        return new Endpoint[] {in, out};
    }
}
