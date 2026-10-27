package org.visualvoicemail.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract.PhoneLookup;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** Looks up only inbox caller numbers in the local provider. Names stay in memory. */
final class ContactNames {
    static String number(JSONObject message) {
        String number = message.optString("returnnumber", "").replaceFirst("(?i)^tel:", "").trim();
        return "null".equalsIgnoreCase(number) ? "" : number;
    }

    static Map<String, String> lookup(Context context, List<JSONObject> inbox, CancellationSignal cancel) {
        Map<String, String> names = new HashMap<>();
        Set<String> requested = new HashSet<>();
        for (JSONObject message : inbox) {
            cancel.throwIfCanceled();
            String number = number(message);
            if (number.isEmpty() || !requested.add(number)) continue;
            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{PhoneLookup.DISPLAY_NAME}, null, null, null, cancel)) {
                if (cursor == null) throw new IllegalStateException("Contacts provider unavailable");
                if (cursor.moveToFirst()) {
                    String name = cursor.getString(0);
                    if (name != null && !name.trim().isEmpty()) names.put(number, name.trim());
                }
            }
        }
        return names;
    }
}
