import com.shilapi.xcertplay.network.safety.HotspotTransactionCoordinator;
import com.shilapi.xcertplay.network.safety.HotspotTransactionCoordinator.*;

public final class CoordinatorSelfTest {
 static final class S implements Snapshot {}
 static final class B implements Backup {}
 static final class Fake implements RootOps {
  boolean fail, backupMissing, restored, stopped, failAfterLaunch, failStop, lockHeld, lockReleased;
  int applyCalls, restoreCalls, stopCalls;
  public LockHandle acquireExclusiveLock(String id) {
   if(lockHeld)throw new IllegalStateException("concurrent transaction");
   lockHeld=true;
   return new LockHandle(){public void close(){lockHeld=false;lockReleased=true;}};
  }
  public Snapshot preflight(){return new S();}
  public Backup createAndVerifyBackup(String i,Snapshot s){return backupMissing?null:new B();}
  public ProcessIdentity apply(String i,Snapshot s,Backup b,ProcessStartedListener listener)throws Exception{
   applyCalls++;
   ProcessIdentity p=new ProcessIdentity(123,456L,"/data/local/tmp/owned-hostapd");
   listener.onStarted(p);
   listener.onStarted(new ProcessIdentity(124,457L,"/data/local/tmp/owned-dnsmasq"));
   if(failAfterLaunch)throw new Exception("simulated failure after process launch");
   return p;
  }
  public void verifyActive(ProcessIdentity p)throws Exception{
   if(fail)throw new Exception("simulated verification failure");
  }
  public void stopIfStillOwned(ProcessIdentity p)throws Exception{stopped=true;stopCalls++;if(failStop)throw new Exception("simulated owned-process stop failure");}
  public void restore(Snapshot s,Backup b){restored=true;restoreCalls++;}
 }
 static final class J implements Journal {
  State last;
  public void persist(String i,State s,String d){last=s;}
 }
 public static void main(String[] a)throws Exception {
  Fake ok=new Fake();J j1=new J();
  if(new HotspotTransactionCoordinator(ok,j1).start("test_txn_0001")!=State.ACTIVE)
   throw new AssertionError("success path");
  if(ok.lockHeld||!ok.lockReleased)throw new AssertionError("lock released after success");

  Fake bad=new Fake();bad.fail=true;J j2=new J();
  try {new HotspotTransactionCoordinator(bad,j2).start("test_txn_0002");
   throw new AssertionError("expected verification failure");
  } catch(Exception expected) {
   if(!bad.stopped||bad.stopCalls!=2||!bad.restored||j2.last!=State.ROLLED_BACK)
    throw new AssertionError("verified rollback path");
   if(bad.lockHeld||!bad.lockReleased)throw new AssertionError("lock released after rollback");
  }

  Fake partial=new Fake();partial.failAfterLaunch=true;J j4=new J();
  try {new HotspotTransactionCoordinator(partial,j4).start("test_txn_0004");
   throw new AssertionError("expected post-launch failure");
  } catch(Exception expected) {
   if(!partial.stopped||partial.stopCalls!=2||!partial.restored||j4.last!=State.ROLLED_BACK)
    throw new AssertionError("process registered before post-launch failure");
  }

  Fake stopFailure=new Fake();stopFailure.fail=true;stopFailure.failStop=true;J j5=new J();
  try {new HotspotTransactionCoordinator(stopFailure,j5).start("test_txn_0005");
   throw new AssertionError("expected stop failure");
  } catch(Exception expected) {
   if(!stopFailure.restored||j5.last!=State.RECOVERY_REQUIRED)
    throw new AssertionError("config restore must still run after process stop failure");
  }

  Fake noBackup=new Fake();noBackup.backupMissing=true;J j3=new J();
  try {new HotspotTransactionCoordinator(noBackup,j3).start("test_txn_0003");
   throw new AssertionError("expected missing backup failure");
  } catch(Exception expected) {
   if(noBackup.applyCalls!=0||noBackup.restoreCalls!=0||j3.last!=State.RECOVERY_REQUIRED)
    throw new AssertionError("apply must be blocked without verified backup");
  }

  try {new HotspotTransactionCoordinator(ok,new J()).start("bad id");
   throw new AssertionError("invalid transaction id accepted");
  } catch(IllegalArgumentException expected) { }
  System.out.println("PASS: lock, success, rollback, early process ownership, backup gate and ID validation");
 }
}
