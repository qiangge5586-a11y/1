package com.shilapi.xcertplay.compat.iap2;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class IapTunnelFramingTest {
    private static int checks;
    public static void main(String[] args) {
        frameFragmentationAndCoalescing();
        frameRejectsOversizeAndResets();
        packageFragmentationAndCoalescing();
        packageRejectsInvalidSizesAndResets();
        packagePreservesUnknownTypes();
        System.out.println("IapTunnelFramingTest: PASS (" + checks + " checks)");
    }

    private static void frameFragmentationAndCoalescing() {
        IapTunnelFrameDecoder d = new IapTunnelFrameDecoder(128);
        byte[] f1 = frame(new byte[]{1,2,3});
        byte[] f2 = frame(new byte[]{4,5});
        List<byte[]> out = d.feed(slice(f1,0,2));
        eq(0,out.size()); eq(2,d.pendingBytes());
        out = d.feed(concat(slice(f1,2,f1.length), f2));
        eq(2,out.size());
        eq(19,out.get(0).length); eq(18,out.get(1).length);
        bytes(new byte[]{1,2,3},slice(out.get(0),0,3));
        bytes(new byte[]{4,5},slice(out.get(1),0,2));
        eq(0x5a,out.get(0)[18] & 255); eq(0x5a,out.get(1)[17] & 255);
        eq(0,d.pendingBytes());
    }

    private static void frameRejectsOversizeAndResets() {
        final IapTunnelFrameDecoder d = new IapTunnelFrameDecoder(10);
        expectFailure(new Runnable(){public void run(){d.feed(new byte[]{11,0});}});
        eq(0,d.pendingBytes());
    }

    private static void packageFragmentationAndCoalescing() {
        IapTunnelPackageDecoder d = new IapTunnelPackageDecoder(256);
        byte[] p1 = packet(IapTunnelPackageDecoder.MESSAGE_TYPE_COMM,new byte[]{9,8,7});
        byte[] p2 = packet(0x12345678,new byte[]{6});
        List<IapTunnelPackageDecoder.Package> out=d.feed(slice(p1,0,12));
        eq(0,out.size()); eq(12,d.pendingBytes());
        out=d.feed(concat(slice(p1,12,p1.length),p2));
        eq(2,out.size()); check(out.get(0).isComm());
        bytes(new byte[]{9,8,7},out.get(0).body);
        eq(0x12345678,out.get(1).messageType);
        bytes(new byte[]{6},out.get(1).body);
        eq(0,d.pendingBytes());
    }

    private static void packageRejectsInvalidSizesAndResets() {
        final IapTunnelPackageDecoder d=new IapTunnelPackageDecoder(64);
        expectFailure(new Runnable(){public void run(){d.feed(new byte[]{0,0,0,31,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0});}});
        eq(0,d.pendingBytes());
        expectFailure(new Runnable(){public void run(){d.feed(new byte[]{0,0,0,65});}});
        eq(0,d.pendingBytes());
    }

    private static void packagePreservesUnknownTypes() {
        IapTunnelPackageDecoder d=new IapTunnelPackageDecoder(64);
        List<IapTunnelPackageDecoder.Package> out=d.feed(packet(7,new byte[0]));
        eq(1,out.size()); eq(7,out.get(0).messageType); check(!out.get(0).isComm());
    }

    private static byte[] frame(byte[] cipher) {
        byte[] out=new byte[2+cipher.length+16];
        out[0]=(byte)cipher.length; out[1]=(byte)(cipher.length>>>8);
        System.arraycopy(cipher,0,out,2,cipher.length);
        for(int i=2+cipher.length;i<out.length;i++)out[i]=(byte)0x5a;
        return out;
    }
    private static byte[] packet(int type,byte[] body) {
        int n=32+body.length; byte[] out=new byte[n];
        out[0]=(byte)(n>>>24);out[1]=(byte)(n>>>16);out[2]=(byte)(n>>>8);out[3]=(byte)n;
        out[16]=(byte)(type>>>24);out[17]=(byte)(type>>>16);out[18]=(byte)(type>>>8);out[19]=(byte)type;
        System.arraycopy(body,0,out,32,body.length); return out;
    }
    private static byte[] concat(byte[] a,byte[] b){byte[] o=new byte[a.length+b.length];System.arraycopy(a,0,o,0,a.length);System.arraycopy(b,0,o,a.length,b.length);return o;}
    private static byte[] slice(byte[] a,int s,int e){byte[] o=new byte[e-s];System.arraycopy(a,s,o,0,o.length);return o;}
    private static void expectFailure(Runnable r){checks++;try{r.run();throw new AssertionError("expected failure");}catch(IllegalArgumentException expected){}}
    private static void check(boolean b){checks++;if(!b)throw new AssertionError("false");}
    private static void eq(int a,int b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void bytes(byte[] a,byte[] b){checks++;if(a.length!=b.length)throw new AssertionError("length");for(int i=0;i<a.length;i++)if(a[i]!=b[i])throw new AssertionError("byte "+i);}
}
