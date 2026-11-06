import com.shilapi.xcertplay.network.safety.AppendOnlyHotspotJournal;
import com.shilapi.xcertplay.network.safety.HotspotRecoveryPolicy;
import com.shilapi.xcertplay.network.safety.HotspotRecoveryPolicy.Decision;
import com.shilapi.xcertplay.network.safety.HotspotTransactionCoordinator.State;
import java.io.File;

public final class HotspotRecoveryPolicyTest {
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static Decision classify(State state)throws Exception{
        File dir=new File(System.getProperty("java.io.tmpdir"),"hotspot-policy-"+state.name()+"-"+System.nanoTime());
        check(dir.mkdir(),"temporary directory created");
        try {
            AppendOnlyHotspotJournal journal=new AppendOnlyHotspotJournal(dir);
            journal.persist("txn_recovery_01",state,"policy test");
            return HotspotRecoveryPolicy.classify(journal.readLatest());
        } finally {
            File log=new File(dir,"hotspot-transactions.log");if(log.exists())log.delete();
            dir.delete();
        }
    }
    public static void main(String[] args)throws Exception{
        check(HotspotRecoveryPolicy.classify(null)==Decision.NO_JOURNAL,"empty journal");
        check(classify(State.NEW)==Decision.NO_SYSTEM_CHANGE_RECORDED,"new");
        check(classify(State.PREFLIGHTED)==Decision.NO_SYSTEM_CHANGE_RECORDED,"preflight");
        check(classify(State.BACKED_UP)==Decision.NO_SYSTEM_CHANGE_RECORDED,"backup before apply");
        check(classify(State.ACTIVE)==Decision.VERIFY_ACTIVE_PROCESS,"active requires identity verification");
        check(classify(State.ROLLED_BACK)==Decision.CLEAN_ROLLED_BACK,"rolled back");
        check(classify(State.APPLYING)==Decision.MANUAL_RECOVERY_REQUIRED,"interrupted apply");
        check(classify(State.ROLLING_BACK)==Decision.MANUAL_RECOVERY_REQUIRED,"interrupted rollback");
        check(classify(State.RECOVERY_REQUIRED)==Decision.MANUAL_RECOVERY_REQUIRED,"recovery required");
        System.out.println("PASS: startup recovery classification fails closed");
    }
}
