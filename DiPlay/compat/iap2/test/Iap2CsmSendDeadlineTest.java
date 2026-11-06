package com.shilapi.xcertplay.compat.iap2;

public final class Iap2CsmSendDeadlineTest {
    private static int checks;
    public static void main(String[] args) {
        exactAndRoundedRemainingBudget();
        expiresAtOrAfterDeadline();
        validatesTimeoutRange();
        sharedDeadlineDoesNotResetBetweenChunks();
        System.out.println("Iap2CsmSendDeadlineTest: PASS (" + checks + " checks)");
    }
    private static void exactAndRoundedRemainingBudget() {
        Iap2CsmSendDeadline d=new Iap2CsmSendDeadline(1000000000L,5000);
        eq(5000L,d.remainingMillis(1000000000L));
        eq(5000L,d.remainingMillis(1000000001L));
        eq(4999L,d.remainingMillis(1001000000L));
        eq(1L,d.remainingMillis(5999000000L));
    }
    private static void expiresAtOrAfterDeadline() {
        Iap2CsmSendDeadline d=new Iap2CsmSendDeadline(0L,25);
        eq(0L,d.remainingMillis(25000000L));
        eq(0L,d.remainingMillis(26000000L));
        Iap2CsmSendDeadline zero=new Iap2CsmSendDeadline(77L,0);
        eq(0L,zero.remainingMillis(77L));
    }
    private static void validatesTimeoutRange() {
        expectIllegal(new Runnable(){public void run(){new Iap2CsmSendDeadline(0,-1);}});
        expectIllegal(new Runnable(){public void run(){new Iap2CsmSendDeadline(0,300001);}});
        eq(300000L,new Iap2CsmSendDeadline(0,300000).remainingMillis(0));
    }
    private static void sharedDeadlineDoesNotResetBetweenChunks() {
        Iap2CsmSendDeadline d=new Iap2CsmSendDeadline(1000000L,1000);
        long first=d.remainingMillis(251000000L);
        long second=d.remainingMillis(751000000L);
        eq(750L,first); eq(250L,second); truth(second<first);
    }
    private static void eq(long a,long b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void truth(boolean b){checks++;if(!b)throw new AssertionError("expected true");}
    private static void expectIllegal(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("expected IllegalArgumentException");}
}
