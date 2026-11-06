package com.shilapi.xcertplay.compat.usb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable passive descriptor group. On API 17 this is synthesized from
 * UsbDevice's public interface list; configuration ID/name/power metadata are
 * unavailable and the synthetic values must not be interpreted as real ones.
 */
public final class UsbConfigurationSnapshot {
    private final int id;
    private final String name;
    private final int attributes;
    private final int maxPower;
    private final List<InterfaceSnapshot> interfaces;

    public UsbConfigurationSnapshot(int id, String name, int attributes, int maxPower,
            List<InterfaceSnapshot> interfaces) {
        requireByte("configuration id", id);
        requireByte("configuration attributes", attributes);
        requireByte("configuration maxPower", maxPower);
        if (interfaces == null) throw new NullPointerException("interfaces");
        this.id = id;
        this.name = name;
        this.attributes = attributes;
        this.maxPower = maxPower;
        this.interfaces = Collections.unmodifiableList(
                new ArrayList<InterfaceSnapshot>(interfaces));
        for (InterfaceSnapshot descriptor : this.interfaces) {
            if (descriptor == null) throw new IllegalArgumentException("null interface");
        }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getAttributes() { return attributes; }
    public int getMaxPower() { return maxPower; }
    public List<InterfaceSnapshot> getInterfaces() { return interfaces; }
    public int getInterfaceCount() { return interfaces.size(); }

    public static final class InterfaceSnapshot {
        private final int id;
        private final int interfaceClass;
        private final int interfaceSubclass;
        private final int interfaceProtocol;
        private final int alternateSetting;
        private final List<EndpointSnapshot> endpoints;

        public InterfaceSnapshot(int id, int interfaceClass, int interfaceSubclass,
                int interfaceProtocol, int alternateSetting, List<EndpointSnapshot> endpoints) {
            requireByte("interface id", id);
            requireByte("interface class", interfaceClass);
            requireByte("interface subclass", interfaceSubclass);
            requireByte("interface protocol", interfaceProtocol);
            if (alternateSetting != -1) requireByte("alternate setting", alternateSetting);
            if (endpoints == null) throw new NullPointerException("endpoints");
            this.id = id;
            this.interfaceClass = interfaceClass;
            this.interfaceSubclass = interfaceSubclass;
            this.interfaceProtocol = interfaceProtocol;
            this.alternateSetting = alternateSetting;
            this.endpoints = Collections.unmodifiableList(new ArrayList<EndpointSnapshot>(endpoints));
            for (EndpointSnapshot endpoint : this.endpoints) {
                if (endpoint == null) throw new IllegalArgumentException("null endpoint");
            }
        }

        public int getId() { return id; }
        public int getInterfaceClass() { return interfaceClass; }
        public int getInterfaceSubclass() { return interfaceSubclass; }
        public int getInterfaceProtocol() { return interfaceProtocol; }
        public int getAlternateSetting() { return alternateSetting; }
        public boolean isAlternateSettingKnown() { return alternateSetting >= 0; }
        public List<EndpointSnapshot> getEndpoints() { return endpoints; }
        public int getEndpointCount() { return endpoints.size(); }
    }

    public static final class EndpointSnapshot {
        private final int address;
        private final int attributes;
        private final int direction;
        private final int type;
        private final int maxPacketSize;
        private final int interval;

        public EndpointSnapshot(int address, int attributes, int direction, int type,
                int maxPacketSize, int interval) {
            requireByte("endpoint address", address);
            requireByte("endpoint attributes", attributes);
            if (direction != 0 && direction != 128) {
                throw new IllegalArgumentException("endpoint direction must be OUT(0) or IN(128)");
            }
            if (type < 0 || type > 3) throw new IllegalArgumentException("endpoint type out of range");
            if (maxPacketSize < 0 || maxPacketSize > 65535) {
                throw new IllegalArgumentException("endpoint maxPacketSize out of range");
            }
            requireByte("endpoint interval", interval);
            this.address = address;
            this.attributes = attributes;
            this.direction = direction;
            this.type = type;
            this.maxPacketSize = maxPacketSize;
            this.interval = interval;
        }

        public int getAddress() { return address; }
        public int getAttributes() { return attributes; }
        public int getDirection() { return direction; }
        public int getType() { return type; }
        public int getMaxPacketSize() { return maxPacketSize; }
        public int getInterval() { return interval; }
    }

    private static void requireByte(String name, int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException(name + " out of range");
    }
}
