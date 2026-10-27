package org.visualvoicemail.app;

import java.util.List;

public final class TranscriptSearchTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Transcript search check "+checks);}
    public static void main(String[] args){
        check(TranscriptSearch.find("Call me","   ").isEmpty());
        check(TranscriptSearch.find("Call me","tomorrow").isEmpty());
        List<int[]> repeated=TranscriptSearch.find("Call, call, CALL.","call");
        check(repeated.size()==3);check(repeated.get(2)[0]==12&&repeated.get(2)[1]==16);
        check(TranscriptSearch.find("I'll call you tomorrow.","CALL YOU").size()==1);
        check(TranscriptSearch.find("Meet at 10.30, please.","10.30").size()==1);
        check(TranscriptSearch.find("Meet at 10x30, please.","10.30").isEmpty());
        check(TranscriptSearch.find("CAFÉ mañana","café").size()==1);
        check(TranscriptSearch.find("你好，明天见","明天").get(0)[0]==3);
        check(TranscriptSearch.find("😀 call me","call").get(0)[0]==3);
        check(TranscriptSearch.find("First phrase. Second phrase.","phrase. second").size()==1);
        check(TranscriptSearch.find("aaaa","aa").size()==2);
        check(TranscriptSearch.find("word"," word ").size()==1);
        check(TranscriptSearch.find("","anything").isEmpty());
        System.out.println("Transcript search: "+checks+" checks passed.");
    }
}
