package com.shilapi.xcertplay.compat.mfi;

public final class MfiProviderSessionTest {
    private static int checks;

    private static final class Clock implements MfiOperationContext.NanoClock {
        long now;
        public long nanoTime() { return now; }
    }

    public static void main(String[] args) {
        sameContextSpansCertificateAndSignature();
        staleGenerationCannotAdvanceSession();
        providerCompletionAfterDeadlineIsRejected();
        cancelClosesAndCancelsContext();
        invalidChallengeFailsClosed();
        System.out.println("MfiProviderSessionTest: PASS (" + checks + " checks)");
    }

    private static void sameContextSpansCertificateAndSignature() {
        final Clock clock = new Clock();
        final MfiOperationContext context = context(clock, 1000L, 41L);
        final Object[] seen = new Object[2];
        MfiProviderSession.Provider provider = new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) {
                seen[0] = c; clock.now += 400000000L; c.checkActive(41L);
                return new byte[] {1, 2, 3};
            }
            public byte[] signChallenge(byte[] challenge, MfiOperationContext c) {
                seen[1] = c; clock.now += 300000000L; c.checkActive(41L);
                eq(300L, c.remainingMillis());
                return new byte[] {9, 8};
            }
        };
        MfiProviderSession session = new MfiProviderSession(provider, context, 41L);
        bytes(new byte[] {1, 2, 3}, session.readCertificate(41L));
        bytes(new byte[] {9, 8}, session.signChallenge(new byte[] {7}, 41L));
        truth(seen[0] == context && seen[1] == context);
        eq(MfiProviderSession.State.CHALLENGE_SIGNED, session.state());
    }

    private static void staleGenerationCannotAdvanceSession() {
        Clock clock = new Clock();
        MfiOperationContext context = context(clock, 1000L, 5L);
        MfiProviderSession session = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) { return new byte[] {1}; }
            public byte[] signChallenge(byte[] b, MfiOperationContext c) { return new byte[] {2}; }
        }, context, 5L);
        expectCancelled(new Runnable() { public void run() { session.readCertificate(6L); }});
        eq(MfiProviderSession.State.FAILED, session.state());
    }

    private static void providerCompletionAfterDeadlineIsRejected() {
        final Clock clock = new Clock();
        final MfiOperationContext context = context(clock, 10L, 3L);
        final MfiProviderSession session = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) {
                clock.now += 11000000L;
                return new byte[] {1};
            }
            public byte[] signChallenge(byte[] b, MfiOperationContext c) { return new byte[] {2}; }
        }, context, 3L);
        expectTimeout(new Runnable() { public void run() { session.readCertificate(3L); }});
        eq(MfiProviderSession.State.FAILED, session.state());
    }

    private static void cancelClosesAndCancelsContext() {
        Clock clock = new Clock();
        final MfiOperationContext context = context(clock, 1000L, 12L);
        MfiProviderSession session = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) { return new byte[] {1}; }
            public byte[] signChallenge(byte[] b, MfiOperationContext c) { return new byte[] {2}; }
        }, context, 12L);
        session.cancel();
        truth(context.isCancelled());
        eq(MfiProviderSession.State.CLOSED, session.state());
        expectCancelled(new Runnable() { public void run() { context.checkActive(12L); }});
    }

    private static void invalidChallengeFailsClosed() {
        Clock clock = new Clock();
        final MfiProviderSession session = new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) { return new byte[] {1}; }
            public byte[] signChallenge(byte[] b, MfiOperationContext c) { return new byte[] {2}; }
        }, context(clock, 1000L, 8L), 8L);
        session.readCertificate(8L);
        expectIllegal(new Runnable() { public void run() { session.signChallenge(new byte[0], 8L); }});
        eq(MfiProviderSession.State.FAILED, session.state());
    }

    private static MfiOperationContext context(Clock c, long timeout, long generation) {
        return new MfiOperationContext(c, c.now, timeout, generation,
                new MfiOperationContext.Cancellation());
    }
    private static void bytes(byte[] a, byte[] b) {
        checks++; if (a.length != b.length) throw new AssertionError("length");
        for (int i=0;i<a.length;i++) if (a[i]!=b[i]) throw new AssertionError("byte");
    }
    private static void eq(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    private static void truth(boolean b){checks++;if(!b)throw new AssertionError("expected true");}
    private static void expectIllegal(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("expected illegal argument");}
    private static void expectCancelled(Runnable r){checks++;try{r.run();}catch(MfiOperationContext.OperationCancelledException e){return;}throw new AssertionError("expected cancellation");}
    private static void expectTimeout(Runnable r){checks++;try{r.run();}catch(MfiOperationContext.OperationTimeoutException e){return;}throw new AssertionError("expected timeout");}
}
