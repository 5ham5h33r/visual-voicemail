package org.visualvoicemail.checks;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;

/** Grants access only to this test provider's public fixture tree, never a real storage folder. */
public final class ExportGrantReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = intent.getStringExtra("mode");
        if (!"normal".equals(mode) && !"full".equals(mode) && !"read".equals(mode)) return;
        context.getSharedPreferences("export-control", 0).edit().putString("mode", mode).commit();
        Uri tree = DocumentsContract.buildTreeDocumentUri(ExportFixtureProvider.AUTHORITY, "root");
        context.revokeUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        int access =
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
        if (!"read".equals(mode))
            access |=
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        context.grantUriPermission("org.visualvoicemail.app", tree, access);
    }
}
