package org.visualvoicemail.app;

import android.content.Context;
import com.k2fsa.sherpa.onnx.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Process-scoped, single-job local speech recognition. Never sends audio or text to a server. */
final class Transcription {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final String BASE="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/d026532c022fa99fd789d6b32446a1df7b6bfc43/";
    private static final String[] NAMES={"tiny.en-encoder.int8.onnx","tiny.en-decoder.int8.onnx","tiny.en-tokens.txt","silero_vad.onnx"};
    private static final long[] SIZES={12937772,89853865,835554,643854};
    private static final String[] HASHES={"0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6","06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527","306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930","9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"};
    static volatile String job="", status="", failure="";
    static volatile boolean busy, downloading, cancelled;
    static volatile int progress;
    private static File directory(Context c) { File f=new File(c.getFilesDir(),"speech-model"); f.mkdirs(); return f; }
    static boolean ready(Context c) {
        File root=directory(c);
        for(int i=0;i<NAMES.length;i++) if(new File(root,NAMES[i]).length()!=SIZES[i]) return false;
        return new File(root,"verified-v1").exists();
    }
    static String key(String id) {
        try { return "transcript-"+hex(MessageDigest.getInstance("SHA-256").digest(id.getBytes("UTF-8")))+".enc"; }
        catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
    static Transcript cached(Context c,String id) { return Transcript.parse(new PrivateStore(c).get(key(id))); }
    static synchronized void download(Context context) {
        if(busy) return;
        final Context c=context.getApplicationContext(); busy=true; downloading=true; cancelled=false; job="model"; failure=""; progress=0; status="Downloading English model…";
        WORK.execute(()->{
            File temporary=null;
            try {
                File root=directory(c);
                if(root.getUsableSpace()<230000000) throw new java.io.IOException("storage");
                long completed=0, total=0; for(long size:SIZES) total+=size;
                for(int i=0;i<NAMES.length;i++) {
                    File target=new File(root,NAMES[i]);
                    if(target.length()==SIZES[i] && digest(target).equals(HASHES[i])) { completed+=SIZES[i]; continue; }
                    URL url=new URL(i==3?"https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx":BASE+NAMES[i]);
                    HttpURLConnection connection=(HttpURLConnection)url.openConnection();
                    connection.setConnectTimeout(20000); connection.setReadTimeout(20000);
                    temporary=new File(root,NAMES[i]+".part"); long received=0; MessageDigest hash=MessageDigest.getInstance("SHA-256");
                    try {
                        if(connection.getResponseCode()!=200) throw new java.io.IOException("download");
                        try(java.io.InputStream in=connection.getInputStream(); FileOutputStream out=new FileOutputStream(temporary)) {
                            byte[] buffer=new byte[65536]; int n;
                            while((n=in.read(buffer))!=-1) {
                                if(cancelled) throw new java.io.InterruptedIOException();
                                received+=n; if(received>SIZES[i]) throw new java.io.IOException("size");
                                out.write(buffer,0,n); hash.update(buffer,0,n);
                                progress=(int)((completed+received)*100/total); status="Downloading English model · "+progress+"%";
                            }
                            out.getFD().sync();
                        }
                        if(received!=SIZES[i] || !hex(hash.digest()).equals(HASHES[i])) throw new java.io.IOException("verification");
                        if(!temporary.renameTo(target)) throw new java.io.IOException("storage");
                        temporary=null; completed+=received;
                    } finally { connection.disconnect(); }
                }
                if(cancelled) throw new java.io.InterruptedIOException();
                try(FileOutputStream marker=new FileOutputStream(new File(root,"verified-v1"))) { marker.write(1); }
                status="English model ready";
            } catch(Exception e) { failure=cancelled?"Download cancelled. You can resume when ready.":"Model download didn't finish. Check your connection and free space, then retry."; }
            finally { if(temporary!=null) temporary.delete(); downloading=false; busy=false; }
        });
    }
    static synchronized void generate(Context context,JSONObject message) {
        if(busy || !ready(context)) return;
        final Context c=context.getApplicationContext(); busy=true; downloading=false; cancelled=false;
        job=message.optString("objectid"); final String id=job; failure=""; status="Opening recording…";
        WORK.execute(()->{
            OfflineRecognizer recognizer=null; Vad vad=null;
            try {
                File audio=CarrierClient.get(c).audio(message);
                if(cancelled) throw new java.io.InterruptedIOException();
                status="Reading audio on this phone…"; float[] samples=AudioSamples.read(audio);
                File root=directory(c);
                SileroVadModelConfig silero=new SileroVadModelConfig(); silero.setModel(new File(root,NAMES[3]).getAbsolutePath());
                silero.setMinSilenceDuration(.45f); silero.setMinSpeechDuration(.2f); silero.setMaxSpeechDuration(12f);
                VadModelConfig vc=new VadModelConfig(); vc.setSileroVadModelConfig(silero); vad=new Vad(null,vc);
                List<int[]> ranges=new ArrayList<>();
                for(int offset=0;offset<samples.length;offset+=512) {
                    if(cancelled) throw new java.io.InterruptedIOException();
                    vad.acceptWaveform(Arrays.copyOfRange(samples,offset,Math.min(offset+512,samples.length)));
                    drain(vad,ranges,samples.length);
                }
                vad.flush(); drain(vad,ranges,samples.length); vad.release(); vad=null;
                Transcript result=new Transcript();
                if(!ranges.isEmpty()) {
                    OfflineWhisperModelConfig whisper=new OfflineWhisperModelConfig();
                    whisper.setEncoder(new File(root,NAMES[0]).getAbsolutePath()); whisper.setDecoder(new File(root,NAMES[1]).getAbsolutePath());
                    OfflineModelConfig model=new OfflineModelConfig(); model.setWhisper(whisper); model.setTokens(new File(root,NAMES[2]).getAbsolutePath()); model.setNumThreads(4);
                    OfflineRecognizerConfig config=new OfflineRecognizerConfig(); config.setModelConfig(model); recognizer=new OfflineRecognizer(null,config);
                    for(int i=0;i<ranges.size();i++) {
                        if(cancelled) throw new java.io.InterruptedIOException();
                        status="Transcribing on this phone · "+(i+1)+" of "+ranges.size();
                        int[] range=ranges.get(i); OfflineStream stream=recognizer.createStream();
                        try {
                            stream.acceptWaveform(Arrays.copyOfRange(samples,range[0],range[1]),16000);
                            recognizer.decode(stream); String text=recognizer.getResult(stream).getText().trim();
                            if(!text.isEmpty()) result.segments.add(new Transcript.Segment(range[0]/16,range[1]/16,text));
                        } finally { stream.release(); }
                    }
                }
                if(cancelled) throw new java.io.InterruptedIOException();
                if(!new PrivateStore(c).put(key(id),result.json())) throw new java.io.IOException("storage");
                status=result.segments.isEmpty()?"No speech detected":"Transcript saved on this phone";
            } catch(Exception | LinkageError e) { failure=cancelled?"Transcription cancelled. Your recording is unchanged.":"Couldn't transcribe this recording. You can still listen, or try again."; }
            finally { if(vad!=null) vad.release(); if(recognizer!=null) recognizer.release(); busy=false; }
        });
    }
    private static void drain(Vad vad,List<int[]> ranges,int count) {
        while(!vad.empty()) {
            SpeechSegment s=vad.front(); int start=Math.max(0,(int)s.getStart()-2400),end=Math.min(count,(int)s.getStart()+s.getSamples().length+1600);
            if(end>start) {
                if(!ranges.isEmpty()) { int[] last=ranges.get(ranges.size()-1); start=Math.max(start,last[1]); }
                if(end>start) ranges.add(new int[]{start,end});
            }
            vad.pop();
        }
    }
    static void cancel() { cancelled=true; }
    static synchronized boolean clear(Context c,boolean model) {
        if(busy)return false;boolean okay=true;
        File[] files=model?directory(c).listFiles():c.getFilesDir().listFiles();
        if(files==null)return false;
        for(File file:files) {
            boolean selected=model?file.getName().equals("verified-v1"):file.getName().matches("transcript-[0-9a-f]{64}\\.enc(?:\\.bak|\\.new)?");
            if(model)for(String name:NAMES)if(file.getName().equals(name)||file.getName().equals(name+".part"))selected=true;
            if(selected&&!file.delete())okay=false;
        }
        return okay;
    }
    private static String digest(File file) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(file)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) hash.update(b,0,n); }
        return hex(hash.digest());
    }
    private static String hex(byte[] bytes) { StringBuilder s=new StringBuilder(); for(byte b:bytes) s.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return s.toString(); }
}
