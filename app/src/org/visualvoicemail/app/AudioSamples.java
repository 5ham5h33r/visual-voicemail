package org.visualvoicemail.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Decode carrier audio locally, preserving its timeline, then resample to 16 kHz mono. */
final class AudioSamples {
    static float[] read(File file) throws Exception {
        MediaExtractor extractor = new MediaExtractor(); MediaCodec codec = null;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            int track = -1;
            for (int i=0;i<extractor.getTrackCount();i++)
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/")) { track=i; break; }
            if(track<0) throw new IllegalArgumentException("No audio track");
            extractor.selectTrack(track); MediaFormat format=extractor.getTrackFormat(track);
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0); codec.start();
            float[] samples=new float[rate*30]; int used=0; boolean inputDone=false, done=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long deadline=android.os.SystemClock.elapsedRealtime()+120000;
            int encoding=android.media.AudioFormat.ENCODING_PCM_16BIT;
            while(!done) {
                if(android.os.SystemClock.elapsedRealtime()>deadline || Thread.currentThread().isInterrupted()) throw new java.io.IOException("Decode interrupted");
                if(!inputDone) {
                    int index=codec.dequeueInputBuffer(10000);
                    if(index>=0) {
                        ByteBuffer buffer=codec.getInputBuffer(index); int size=extractor.readSampleData(buffer,0);
                        if(size<0) { inputDone=true; codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); }
                        else { codec.queueInputBuffer(index,0,size,extractor.getSampleTime(),0); extractor.advance(); }
                    }
                }
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out=codec.getOutputFormat(); rate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding=out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if(index>=0) {
                    ByteBuffer buffer=codec.getOutputBuffer(index).order(ByteOrder.nativeOrder());
                    buffer.position(info.offset); buffer.limit(info.offset+info.size);
                    int bytes=encoding==android.media.AudioFormat.ENCODING_PCM_FLOAT?4:2;
                    if(encoding!=android.media.AudioFormat.ENCODING_PCM_FLOAT && encoding!=android.media.AudioFormat.ENCODING_PCM_16BIT) throw new java.io.IOException("Unsupported PCM");
                    int frames=info.size/bytes/channels;
                    if(used+frames>rate*600) throw new java.io.IOException("Recording exceeds ten minutes");
                    if(used+frames>samples.length) samples=Arrays.copyOf(samples,Math.max(used+frames,samples.length*2));
                    for(int frame=0;frame<frames;frame++) {
                        float value=0;
                        for(int ch=0;ch<channels;ch++) value+=bytes==4?buffer.getFloat():buffer.getShort()/32768f;
                        samples[used++]=value/channels;
                    }
                    done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0; codec.releaseOutputBuffer(index,false);
                }
            }
            if(used==0) return new float[0];
            float[] result=new float[(int)((long)used*16000/rate)];
            for(int i=0;i<result.length;i++) {
                double position=(double)i*rate/16000; int a=(int)position, b=Math.min(a+1,used-1);
                result[i]=(float)(samples[a]+(samples[b]-samples[a])*(position-a));
            }
            return result;
        } finally { if(codec!=null) { try { codec.stop(); } catch(Exception ignored) { } codec.release(); } extractor.release(); }
    }
}
