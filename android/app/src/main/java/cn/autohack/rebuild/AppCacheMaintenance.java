package cn.autohack.rebuild;

import java.io.File;
import java.io.IOException;

/** Maintenance operations restricted to children of the app-private cache directory. */
public final class AppCacheMaintenance {
    private AppCacheMaintenance() {}

    public static long calculateSize(File cacheRoot) {
        if (cacheRoot == null || !cacheRoot.isDirectory()) return 0L;
        try {
            String root = cacheRoot.getCanonicalPath();
            File[] children = cacheRoot.listFiles();
            if (children == null) return 0L;
            long total = 0L;
            for (File child : children) total += calculate(child, root);
            return total;
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long calculate(File file, String root) throws IOException {
        if (!isInsideRoot(file, root)) return 0L;
        if (file.isFile()) return file.length();
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) total += calculate(child, root);
        return total;
    }

    public static boolean clearContents(File cacheRoot) {
        if (cacheRoot == null || !cacheRoot.isDirectory()) return false;
        try {
            String root = cacheRoot.getCanonicalPath();
            File[] children = cacheRoot.listFiles();
            if (children == null) return false;
            boolean success = true;
            for (File child : children) success &= delete(child, root);
            return success;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean delete(File file, String root) throws IOException {
        if (!isInsideRoot(file, root)) return false;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            boolean success = true;
            for (File child : children) success &= delete(child, root);
            if (!success) return false;
        }
        return file.delete();
    }

    private static boolean isInsideRoot(File file, String root) throws IOException {
        String path = file.getCanonicalPath();
        return path.startsWith(root + File.separator);
    }
}
