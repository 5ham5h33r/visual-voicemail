package org.visualvoicemail.app;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.AtomicFile;

import java.io.*;
import java.util.*;

/** Only explicitly shared temporary files are exposed, through individual Android read grants. */
public final class ShareProvider extends ContentProvider {
    private static final long MAX_AGE = 24 * 60 * 60 * 1000L;

    @Override
    public boolean onCreate() {
        return true;
    }

    static Uri prepare(Context context, File source, String date) throws Exception {
        ExportAudioFormat format = ExportAudioFormat.detect(source);
        File directory = new File(context.getCacheDir(), "sharing");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Share storage unavailable.");
        File[] old = directory.listFiles();
        if (old != null)
            for (File file : old)
                if (file.getName().matches("[0-9a-f-]{36}\\.(?:amr|awb)(?:\\.new|\\.bak)?")
                        && System.currentTimeMillis() - file.lastModified() > MAX_AGE)
                    file.delete();
        String token = UUID.randomUUID().toString();
        File target = new File(directory, token + "." + format.extension);
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream output = atomic.startWrite();
        try (InputStream input = new FileInputStream(source)) {
            byte[] bytes = new byte[8192];
            int n, total = 0;
            while ((n = input.read(bytes)) != -1) {
                total += n;
                if (total > 25 * 1024 * 1024)
                    throw new IOException("Recording too large to share.");
                output.write(bytes, 0, n);
            }
            if (total <= 0) throw new IOException("Recording empty.");
            atomic.finishWrite(output);
        } catch (Exception error) {
            atomic.failWrite(output);
            throw error;
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".sharing")
                .appendPath(token)
                .appendPath(format.filename(date))
                .build();
    }

    private File file(Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (!"content".equals(uri.getScheme())
                || !(getContext().getPackageName() + ".sharing").equals(uri.getAuthority())
                || segments.size() != 2
                || !segments.get(0).matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                || !segments.get(1).matches("Voicemail_[A-Za-z0-9_.-]+\\.(?:amr|awb)"))
            throw new FileNotFoundException("Invalid shared recording.");
        String extension = segments.get(1).endsWith(".awb") ? "awb" : "amr";
        File file =
                new File(
                        new File(getContext().getCacheDir(), "sharing"),
                        segments.get(0) + "." + extension);
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() > MAX_AGE)
            throw new FileNotFoundException("Share expired. Share the recording again.");
        return file;
    }

    @Override
    public String getType(Uri uri) {
        try {
            file(uri);
            return uri.getLastPathSegment().endsWith(".awb") ? "audio/amr-wb" : "audio/amr";
        } catch (FileNotFoundException missing) {
            return null;
        }
    }

    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = file(uri);
            String[] columns =
                    projection == null
                            ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                            : projection;
            MatrixCursor result = new MatrixCursor(columns, 1);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++)
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i]))
                    values[i] = uri.getLastPathSegment();
                else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            result.addRow(values);
            return result;
        } catch (FileNotFoundException missing) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Shared recordings are read-only.");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only.");
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] args) {
        throw new UnsupportedOperationException("Read-only.");
    }

    @Override
    public int delete(Uri uri, String where, String[] args) {
        throw new UnsupportedOperationException("Read-only.");
    }
}
