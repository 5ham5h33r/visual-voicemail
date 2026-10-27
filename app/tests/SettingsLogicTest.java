package org.visualvoicemail.app;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Tests deletion boundaries using disposable synthetic files, never user recordings. */
public final class SettingsLogicTest {
    private static int checks;
    private static void expect(boolean ok,String detail) { checks++; if(!ok)throw new AssertionError(detail); }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("voicemail-cache-check").toFile();
        String id=new String(new char[64]).replace('\0','a');
        File audio=new File(root,id+".audio"), partial=new File(root,id+".audio.new");
        File inbox=new File(root,"inbox.bin"), session=new File(root,"session.bin"), marks=new File(root,"deletion-marks.bin");
        File unrelated=new File(root,id+".audio.backup"), nested=new File(root,"nested"); nested.mkdir();
        File child=new File(nested,id+".audio");
        File[] files={audio,partial,inbox,session,marks,unrelated,child};
        try {
            for(File file:files) Files.write(file.toPath(),"data".getBytes(StandardCharsets.UTF_8));
            long[] stats=RecordingCache.stats(root);
            expect(stats[0]==1,"Only complete recording files count as downloads");
            expect(stats[1]==8,"Cache bytes include recognized atomic-write files only");
            RecordingCache.clear(root);
            expect(!audio.exists() && !partial.exists(),"Recognized cached audio is cleared");
            expect(inbox.exists(),"Inbox metadata is preserved"); expect(session.exists(),"Mailbox session is preserved");
            expect(marks.exists(),"Deletion marks are preserved"); expect(unrelated.exists(),"Unrecognized files are preserved");
            expect(child.exists(),"Nested directories are not traversed");
            expect(RecordingCache.stats(root)[0]==0,"Cleared cache reports zero downloads");
            RecordingCache.clear(new File(root,"missing"));
            expect(true,"Missing cache is a safe no-op");
            System.out.println("Passed "+checks+" recording-cache safety checks.");
        } finally {
            for(File file:files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(nested.toPath()); Files.deleteIfExists(root.toPath());
        }
    }
}
