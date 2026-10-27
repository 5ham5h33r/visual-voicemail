package org.visualvoicemail.app;

import android.content.Context;
import org.json.JSONObject;

/** Local, encrypted, reversible deletion marks. Never sends a carrier DELETE. */
final class MessageMarks {
    private final PrivateStore store;
    private JSONObject marks;
    MessageMarks(Context context) {
        store = new PrivateStore(context);
        marks = store.get("deletion-marks.bin");
        if (marks == null) marks = new JSONObject();
    }
    boolean contains(String id) { return marks.optBoolean(id, false); }
    boolean set(String id, boolean marked) {
        try {
            JSONObject updated = new JSONObject(marks.toString());
            if (marked) updated.put(id, true); else updated.remove(id);
            if (!store.put("deletion-marks.bin", updated)) return false;
            marks = updated; return true;
        } catch (Exception ignored) { return false; }
    }
}
