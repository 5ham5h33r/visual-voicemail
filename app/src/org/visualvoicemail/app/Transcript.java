package org.visualvoicemail.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** A phrase's anchor is a real sample offset returned by voice activity detection. */
final class Transcript {
    static final class Segment {
        final int start, end; final String text;
        Segment(int start,int end,String text) { this.start=start; this.end=end; this.text=text; }
    }
    final List<Segment> segments=new ArrayList<>();
    String text() { StringBuilder out=new StringBuilder(); for(Segment s:segments) { if(out.length()>0) out.append(' '); out.append(s.text); } return out.toString(); }
    JSONObject json() throws Exception {
        JSONArray items=new JSONArray();
        for(Segment s:segments) items.put(new JSONObject().put("start",s.start).put("end",s.end).put("text",s.text));
        return new JSONObject().put("version",1).put("engine","whisper-tiny.en+silero-vad").put("segments",items);
    }
    static Transcript parse(JSONObject value) {
        if(value==null || value.optInt("version")!=1) return null;
        Transcript out=new Transcript(); JSONArray items=value.optJSONArray("segments"); if(items==null) return null;
        for(int i=0;i<items.length();i++) {
            JSONObject s=items.optJSONObject(i); if(s==null) continue;
            int start=s.optInt("start",-1),end=s.optInt("end",-1); String text=s.optString("text","").trim();
            if(text.isEmpty()) continue;
            // Invalid anchors remain readable but are never used for seeking.
            out.segments.add(new Segment(start>=0&&end>start?start:-1,end,text));
        }
        return out;
    }
    boolean matches(String query) {
        String needle=query.trim().toLowerCase(Locale.ROOT), all=text().toLowerCase(Locale.ROOT);
        if(needle.isEmpty()) return true;
        for(String term:needle.split("\\s+")) if(!all.contains(term)) return false;
        return true;
    }
}
