package com.shilapi.xcertplay.compat.iap2;

import java.util.List;
import java.util.Random;

public final class Iap2CsmFrameDecoderTest {
    private static int checks;
    public static void main(String[] args) {
        fragmentedAndCoalescedFrames();
        everySingleSplitBoundaryAndBytewiseFeed();
        resynchronizesAfterGarbageAndBadShortLength();
        reportsResynchronizationDiagnostics();
        retainsIncompleteFrame();
        randomizedFragmentationAndCoalescing();
        acceptsLargeCoalescedInputLikeOriginalOffer();
        decodesMaximumLengthFrameInBoundedChunks();
        System.out.println("Iap2CsmFrameDecoderTest: PASS (" + checks + " checks)");
    }
    private static void fragmentedAndCoalescedFrames() {
        Iap2CsmFrameDecoder d=new Iap2CsmFrameDecoder();
        byte[] a=frame(0x4300,new byte[]{1,2});
        byte[] b=frame(0x5703,new byte[]{3});
        List<Iap2CsmFrameDecoder.Frame> out=d.feed(slice(a,0,3));
        eq(0,out.size()); eq(3,d.pendingBytes());
        out=d.feed(concat(slice(a,3,a.length),b));
        eq(2,out.size()); eq(0x4300,out.get(0).messageId); bytes(new byte[]{1,2},out.get(0).payload);
        eq(0x5703,out.get(1).messageId); bytes(new byte[]{3},out.get(1).payload);
        eq(0,d.pendingBytes());
    }
    private static void everySingleSplitBoundaryAndBytewiseFeed() {
        byte[] payload = new byte[]{0, 1, 0x40, 0x40, (byte)0xff, 7, 9};
        byte[] encoded = frame(0x4e0d, payload);
        for (int split = 1; split < encoded.length; split++) {
            Iap2CsmFrameDecoder d = new Iap2CsmFrameDecoder();
            List<Iap2CsmFrameDecoder.Frame> first = d.feed(slice(encoded, 0, split));
            eq(0, first.size());
            List<Iap2CsmFrameDecoder.Frame> second = d.feed(slice(encoded, split, encoded.length));
            eq(1, second.size());
            eq(0x4e0d, second.get(0).messageId);
            bytes(payload, second.get(0).payload);
            eq(0, d.pendingBytes());
        }
        Iap2CsmFrameDecoder bytewise = new Iap2CsmFrameDecoder();
        for (int i = 0; i < encoded.length; i++) {
            List<Iap2CsmFrameDecoder.Frame> out = bytewise.feed(slice(encoded, i, i + 1));
            if (i + 1 < encoded.length) eq(0, out.size());
            else {
                eq(1, out.size());
                eq(0x4e0d, out.get(0).messageId);
                bytes(payload, out.get(0).payload);
            }
        }
        eq(0, bytewise.pendingBytes());
    }

    private static void resynchronizesAfterGarbageAndBadShortLength() {
        Iap2CsmFrameDecoder d=new Iap2CsmFrameDecoder();
        byte[] valid=frame(7,new byte[]{9});
        byte[] invalid=new byte[]{0x40,0x40,0,5,0,1,99,98};
        List<Iap2CsmFrameDecoder.Frame> out=d.feed(concat(invalid,concat(new byte[]{11,12},valid)));
        eq(1,out.size()); eq(7,out.get(0).messageId); bytes(new byte[]{9},out.get(0).payload);
    }
    private static void reportsResynchronizationDiagnostics() {
        Iap2CsmFrameDecoder d = new Iap2CsmFrameDecoder();
        byte[] invalid = new byte[]{0x40,0x40,0,5,0,1,99,98};
        byte[] valid = frame(0x1234, new byte[]{7});
        List<Iap2CsmFrameDecoder.Frame> out = d.feed(concat(invalid, valid));
        eq(1, out.size());
        eq(0x1234, out.get(0).messageId);
        eq(8L, d.getDiscardedBytes());
        eq(1L, d.getInvalidLengthCandidates());
        d.reset();
        eq(0L, d.getDiscardedBytes());
        eq(0L, d.getInvalidLengthCandidates());
        eq(0, d.pendingBytes());
    }

