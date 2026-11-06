import com.shilapi.xcertplay.network.safety.HotspotTransactionCoordinator;
import com.shilapi.xcertplay.network.safety.HotspotTransactionCoordinator.*;

public final class CoordinatorSelfTest {
 static final class S implements Snapshot {}
 static final class B implements Backup {}
 static final class Fake implements RootOps {
  boolean fail, backupMissing, restored, stopped;
  int applyCalls, restoreCalls;
  public Snapshot preflight(){return new S();}
  public Backup createAndVerifyBackup(String i,Snapshot s){return backupMissing?null:new B();}
  public ProcessIdentity apply(String i,Snapshot s,Backup b){
   applyCalls++;return new ProcessIdentity(123,456L,"/data/local/tmp/owned-hostapd");
  }
  public void verifyActive(ProcessIdentity p)throws Exception{
   if(fail)throw new Exception("simulated verification failure");
  }
  public void stopIfStillOwned(ProcessIdentity p){stopped=true;}
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

  Fake bad=new Fake();bad.fail=true;J j2=new J();
  try {new HotspotTransactionCoordinator(bad,j2).start("test_txn_0002");
   throw new AssertionError("expected verification failure");
  } catch(Exception expected) {
   if(!bad.stopped||!bad.restored||j2.last!=State.ROLLED_BACK)
    throw new AssertionError("verified rollback path");
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

  System.out.println("PASS: success, rollback, backup gate and transaction ID validation");
 }
}
