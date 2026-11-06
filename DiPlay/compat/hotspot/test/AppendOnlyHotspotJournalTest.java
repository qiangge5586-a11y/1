import com.shilapi.xcertplay.network.safety.AppendOnlyHotspotJournal;
import com.shilapi.xcertplay.network.safety.HotspotTransactionCoordinator.State;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

public final class AppendOnlyHotspotJournalTest {
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        File dir=new File(System.getProperty("java.io.tmpdir"),"hotspot-journal-test-"+System.nanoTime());
        check(dir.mkdir(),"temporary directory created");
        try {
            AppendOnlyHotspotJournal journal=new AppendOnlyHotspotJournal(dir);
            check(journal.readLatest()==null,"empty journal");
            journal.persist("txn_test_0001",State.NEW,"begin");
            journal.persist("txn_test_0001",State.PREFLIGHTED,"read-only preflight");
            journal.persist("txn_test_0001",State.BACKED_UP,"备份已验证");
            AppendOnlyHotspotJournal.Entry e=journal.readLatest();
            check(e!=null&&e.state==State.BACKED_UP,"latest state recovered");
            check("txn_test_0001".equals(e.transactionId),"transaction ID recovered");
            check("备份已验证".equals(e.detail),"UTF-8 detail round trip");

            File log=new File(dir,"hotspot-transactions.log");
            RandomAccessFile raf=new RandomAccessFile(log,"rw");
            raf.seek(raf.length()-2); raf.write('X'); raf.close();
            try { journal.readLatest(); throw new AssertionError("tampered journal accepted"); }
            catch(java.io.IOException expected) { }
            try { journal.persist("txn_test_0002",State.NEW,"must not append after corruption");
                throw new AssertionError("append to corrupt journal accepted"); }
            catch(java.io.IOException expected) { }
            System.out.println("PASS: durable journal append, UTF-8 recovery and tamper fail-closed");
        } finally {
            File log=new File(dir,"hotspot-transactions.log"); if(log.exists())log.delete();
            dir.delete();
        }
    }
}