    private static void retainsIncompleteFrame() {
        Iap2CsmFrameDecoder d=new Iap2CsmFrameDecoder();
        byte[] f=frame(99,new byte[]{1,2,3,4});
        List<Iap2CsmFrameDecoder.Frame> out=d.feed(slice(f,0,7));
        eq(0,out.size()); eq(7,d.pendingBytes());
        out=d.feed(slice(f,7,f.length));
        eq(1,out.size()); eq(99,out.get(0).messageId); bytes(new byte[]{1,2,3,4},out.get(0).payload);
    }

    private static void randomizedFragmentationAndCoalescing() {
        Random random = new Random(40402026L);
        Iap2CsmFrameDecoder decoder = new Iap2CsmFrameDecoder();
        for (int n = 0; n < 100; n++) {
            int payloadLength = random.nextInt(256);
            byte[] payload = new byte[payloadLength];
            random.nextBytes(payload);
            int id = random.nextInt(65536);
            byte[] encoded = frame(id, payload);
            int offset = 0;
            while (offset < encoded.length) {
                int chunk = Math.min(encoded.length - offset, random.nextInt(13) + 1);
                List<Iap2CsmFrameDecoder.Frame> out = decoder.feed(slice(encoded, offset, offset + chunk));
                offset += chunk;
                if (offset < encoded.length) eq(0, out.size());
                else {
                    eq(1, out.size());
                    eq(id, out.get(0).messageId);
                    bytes(payload, out.get(0).payload);
                }
            }
            eq(0, decoder.pendingBytes());
        }
    }

    private static void acceptsLargeCoalescedInputLikeOriginalOffer() {
        Iap2CsmFrameDecoder d = new Iap2CsmFrameDecoder();
        byte[] firstPayload = new byte[40000];
        byte[] secondPayload = new byte[40000];
        for (int i = 0; i < firstPayload.length; i++) firstPayload[i] = (byte) (i * 7);
        for (int i = 0; i < secondPayload.length; i++) secondPayload[i] = (byte) (i * 13);
        byte[] first = frame(0x1234, firstPayload);
        byte[] second = frame(0x5678, secondPayload);
        byte[] coalesced = concat(first, second);
        truth(coalesced.length > Iap2CsmFrameDecoder.MAX_FRAME_BYTES);
        List<Iap2CsmFrameDecoder.Frame> out = d.feed(coalesced);
        eq(2, out.size());
        eq(0x1234, out.get(0).messageId);
        bytes(firstPayload, out.get(0).payload);
        eq(0x5678, out.get(1).messageId);
        bytes(secondPayload, out.get(1).payload);
        eq(0, d.pendingBytes());
    }

    private static void decodesMaximumLengthFrameInBoundedChunks() {
        Iap2CsmFrameDecoder d = new Iap2CsmFrameDecoder();
        byte[] payload = new byte[65535 - Iap2CsmFrameDecoder.HEADER_BYTES];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte)(i * 31);
        byte[] encoded = frame(0xffff, payload);
        eq(65535, encoded.length);
        int offset = 0;
        List<Iap2CsmFrameDecoder.Frame> out = null;
        while (offset < encoded.length) {
            int end = Math.min(encoded.length, offset + Iap2CsmFrameDecoder.MAX_INPUT_CHUNK_BYTES);
            out = d.feed(slice(encoded, offset, end));
            offset = end;
        }
        eq(1, out.size());
        eq(0xffff, out.get(0).messageId);
        bytes(payload, out.get(0).payload);
        eq(0, d.pendingBytes());
    }
    private static byte[] frame(int id,byte[] payload) {
        int n=6+payload.length; byte[] b=new byte[n];
        b[0]=0x40;b[1]=0x40;b[2]=(byte)(n>>>8);b[3]=(byte)n;
        b[4]=(byte)(id>>>8);b[5]=(byte)id;
        System.arraycopy(payload,0,b,6,payload.length);return b;
    }
    private static byte[] concat(byte[] a,byte[] b){byte[] o=new byte[a.length+b.length];System.arraycopy(a,0,o,0,a.length);System.arraycopy(b,0,o,a.length,b.length);return o;}
    private static byte[] slice(byte[] a,int s,int e){byte[] o=new byte[e-s];System.arraycopy(a,s,o,0,o.length);return o;}
    private static void eq(int a,int b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void eq(long a,long b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void bytes(byte[] a,byte[] b){checks++;if(a.length!=b.length)throw new AssertionError("length");for(int i=0;i<a.length;i++)if(a[i]!=b[i])throw new AssertionError("byte "+i);}
}
