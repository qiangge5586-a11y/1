package com.shilapi.xcertplay.compat.iap2;

import java.util.Arrays;

public final class Iap2LinkCommandQueueModelTest {
    private static int checks;
    public static void main(String[] args) {
        enforcesCommandCountLimit();
        enforcesAggregateByteLimit();
        pumpRemovalReleasesExactCapacity();
        preservesFifoAndDefensiveCopies();
        closeAndTerminationRejectNewCommands();
        validatesPayloadBounds();
        System.out.println("Iap2LinkCommandQueueModelTest: PASS (" + checks + " checks)");
    }
    private static void enforcesCommandCountLimit() {
        Iap2LinkCommandQueueModel q = new Iap2LinkCommandQueueModel();
        for (int i=0;i<64;i++) truth(q.enqueueIfCapacity(new byte[] {(byte)i}));
        eq(64,q.queuedCommands()); eq(64,q.queuedBytes());
        truth(!q.hasCapacity(0)); truth(!q.enqueueIfCapacity(new byte[0]));
    }
    private static void enforcesAggregateByteLimit() {
        Iap2LinkCommandQueueModel q = new Iap2LinkCommandQueueModel();
        for (int i=0;i<16;i++) truth(q.enqueueIfCapacity(new byte[65525]));
        truth(q.enqueueIfCapacity(new byte[65536]));
        eq(17,q.queuedCommands()); eq(16*65525+65536,q.queuedBytes());
        truth(!q.hasCapacity(1)); truth(!q.enqueueIfCapacity(new byte[1]));
        Iap2LinkCommandQueueModel exact = new Iap2LinkCommandQueueModel();
        truth(exact.enqueueIfCapacity(new byte[1048576]));
        eq(1048576,exact.queuedBytes()); truth(!exact.hasCapacity(0));
    }
    private static void pumpRemovalReleasesExactCapacity() {
        Iap2LinkCommandQueueModel q = new Iap2LinkCommandQueueModel();
        truth(q.enqueueIfCapacity(new byte[1048576]));
        truth(!q.hasCapacity(1));
        byte[] removed=q.takeForPump();
        eq(1048576,removed.length); eq(0,q.queuedBytes()); eq(0,q.queuedCommands());
        truth(q.hasCapacity(65525)); truth(q.enqueueIfCapacity(new byte[65525]));
    }
    private static void preservesFifoAndDefensiveCopies() {
        Iap2LinkCommandQueueModel q = new Iap2LinkCommandQueueModel();
        byte[] first={1,2}; byte[] second={3};
        truth(q.enqueueIfCapacity(first)); truth(q.enqueueIfCapacity(second));
        first[0]=99;
        byte[] a=q.takeForPump(); byte[] b=q.takeForPump();
        eq(1,a[0]&255); eq(2,a[1]&255); eq(3,b[0]&255);
        a[0]=88; eq(0,q.queuedBytes());
        truth(Arrays.equals(new byte[] {3},b));
    }
    private static void closeAndTerminationRejectNewCommands() {
        Iap2LinkCommandQueueModel closing=new Iap2LinkCommandQueueModel();
        truth(closing.enqueueIfCapacity(new byte[] {1}));
        closing.beginClose(); truth(closing.isClosing());
        truth(!closing.enqueueIfCapacity(new byte[] {2}));
        eq(1,closing.queuedCommands());
        Iap2LinkCommandQueueModel dead=new Iap2LinkCommandQueueModel();
        dead.terminate("link failed"); truth(dead.isTerminated());
        eq("link failed",dead.terminalFailure());
        truth(!dead.enqueueIfCapacity(new byte[] {1}));
    }
    private static void validatesPayloadBounds() {
        final Iap2LinkCommandQueueModel q=new Iap2LinkCommandQueueModel();
        expectNull(new Runnable(){public void run(){q.enqueueIfCapacity(null);}});
        expectIllegal(new Runnable(){public void run(){q.enqueueIfCapacity(new byte[65526]);}});
        truth(!q.hasCapacity(-1)); truth(!q.hasCapacity(65526));
    }
    private static void eq(int a,int b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void eq(String a,String b){checks++;if(a==null?b!=null:!a.equals(b))throw new AssertionError(a+" != "+b);}
    private static void truth(boolean v){checks++;if(!v)throw new AssertionError("expected true");}
    private static void expectIllegal(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("expected IllegalArgumentException");}
    private static void expectNull(Runnable r){checks++;try{r.run();}catch(NullPointerException e){return;}throw new AssertionError("expected NullPointerException");}
}
