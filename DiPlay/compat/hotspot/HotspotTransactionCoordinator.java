package com.shilapi.xcertplay.network.safety;

public final class HotspotTransactionCoordinator {
 public enum State { NEW, PREFLIGHTED, BACKED_UP, APPLYING, ACTIVE, ROLLING_BACK, ROLLED_BACK, RECOVERY_REQUIRED }
 public static final class ProcessIdentity {
  public final int pid; public final long startTicks; public final String commandLine;
  public ProcessIdentity(int p,long t,String c){if(p<=1||t<=0||c==null||c.length()==0)throw new IllegalArgumentException("Invalid process identity");pid=p;startTicks=t;commandLine=c;}
 }
 public interface Snapshot {}
 public interface Backup {}
 public interface Journal { void persist(String id,State state,String detail) throws Exception; }
 public interface RootOps {
  Snapshot preflight() throws Exception;
  Backup createAndVerifyBackup(String id,Snapshot snapshot) throws Exception;
  ProcessIdentity apply(String id,Snapshot snapshot,Backup backup) throws Exception;
  void verifyActive(ProcessIdentity launched) throws Exception;
  void stopIfStillOwned(ProcessIdentity launched) throws Exception;
  void restore(Snapshot snapshot,Backup backup) throws Exception;
 }
 private final RootOps ops; private final Journal journal;
 public HotspotTransactionCoordinator(RootOps o,Journal j){if(o==null||j==null)throw new IllegalArgumentException("ops/journal required");ops=o;journal=j;}
 public State start(String id) throws Exception {
  if(id==null||!id.matches("[A-Za-z0-9_-]{8,64}"))throw new IllegalArgumentException("Invalid transaction id");
  Snapshot s=null; Backup b=null; ProcessIdentity p=null; State state=State.NEW; journal.persist(id,state,"begin");
  try {
   s=ops.preflight(); if(s==null)throw new IllegalStateException("No preflight snapshot");
   state=State.PREFLIGHTED;journal.persist(id,state,"read-only preflight complete");
   b=ops.createAndVerifyBackup(id,s);if(b==null)throw new IllegalStateException("Backup not verified");
   state=State.BACKED_UP;journal.persist(id,state,"backup verified");
   state=State.APPLYING;journal.persist(id,state,"applying");
   p=ops.apply(id,s,b);if(p==null)throw new IllegalStateException("No owned process identity");
   ops.verifyActive(p);state=State.ACTIVE;journal.persist(id,state,"active and verified");return state;
  } catch(Exception failure) {
   if(s==null||b==null){state=State.RECOVERY_REQUIRED;journal.persist(id,state,"aborted before verified backup");throw failure;}
   try {state=State.ROLLING_BACK;journal.persist(id,state,"rollback started");if(p!=null)ops.stopIfStillOwned(p);ops.restore(s,b);state=State.ROLLED_BACK;journal.persist(id,state,"rollback verified");}
   catch(Exception rollbackFailure){state=State.RECOVERY_REQUIRED;journal.persist(id,state,"rollback failed");failure.addSuppressed(rollbackFailure);}
   throw failure;
  }
 }
}
