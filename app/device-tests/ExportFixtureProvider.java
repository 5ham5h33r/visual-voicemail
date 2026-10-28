package org.visualvoicemail.checks;

import android.database.*;
import android.os.*;
import android.provider.*;

import java.io.*;
import java.util.UUID;

/** Temporary DocumentsProvider registered only for the public-data resilience check APK. */
public final class ExportFixtureProvider extends DocumentsProvider {
    public static final String AUTHORITY = "org.visualvoicemail.checks.documents";
    private static final String[] COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    private File directory() {
        File dir = new File(getContext().getCacheDir(), "public-exports");
        dir.mkdirs();
        return dir;
    }

    private File file(String id) throws FileNotFoundException {
        if (!id.matches("export-[0-9a-f-]{36}"))
            throw new FileNotFoundException("Invalid public fixture");
        return new File(directory(), id);
    }

    private void add(MatrixCursor cursor, String id) throws FileNotFoundException {
        File item = "root".equals(id) ? directory() : file(id);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            if (column.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) row.add(id);
            else if (column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                row.add("root".equals(id) ? "Public export check" : "Public recording.amr");
            else if (column.equals(DocumentsContract.Document.COLUMN_MIME_TYPE))
                row.add("root".equals(id) ? DocumentsContract.Document.MIME_TYPE_DIR : "audio/amr");
            else if (column.equals(DocumentsContract.Document.COLUMN_FLAGS))
                row.add(
                        "root".equals(id)
                                ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                                : DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                                        | DocumentsContract.Document.FLAG_SUPPORTS_DELETE);
            else if (column.equals(DocumentsContract.Document.COLUMN_SIZE)) row.add(item.length());
            else row.add(null);
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor =
                new MatrixCursor(
                        new String[] {
                            DocumentsContract.Root.COLUMN_ROOT_ID,
                            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Root.COLUMN_TITLE,
                            DocumentsContract.Root.COLUMN_FLAGS
                        });
        cursor.addRow(
                new Object[] {
                    "root",
                    "root",
                    "Public export check",
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                });
        return cursor;
    }

    @Override
    public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(projection == null ? COLUMNS : projection);
        add(cursor, id);
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String id, String[] projection, String sort)
            throws FileNotFoundException {
        if (!"root".equals(id)) throw new FileNotFoundException();
        MatrixCursor cursor = new MatrixCursor(projection == null ? COLUMNS : projection);
        File[] files = directory().listFiles();
        if (files != null)
            for (File item : files)
                if (item.getName().matches("export-[0-9a-f-]{36}")) add(cursor, item.getName());
        return cursor;
    }

    @Override
    public boolean isChildDocument(String parent, String child) {
        try {
            return "root".equals(parent) && ("root".equals(child) || file(child).isFile());
        } catch (FileNotFoundException missing) {
            return false;
        }
    }

    @Override
    public String createDocument(String parent, String mime, String name)
            throws FileNotFoundException {
        if (!"root".equals(parent)) throw new FileNotFoundException();
        String id = "export-" + UUID.randomUUID();
        try {
            if (!file(id).createNewFile()) throw new IOException();
            return id;
        } catch (IOException e) {
            throw new FileNotFoundException("Fixture create failed");
        }
    }

    @Override
    public void deleteDocument(String id) throws FileNotFoundException {
        File item = file(id);
        if (item.exists() && !item.delete())
            throw new FileNotFoundException("Fixture remove failed");
    }

    @Override
    public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        File item = file(id);
        if (mode.contains("w")
                && "full"
                        .equals(
                                getContext()
                                        .getSharedPreferences("export-control", 0)
                                        .getString("mode", "normal"))) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
                Thread failure =
                        new Thread(
                                () -> {
                                    try (InputStream input =
                                            new ParcelFileDescriptor.AutoCloseInputStream(
                                                    pipe[0])) {
                                        input.read(new byte[1024]);
                                        pipe[0].closeWithError("No space left on device");
                                    } catch (IOException ignored) {
                                        try {
                                            pipe[0].close();
                                        } catch (IOException alsoIgnored) {
                                        }
                                    }
                                },
                                "full-storage-fixture");
                failure.setDaemon(true);
                failure.start();
                return pipe[1];
            } catch (IOException error) {
                throw new FileNotFoundException("No space left on device");
            }
        }
        return ParcelFileDescriptor.open(
                item,
                mode.contains("w")
                        ? ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE
                                | ParcelFileDescriptor.MODE_WRITE_ONLY
                        : ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
