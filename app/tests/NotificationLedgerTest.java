package org.visualvoicemail.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NotificationLedgerTest {
    private static int checks;
    private static void expect(boolean condition,String detail) { checks++; if(!condition)throw new AssertionError(detail); }
    private static Set<String> ids(String... ids) { return new LinkedHashSet<>(Arrays.asList(ids)); }
    private static NotificationLedger.Message message(String id,boolean unread) { return new NotificationLedger.Message(id,unread); }
    private static NotificationLedger.Result observe(Set<String> known,Set<String> pending,boolean initialized,boolean eligible,NotificationLedger.Message... messages) {
        return NotificationLedger.observe(known,pending,Arrays.asList(messages),initialized,eligible);
    }
    public static void main(String[] args) {
        NotificationLedger.Result initial=observe(ids(),ids(),false,true,message("old",true));
        expect(!initial.hasNew && initial.pending.isEmpty() && initial.known.contains("old"),"First sync establishes a silent baseline");
        NotificationLedger.Result arrived=observe(initial.known,ids(),true,true,message("old",true),message("new",true));
        expect(arrived.hasNew && arrived.pending.equals(ids("new")),"New unread voicemail alerts without replaying existing unread");
        NotificationLedger.Result repeat=observe(arrived.known,arrived.pending,true,true,message("old",true),message("new",true));
        expect(!repeat.hasNew && repeat.pending.equals(ids("new")),"Repeated sync preserves the alert without alerting again");
        NotificationLedger.Result dismissed=observe(arrived.known,ids(),true,true,message("new",true));
        expect(!dismissed.hasNew && dismissed.pending.isEmpty(),"Dismissed voicemail is not notified again");
        NotificationLedger.Result read=observe(arrived.known,arrived.pending,true,true,message("new",false));
        expect(read.pending.isEmpty() && !read.hasNew,"Reading a pending message removes its alert");
        expect(observe(arrived.known,arrived.pending,true,true).pending.isEmpty(),"Removed mailbox messages lose pending alerts");
        expect(!observe(ids("read"),ids(),true,true,message("read",true)).hasNew,"Marking an existing message unread is not an arrival");
        NotificationLedger.Result alreadyRead=observe(ids(),ids(),true,true,message("read",false));
        expect(!alreadyRead.hasNew && alreadyRead.known.contains("read"),"New messages already read elsewhere stay quiet");
        NotificationLedger.Result disabled=observe(arrived.known,arrived.pending,true,false,message("later",true));
        expect(disabled.pending.isEmpty() && disabled.known.contains("later"),"Disabled or blocked notifications still advance the baseline");
        expect(!observe(disabled.known,ids(),true,true,message("later",true)).hasNew,"Enabling does not replay messages discovered while disabled");
        NotificationLedger.Result batch=observe(ids(),ids(),true,true,message("a",true),message("b",true),message("a",true));
        expect(batch.hasNew && batch.pending.size()==2,"Batch arrivals are deduplicated");
        expect(observe(ids(),ids(),true,true,message("",true),message(null,true)).pending.isEmpty(),"Missing message IDs never notify");
        Set<String> many=ids(); for(int i=0;i<NotificationLedger.MAX_KNOWN+10;i++)many.add("id"+i);
        NotificationLedger.Result bounded=observe(many,ids(),true,true,message("id0",true));
        expect(bounded.known.size()==NotificationLedger.MAX_KNOWN && bounded.known.contains("id0"),"Bounded history retains IDs still in the mailbox");
        expect(!bounded.hasNew,"Refreshing a retained ID does not fabricate an arrival");
        expect(observe(Collections.emptySet(),ids(),true,true,message("same",true),message("same",false)).pending.isEmpty(),"The final duplicate state wins");
        System.out.println("Passed "+checks+" notification ledger checks.");
    }
}
