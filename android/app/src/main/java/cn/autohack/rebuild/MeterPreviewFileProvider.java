package cn.autohack.rebuild;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only content provider for user-shared meter preview PNGs in the app cache. */
public final class MeterPreviewFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "image/png"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        if (file == null || !file.isFile()) return null;
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        File file = resolve(uri);
        if (file == null || !file.isFile()) throw new FileNotFoundException("Preview not found");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File resolve(Uri uri) {
        if (getContext() == null) return null;
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")
                || name.contains("..")) return null;
        File root = new File(getContext().getCacheDir(), "shared_previews");
        File file = new File(root, name);
        try {
            String rootPath = root.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(rootPath)) return null;
        } catch (IOException e) {
            return null;
        }
        return file;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }
}
