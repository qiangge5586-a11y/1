import com.shilapi.xcertplay.network.safety.AppendOnlyHotspotJournal;
import com.shilapi.xcertplay.network.safety.HotspotRecoveryPolicy;
import com.shilapi.xcertplay.network.safety.HotspotRecoveryPolicy.Decision;
import com.shilapi.xcertplay.network.safety.HotspotTransactionCoordinator.State;

public final class HotspotRecoveryPolicyTest {
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static AppendOnlyHotspotJournal.Entry entry(State s){
        return new AppendOnlyHotspotJournal.Entry("txn_recovery_01",s,1L,"test","hash");
    }
    public static void main(String[] args){
        check(HotspotRecoveryPolicy.classify(null)==Decision.NO_JOURNAL,"empty journal");
        check(HotspotRecoveryPolicy.classify(entry(State.NEW))==Decision.NO_SYSTEM_CHANGE_RECORDED,"new");
        check(HotspotRecoveryPolicy.classify(entry(State.PREFLIGHTED))==Decision.NO_SYSTEM_CHANGE_RECORDED,"preflight");
        check(HotspotRecoveryPolicy.classify(entry(State.BACKED_UP))==Decision.NO_SYSTEM_CHANGE_RECORDED,"backup before apply");
        check(HotspotRecoveryPolicy.classify(entry(State.ACTIVE))==Decision.VERIFY_ACTIVE_PROCESS,"active requires identity verification");
        check(HotspotRecoveryPolicy.classify(entry(State.ROLLED_BACK))==Decision.CLEAN_ROLLED_BACK,"rolled back");
        check(HotspotRecoveryPolicy.classify(entry(State.APPLYING))==Decision.MANUAL_RECOVERY_REQUIRED,"interrupted apply");
        check(HotspotRecoveryPolicy.classify(entry(State.ROLLING_BACK))==Decision.MANUAL_RECOVERY_REQUIRED,"interrupted rollback");
        check(HotspotRecoveryPolicy.classify(entry(State.RECOVERY_REQUIRED))==Decision.MANUAL_RECOVERY_REQUIRED,"recovery required");
        System.out.println("PASS: startup recovery classification fails closed");
    }
}
