package com.shilapi.xcertplay.compat.mfi;

public final class MfiIap2ExchangeCoordinatorTest {
    private static int checks;
    public static void main(String[] args) {
        happyPathPreservesMessageIdsAndPayloads();
        outOfOrderMessagesFailClosed();
        invalidChallengeFailsClosed();
        peerFailureIsTerminal();
        staleGenerationCannotReply();
        System.out.println("MfiIap2ExchangeCoordinatorTest: PASS (" + checks + " checks)");
    }
    private static MfiProviderSession session(final long generation) {
        MfiOperationContext context = MfiOperationContext.start(5000L, generation,
                new MfiOperationContext.Cancellation());
        return new MfiProviderSession(new MfiProviderSession.Provider() {
            public byte[] readCertificate(MfiOperationContext c) { return new byte[] {1,2,3}; }
            public byte[] signChallenge(byte[] challenge, MfiOperationContext c) {
                return new byte[] {(byte)(challenge.length), 9};
            }
        }, context, generation);
    }
    private static void happyPathPreservesMessageIdsAndPayloads() {
        MfiIap2ExchangeCoordinator c = new MfiIap2ExchangeCoordinator(session(4), 4);
        MfiIap2ExchangeCoordinator.Reply cert = c.requestCertificate();
        eq(MfiIap2ExchangeCoordinator.CERTIFICATE_RESPONSE, cert.messageId);
        bytes(new byte[] {1,2,3}, cert.payload());
        MfiIap2ExchangeCoordinator.Reply sig = c.requestChallenge(new byte[] {7,8});
        eq(MfiIap2ExchangeCoordinator.SIGNATURE_RESPONSE, sig.messageId);
        bytes(new byte[] {2,9}, sig.payload());
        c.peerResult(MfiIap2ExchangeCoordinator.AUTHENTICATION_SUCCEEDED);
        eq(MfiIap2ExchangeCoordinator.State.SUCCEEDED, c.state());
    }
    private static void outOfOrderMessagesFailClosed() {
        final MfiIap2ExchangeCoordinator c = new MfiIap2ExchangeCoordinator(session(5), 5);
        expectProtocol(new Runnable() { public void run() { c.requestChallenge(new byte[] {1}); }});
        eq(MfiIap2ExchangeCoordinator.State.FAILED, c.state());
    }
    private static void invalidChallengeFailsClosed() {
        final MfiIap2ExchangeCoordinator c = new MfiIap2ExchangeCoordinator(session(6), 6);
        c.requestCertificate();
        expectIllegal(new Runnable() { public void run() { c.requestChallenge(new byte[0]); }});
        eq(MfiIap2ExchangeCoordinator.State.FAILED, c.state());
    }
    private static void peerFailureIsTerminal() {
        MfiIap2ExchangeCoordinator c = new MfiIap2ExchangeCoordinator(session(7), 7);
        c.requestCertificate();
        c.peerResult(MfiIap2ExchangeCoordinator.AUTHENTICATION_FAILED);
        eq(MfiIap2ExchangeCoordinator.State.FAILED, c.state());
    }
    private static void staleGenerationCannotReply() {
        final MfiIap2ExchangeCoordinator c = new MfiIap2ExchangeCoordinator(session(8), 9);
        expectCancelled(new Runnable() { public void run() { c.requestCertificate(); }});
        eq(MfiIap2ExchangeCoordinator.State.FAILED, c.state());
    }
    private static void bytes(byte[] a, byte[] b) {
        checks++; if (a.length != b.length) throw new AssertionError("length");
        for(int i=0;i<a.length;i++) if(a[i]!=b[i]) throw new AssertionError("byte");
    }
    private static void eq(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    private static void expectProtocol(Runnable r){checks++;try{r.run();}catch(MfiIap2ExchangeCoordinator.ProtocolException e){return;}throw new AssertionError("expected protocol failure");}
    private static void expectIllegal(Runnable r){checks++;try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("expected invalid challenge");}
    private static void expectCancelled(Runnable r){checks++;try{r.run();}catch(MfiOperationContext.OperationCancelledException e){return;}throw new AssertionError("expected cancellation");}
}
