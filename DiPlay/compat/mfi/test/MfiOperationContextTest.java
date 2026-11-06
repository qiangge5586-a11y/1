package com.shilapi.xcertplay.compat.mfi;

public final class MfiOperationContextTest {
    private static int checks;
    private static final class FakeClock implements MfiOperationContext.NanoClock {
        long now;
        public long nanoTime() { return now; }
    }

    public static void main(String[] args) throws Exception {
        oneBudgetSpansProviderPhases();
        timeoutBoundariesAndValidation();
        cancellationAndGenerationAreTerminal();
        interruptionIsObservedAndPreserved();
        monotonicWraparoundIsHandled();
        System.out.println("MfiOperationContextTest: PASS (" + checks + " checks)");
    }

    private static void oneBudgetSpansProviderPhases() {
        FakeClock c = new FakeClock(); c.now = 1000000L;
        MfiOperationContext.Cancellation cancel = new MfiOperationContext.Cancellation();
        MfiOperationContext op = new MfiOperationContext(c, c.now, 30000L, 7L, cancel);
        eq(30000L, op.remainingMillis());
        c.now += 12500000000L; // certificate provider consumed 12.5 seconds
        eq(17500L, op.remainingMillis());
        c.now += 17499000000L; // signing/provider work consumes the same budget
        eq(1L, op.remainingMillis());
        op.checkActive(7L);
        c.now += 1000000L;
        eq(0L, op.remainingMillis());
        expectTimeout(new Runnable(){public void run(){op.checkActive(7L);}});
    }

    private static void timeoutBoundariesAndValidation() {
        FakeClock c = new FakeClock();
        MfiOperationContext zero = new MfiOperationContext(c, 0L, 0L, 1L,
                new MfiOperationContext.Cancellation());
        eq(0L, zero.remainingMillis());
        expectTimeout(new Runnable(){public void run(){zero.checkActive(1L);}});
        expectIllegal(new Runnable(){public void run(){
            new MfiOperationContext(c, 0L, -1L, 1L, new MfiOperationContext.Cancellation());
        }});
        expectIllegal(new Runnable(){public void run(){
            new MfiOperationContext(c, 0L, 300001L, 1L, new MfiOperationContext.Cancellation());
        }});
        eq(300000L, new MfiOperationContext(c, 0L, 300000L, 1L,
                new MfiOperationContext.Cancellation()).remainingMillis());
    }

    private static void cancellationAndGenerationAreTerminal() {
        FakeClock c = new FakeClock();
        MfiOperationContext.Cancellation cancel = new MfiOperationContext.Cancellation();
        MfiOperationContext op = new MfiOperationContext(c, 0L, 1000L, 9L, cancel);
        expectCancelled(new Runnable(){public void run(){op.checkActive(10L);}});
        cancel.cancel();
        truth(op.isCancelled());
        expectCancelled(new Runnable(){public void run(){op.checkActive(9L);}});
    }

    private static void interruptionIsObservedAndPreserved() throws Exception {
        FakeClock c = new FakeClock();
        final MfiOperationContext op = new MfiOperationContext(c, 0L, 1000L, 3L,
                new MfiOperationContext.Cancellation());
        Thread.currentThread().interrupt();
        try {
            expectCancelled(new Runnable(){public void run(){op.checkActive(3L);}});
            truth(Thread.currentThread().isInterrupted());
        } finally {
            // Clear only the test thread's interrupt so later test cases can run.
            Thread.interrupted();
        }
    }

    private static void monotonicWraparoundIsHandled() {
        FakeClock c = new FakeClock(); c.now = Long.MAX_VALUE - 500000L;
        MfiOperationContext op = new MfiOperationContext(c, c.now, 2L, 1L,
                new MfiOperationContext.Cancellation());
        c.now += 1000000L; // signed long wraps, elapsed duration remains positive
        eq(1L, op.remainingMillis());
    }

    private static void eq(long a,long b){checks++;if(a!=b)throw new AssertionError(a+" != "+b);}
    private static void truth(boolean b){checks++;if(!b)throw new AssertionError("expected true");}
    private static void expectIllegal(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("expected IllegalArgumentException");}
    private static void expectTimeout(Runnable r){checks++;try{r.run();}catch(MfiOperationContext.OperationTimeoutException e){return;}throw new AssertionError("expected timeout");}
    private static void expectCancelled(Runnable r){checks++;try{r.run();}catch(MfiOperationContext.OperationCancelledException e){return;}throw new AssertionError("expected cancellation");}
}
