package cn.autohack.rebuild;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

public final class AppCacheMaintenanceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void calculatesNestedCacheSize() throws Exception {
        File root = temporary.newFolder("cache");
        File nested = new File(root, "nested");
        assertTrue(nested.mkdir());
        write(new File(root, "a.bin"), 10);
        write(new File(nested, "b.bin"), 15);
        assertEquals(25L, AppCacheMaintenance.calculateSize(root));
    }

    @Test public void clearsOnlyCacheContentsAndKeepsRootDirectory() throws Exception {
        File root = temporary.newFolder("cache");
        File nested = new File(root, "nested");
        assertTrue(nested.mkdir());
        write(new File(nested, "preview.png"), 12);
        assertTrue(AppCacheMaintenance.clearContents(root));
        assertTrue(root.isDirectory());
        assertEquals(0, root.listFiles().length);
        assertEquals(0L, AppCacheMaintenance.calculateSize(root));
    }

    @Test public void missingDirectoryIsHandledSafely() {
        assertEquals(0L, AppCacheMaintenance.calculateSize(new File(temporary.getRoot(), "missing")));
        assertFalse(AppCacheMaintenance.clearContents(new File(temporary.getRoot(), "missing")));
    }

    private static void write(File file, int size) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            for (int i = 0; i < size; i++) output.write(i);
        }
    }
}
