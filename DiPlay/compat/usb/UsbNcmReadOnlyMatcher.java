package com.shilapi.xcertplay.compat.usb;

import com.shilapi.xcertplay.compat.ncm.NcmInterfaceSelector;
import java.util.ArrayList;
import java.util.List;

/** Applies the offline CDC-NCM selector to passive USB descriptor snapshots only. */
public final class UsbNcmReadOnlyMatcher {
    private UsbNcmReadOnlyMatcher() {
    }

    public static NcmInterfaceSelector.Selection find(UsbConfigurationSnapshot configuration) {
        if (configuration == null) throw new NullPointerException("configuration");
        List<NcmInterfaceSelector.UsbInterface> interfaces =
                new ArrayList<NcmInterfaceSelector.UsbInterface>();
        for (UsbConfigurationSnapshot.InterfaceSnapshot descriptor : configuration.getInterfaces()) {
            List<NcmInterfaceSelector.Endpoint> endpoints =
                    new ArrayList<NcmInterfaceSelector.Endpoint>();
            for (UsbConfigurationSnapshot.EndpointSnapshot endpoint : descriptor.getEndpoints()) {
                endpoints.add(new NcmInterfaceSelector.Endpoint(
                        endpoint.getDirection(), endpoint.getType(),
                        endpoint.getAddress(), endpoint.getMaxPacketSize()));
            }
            interfaces.add(new NcmInterfaceSelector.UsbInterface(
                    descriptor.getId(), descriptor.getInterfaceClass(),
                    descriptor.getInterfaceSubclass(), descriptor.getAlternateSetting(), endpoints));
        }
        return NcmInterfaceSelector.find(interfaces);
    }

    public static Match findFirst(List<UsbConfigurationSnapshot> configurations) {
        if (configurations == null) throw new NullPointerException("configurations");
        for (int i = 0; i < configurations.size(); i++) {
            UsbConfigurationSnapshot configuration = configurations.get(i);
            if (configuration == null) throw new IllegalArgumentException("null configuration");
            NcmInterfaceSelector.Selection selection = find(configuration);
            if (selection != null) return new Match(i, configuration, selection);
        }
        return null;
    }

    public static final class Match {
        private final int configurationIndex;
        private final UsbConfigurationSnapshot configuration;
        private final NcmInterfaceSelector.Selection selection;

        private Match(int configurationIndex, UsbConfigurationSnapshot configuration,
                NcmInterfaceSelector.Selection selection) {
            this.configurationIndex = configurationIndex;
            this.configuration = configuration;
            this.selection = selection;
        }

        public int getConfigurationIndex() { return configurationIndex; }
        public UsbConfigurationSnapshot getConfiguration() { return configuration; }
        public NcmInterfaceSelector.Selection getSelection() { return selection; }
    }
}
