package com.shilapi.xcertplay.network.safety;

/**
 * Transaction state machine. RootOps is responsible for durable, cross-process
 * locking and for invoking ProcessStartedListener immediately after a child is
 * created, before performing any other fallible work.
 */
public final class HotspotTransactionCoordinator {
 public enum State { NEW, PREFLIGHTED, BACKED_UP, APPLYING, ACTIVE, ROLLING_BACK, ROLLED_BACK, RECOVERY_REQUIRED }
 public static final class ProcessIdentity {
  public final int pid; public final long startTicks; public final String commandLine;
  public ProcessIdentity(int p,long t,String c){if(p<=1||t<=0||c==null||c.length()==0)throw new IllegalArgumentException("Invalid process identity");pid=p;startTicks=t;commandLine=c;}
 }
 public interface Snapshot {}
 public interface Backup {}
 public interface Journal { void persist(String id,State state,String detail) throws Exception; }
 public interface LockHandle { void close() throws Exception; }
 public interface ProcessStartedListener { void onStarted(ProcessIdentity identity); }
 public interface RootOps {
  /** Must acquire an OS-backed exclusive lock held until LockHandle.close(). */
  LockHandle acquireExclusiveLock(String id) throws Exception;
  Snapshot preflight() throws Exception;
  Backup createAndVerifyBackup(String id,Snapshot snapshot) throws Exception;
  /**
   * Notify listener immediately after each owned process is created and its
   * PID/start-time/cmdline identity is captured. Do this before any later step
   * that may throw. Return the primary process identity on success.
   */
  ProcessIdentity apply(String id,Snapshot snapshot,Backup backup,ProcessStartedListener listener) throws Exception;
  void verifyActive(ProcessIdentity launched) throws Exception;
  void stopIfStillOwned(ProcessIdentity launched) throws Exception;
  void restore(Snapshot snapshot,Backup backup) throws Exception;
 }
 private final RootOps ops; private final Journal journal;
 public HotspotTransactionCoordinator(RootOps o,Journal j){if(o==null||j==null)throw new IllegalArgumentException("ops/journal required");ops=o;journal=j;}
 public State start(String id) throws Exception {
  if(id==null||!id.matches("[A-Za-z0-9_-]{8,64}"))throw new IllegalArgumentException("Invalid transaction id");
  LockHandle lock=ops.acquireExclusiveLock(id);
  if(lock==null)throw new IllegalStateException("Exclusive hotspot lock unavailable");
  Exception failure=null;
  try {
   return runLocked(id);
  } catch(Exception e) {
   failure=e; throw e;
  } finally {
   try { lock.close(); }
   catch(Exception unlockFailure) {
    if(failure!=null) failure.addSuppressed(unlockFailure);
    else throw unlockFailure;
   }
  }
 }
 private State runLocked(final String id) throws Exception {
  Snapshot s=null; Backup b=null; final ProcessIdentity[] launched=new ProcessIdentity[1];
  State state=State.NEW; journal.persist(id,state,"begin; exclusive lock held");
  try {
   s=ops.preflight(); if(s==null)throw new IllegalStateException("No preflight snapshot");
   state=State.PREFLIGHTED;journal.persist(id,state,"read-only preflight complete");
   b=ops.createAndVerifyBackup(id,s);if(b==null)throw new IllegalStateException("Backup not verified");
   state=State.BACKED_UP;journal.persist(id,state,"backup verified");
   state=State.APPLYING;journal.persist(id,state,"applying");
   ProcessIdentity returned=ops.apply(id,s,b,new ProcessStartedListener(){
    public void onStarted(ProcessIdentity identity) {
     if(identity==null)throw new IllegalArgumentException("Started process identity required");
     launched[0]=identity;
    }
   });
   if(returned==null)throw new IllegalStateException("No owned process identity");
   if(launched[0]==null)launched[0]=returned;
   else if(!same(launched[0],returned))throw new IllegalStateException("Returned process differs from registered process");
   ops.verifyActive(returned);state=State.ACTIVE;journal.persist(id,state,"active and verified");return state;
  } catch(Exception failure) {
   if(s==null||b==null){state=State.RECOVERY_REQUIRED;journal.persist(id,state,"aborted before verified backup");throw failure;}
   try {
    state=State.ROLLING_BACK;journal.persist(id,state,"rollback started");
    if(launched[0]!=null)ops.stopIfStillOwned(launched[0]);
    ops.restore(s,b);state=State.ROLLED_BACK;journal.persist(id,state,"rollback verified");
   } catch(Exception rollbackFailure) {
    state=State.RECOVERY_REQUIRED;journal.persist(id,state,"rollback failed");
    failure.addSuppressed(rollbackFailure);
   }
   throw failure;
  }
 }
 private static boolean same(ProcessIdentity a,ProcessIdentity b) {
  return a.pid==b.pid&&a.startTicks==b.startTicks&&a.commandLine.equals(b.commandLine);
 }
}
