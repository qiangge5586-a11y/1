import com.shilapi.xcertplay.compat.usb.UsbDeviceDescriptor;

public final class UsbDeviceDescriptorTest {
    public static void main(String[] args) {
        UsbDeviceDescriptor d = new UsbDeviceDescriptor(0x18D1, 0x4EE7, 0, 0, 0, 2);
        check(d.getVendorId() == 0x18D1, "vendor ID");
        check(d.getProductId() == 0x4EE7, "product ID");
        check(d.getInterfaceCount() == 2, "interface count");
        check("18D1:4EE7:00:00:00:2".equals(d.getDescriptorKey()), "stable descriptor key");
        expectInvalid(-1, 1, 0, 0, 0, 0);
        expectInvalid(1, 65536, 0, 0, 0, 0);
        expectInvalid(1, 1, 256, 0, 0, 0);
        expectInvalid(1, 1, 0, -1, 0, 0);
        expectInvalid(1, 1, 0, 0, 256, 0);
        expectInvalid(1, 1, 0, 0, 0, 256);
        System.out.println("PASS: passive USB descriptor validation and key formatting");
    }
    private static void expectInvalid(int vid, int pid, int cls, int sub, int protocol, int interfaces) {
        try {
            new UsbDeviceDescriptor(vid, pid, cls, sub, protocol, interfaces);
            throw new AssertionError("expected invalid descriptor");
        } catch (IllegalArgumentException expected) { }
    }
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
