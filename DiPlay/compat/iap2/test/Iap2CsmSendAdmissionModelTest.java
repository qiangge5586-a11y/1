package com.shilapi.xcertplay.compat.iap2;

public final class Iap2CsmSendAdmissionModelTest {
    private static int checks;
    public static void main(String[] args) {
        admitsAllChunksWithOneSharedDeadline();
        waitsThenResumesWhenPumpReleasesCapacity();
        partialAdmissionTimesOutFailClosed();
        zeroTimeoutStillAdmitsWhenCapacityExists();
        zeroTimeoutFailsWhenCapacityIsUnavailable();
        closeAndTerminalFailureWakeAdmission();
        emptyFrameCompletesWithoutQueueing();
        System.out.println("Iap2CsmSendAdmissionModelTest: PASS (" + checks + " checks)");
    }

    private static void admitsAllChunksWithOneSharedDeadline() {
        Iap2LinkCommandQueueModel q=new Iap2LinkCommandQueueModel();
        Iap2CsmSendAdmissionModel m=new Iap2CsmSendAdmissionModel(new byte[10],4,0,1000);
        eq(3,m.totalChunkCount());
        eq(Iap2CsmSendAdmissionModel.Status.QUEUED,m.step(0,q).status);
        eq(1,m.queuedChunkCount());
        eq(Iap2CsmSendAdmissionModel.Status.QUEUED,m.step(250000000L,q).status);
        eq(2,m.queuedChunkCount());
        Iap2CsmSendAdmissionModel.Result done=m.step(750000000L,q);
        eq(Iap2CsmSendAdmissionModel.Status.COMPLETE,done.status);
        eq(3,q.queuedCommands());
        eq(10,q.queuedBytes());
        eq(250L,done.remainingMillis);
    }

    private static void waitsThenResumesWhenPumpReleasesCapacity() {
        Iap2LinkCommandQueueModel q=new Iap2LinkCommandQueueModel();
        for(int i=0;i<16;i++) truth(q.enqueueIfCapacity(new byte[65525]));
        truth(q.enqueueIfCapacity(new byte[176]));
        Iap2CsmSendAdmissionModel m=new Iap2CsmSendAdmissionModel(new byte[100],50,0,5000);
        eq(Iap2CsmSendAdmissionModel.Status.WAITING,m.step(1000000000L,q).status);
        eq(4000L,m.step(1000000000L,q).remainingMillis);
        eq(65525,new byte[0].length+q.takeForPump().length);
        eq(Iap2CsmSendAdmissionModel.Status.QUEUED,m.step(2000000000L,q).status);
        eq(1,m.queuedChunkCount());
    }

    private static void partialAdmissionTimesOutFailClosed() {
        Iap2LinkCommandQueueModel q=new Iap2LinkCommandQueueModel();
        for(int i=0;i<16;i++) truth(q.enqueueIfCapacity(new byte[65525]));
        truth(q.enqueueIfCapacity(new byte[176]));
        Iap2CsmSendAdmissionModel m=new Iap2CsmSendAdmissionModel(new byte[100],50,0,1000);
        eq(Iap2CsmSendAdmissionModel.Status.WAITING,m.step(0,q).status);
        eq(Iap2CsmSendAdmissionModel.Status.TIMEOUT_FAIL_CLOSED,m.step(1000000000L,q).status);
        truth(m.isFailedClosed());
        eq(Iap2CsmSendAdmissionModel.Status.TIMEOUT_FAIL_CLOSED,m.step(1100000000L,q).status);
        eq(0,m.queuedChunkCount());
    }

    private static void zeroTimeoutStillAdmitsWhenCapacityExists() {
        Iap2LinkCommandQueueModel q=new Iap2LinkCommandQueueModel();
        Iap2CsmSendAdmissionModel m=new Iap2CsmSendAdmissionModel(new byte[8],8,5,0);
        eq(Iap2CsmSendAdmissionModel.Status.COMPLETE,m.step(5,q).status);
        eq(1,q.queuedCommands());
    }

    private static void zeroTimeoutFailsWhenCapacityIsUnavailable() {
        Iap2LinkCommandQueueModel q=new Iap2LinkCommandQueueModel();
        for(int i=0;i<64;i++) truth(q.enqueueIfCapacity(new byte[0]));
        Iap2CsmSendAdmissionModel m=new Iap2CsmSendAdmissionModel(new byte[1],1,0,0);
        eq(Iap2CsmSendAdmissionModel.Status.TIMEOUT_FAIL_CLOSED,m.step(0,q).status);
    }

    private static void closeAndTerminalFailureWakeAdmission() {
        Iap2LinkCommandQueueModel closing=new Iap2LinkCommandQueueModel();
        for(int i=0;i<64;i++) truth(closing.enqueueIfCapacity(new byte[0]));
        Iap2CsmSendAdmissionModel a=new Iap2CsmSendAdmissionModel(new byte[1],1,0,5000);
        closing.beginClose();
        eq(Iap2CsmSendAdmissionModel.Status.CLOSED,a.step(1,closing).status);

        Iap2LinkCommandQueueModel dead=new Iap2LinkCommandQueueModel();
        for(int i=0;i<64;i++) truth(dead.enqueueIfCapacity(new byte[0]));
        dead.terminate("link pump failed");
        Iap2CsmSendAdmissionModel b=new Iap2CsmSendAdmissionModel(new byte[1],1,0,5000);
        Iap2CsmSendAdmissionModel.Result r=b.step(1,dead);
        eq(Iap2CsmSendAdmissionModel.Status.TERMINAL_FAILURE,r.status);
        eq("link pump failed",r.terminalFailure);
    }

    private static void emptyFrameCompletesWithoutQueueing() {
        Iap2LinkCommandQueueModel q=new Iap2LinkCommandQueueModel();
        Iap2CsmSendAdmissionModel m=new Iap2CsmSendAdmissionModel(new byte[0],1,0,0);
        eq(Iap2CsmSendAdmissionModel.Status.COMPLETE,m.step(0,q).status);
        eq(0,q.queuedCommands());
    }

    private static void eq(int a,int b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void eq(long a,long b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void eq(Object a,Object b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void truth(boolean b){checks++;if(!b)throw new AssertionError("expected true");}
}
