"""Render a native-interaction product demo. Capture source footage with ProductDemoCapture first."""
from __future__ import annotations
import argparse, math, os, shutil, subprocess, wave, json
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
WORK=HERE/'work'
EXPORTS=HERE/'exports'
WIDTH,HEIGHT,FPS,DURATION=1080,1350,30,44
CUTS=(0,3,9,15,23,29,34,40,44)
WHITE=(242,247,245); MINT=(158,230,199); MUTED=(162,180,171)
FFMPEG=shutil.which('ffmpeg')
if not FFMPEG: raise RuntimeError('FFmpeg is required')
@lru_cache(None)
def font(size,bold=False):
    override=os.environ.get('PROMO_FONT_BOLD' if bold else 'PROMO_FONT_REGULAR')
    candidates=[override,'C:/Windows/Fonts/seguisb.ttf' if bold else 'C:/Windows/Fonts/segoeui.ttf',
      '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf' if bold else '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf']
    for path in candidates:
        if path and Path(path).is_file():return ImageFont.truetype(path,size)
    raise RuntimeError('Set PROMO_FONT_REGULAR and PROMO_FONT_BOLD to installed fonts')
def text(im,value,xy,size,color=WHITE,bold=False,center=False):
    draw=ImageDraw.Draw(im);face=font(size,bold)
    for row,line in enumerate(value.split('\n')):
        box=draw.textbbox((0,0),line,font=face); x=xy[0]-(box[2]/2 if center else 0); y=xy[1]+row*size*1.18
        if x<45 or x+box[2]>WIDTH-45:raise ValueError('Copy exceeds safe area: '+line)
        draw.text((round(x),round(y)),line,font=face,fill=color)
def ease(t):
    t=max(0,min(1,t));return t*t*(3-2*t)
def background():
    y,x=np.mgrid[:HEIGHT,:WIDTH]
    glow=np.exp(-(((x-850)/900)**2+((y-420)/750)**2))
    a=np.empty((HEIGHT,WIDTH,3),dtype=np.float32)
    for c,base in enumerate([7,12,11]):a[:,:,c]=base+glow*[4,12,8][c]
    return Image.fromarray(a.astype(np.uint8)).convert('RGBA')
BG=background()
ICON=Image.open(ROOT/'docs/branding/app-icon.png').convert('RGBA')
@lru_cache(None)
def icon(width):return ICON.resize((width,width),Image.Resampling.LANCZOS)
@lru_cache(None)
def mask(width,height,radius):
    im=Image.new('L',(width,height));ImageDraw.Draw(im).rounded_rectangle((0,0,width-1,height-1),radius,fill=255);return im
class Clip:
    def __init__(self,name):self.path=WORK/(name+'.mp4');self.process=None;self.index=-1;self.last=None
    def close(self):
        if self.process:self.process.stdout.close();self.process.terminate();self.process.wait();self.process=None
    def get(self,seconds):
        target=max(0,int(seconds*FPS))
        if target<self.index:self.close();self.index=-1;self.last=None
        if self.process is None:
            self.process=subprocess.Popen([FFMPEG,'-v','error','-i',str(self.path),'-map','0:v:0','-vf','fps=30','-f','rawvideo','-pix_fmt','rgb24','pipe:1'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
        while self.index<target:
            data=self.process.stdout.read(1080*2340*3)
            if len(data)!=1080*2340*3:break
            self.last=Image.frombytes('RGB',(1080,2340),data);self.index+=1
        if self.last is None:raise RuntimeError('No native frames in '+str(self.path))
        return self.last
CLIPS={name:Clip(name) for name in ['inbox','search','playback','quality','selection']}
THEMES=[Image.open(WORK/('theme-'+str(i)+'.png')).convert('RGB') for i in [1,2,3]]
def panel(im,source,xy,width,crop=None,radius=30):
    if crop:source=source.crop(crop)
    height=round(source.height*width/source.width)
    source=source.resize((width,height),Image.Resampling.LANCZOS).convert('RGBA')
    source.putalpha(mask(width,height,radius));im.alpha_composite(source,tuple(round(v) for v in xy))
    x,y=xy;ImageDraw.Draw(im).rounded_rectangle((round(x),round(y),round(x+width-1),round(y+height-1)),radius,outline=(62,76,68,150),width=1)
def chip(im,value,x,y,width):
    ImageDraw.Draw(im).rounded_rectangle((x,y,x+width,y+48),24,fill=(31,66,51));text(im,value,(x+width/2,y+8),22,MINT,True,True)
def common():
    im=BG.copy();im.alpha_composite(icon(42),(72,62));text(im,'VOICE MAIL',(130,67),22,WHITE,True)
    text(im,'ENGINEERING DEMO',(780,72),18,MUTED)
    text(im,'Development preview · Synthetic messages',(72,1285),20,MUTED)
    return im
def scene(index,t):
    im=common();entry=ease(t/.55);shift=(1-entry)*20
    if index==0:
        im.alpha_composite(icon(116),(76,236))
        text(im,'Voice\nMail',(72,402+shift),108,WHITE,True)
        text(im,'Visual voicemail\nfor Android.',(78,705+shift),34,MUTED)
        chip(im,'Mint Mobile preview',78,853,280)
        panel(im,CLIPS['inbox'].get(0),(545,210+shift),455)
    elif index==1:
        panel(im,CLIPS['inbox'].get(t),(72,177+shift),476)
        text(im,'Your inbox.\nYour dialer.',(608,370+shift),53,WHITE,True)
        text(im,'Use your existing\nMint mailbox.',(610,553+shift),29,MUTED)
        chip(im,'No forwarding setup',607,692,313)
        text(im,'Swipe between\nAll, Unread, and Saved.',(610,818),27,MUTED)
    elif index==2:
        text(im,'Find the message.',(72,154+shift),66,WHITE,True)
        text(im,'Search contacts and saved transcript text.',(74,250+shift),29,MUTED)
        focus=ease((t-1.6)/.65)
        width=round(828-68*focus);top=round(145+305*focus);bottom=round(1325+440*focus)
        panel(im,CLIPS['search'].get(t),((WIDTH-width)/2,342-22*focus+shift),width,(0,top,1080,bottom),24)
    elif index==3:
        text(im,'Tap a phrase. Hear it.',(72,154+shift),64,WHITE,True)
        text(im,'Play from the text, or find a word inside it.',(74,250+shift),29,MUTED)
        top=round(300+350*ease((t-3.0)/.8))
        panel(im,CLIPS['playback'].get(t+1),(126,340+shift),828,(0,top,1080,top+1180),24)
        text(im,'Phrase timing is approximate.',(540,1250),19,MUTED,False,True)
    elif index==4:
        text(im,'From audio to text.',(72,151+shift),65,WHITE,True)
        text(im,'Whisper runs on the phone.',(74,248+shift),30,MUTED)
        panel(im,CLIPS['quality'].get(t),(650,365),354,(0,0,1080,2340),24)
        labels=[('Carrier mailbox','Mint Mobile'),('Private app storage','Audio + encrypted metadata'),('On-device Whisper','Tiny · Base · Large v3'),('Saved transcript','Searchable original / English text')]
        draw=ImageDraw.Draw(im)
        draw.rounded_rectangle((58,506,595,1150),26,outline=(54,82,68),width=1)
        text(im,'ON YOUR PHONE',(88,519),18,MINT,True)
        for i,(title,detail) in enumerate(labels):
            y=357+i*193;active=ease((t-i*.55)/.55)
            draw.rounded_rectangle((82,y,568,y+122),20,fill=(24+int(active*6),35+int(active*9),30+int(active*6)))
            text(im,title,(105,y+17),29,WHITE,True);text(im,detail,(105,y+66),22,MUTED)
            if i<3:
                draw.line((325,y+128,325,y+187),fill=(58,89,73),width=3)
                v=((t*.7-i*.24)%1);dot=y+132+v*47
                draw.ellipse((321,dot-4,329,dot+4),fill=MINT)
        text(im,'Choose a model before downloading.',(540,1215),24,MUTED,False,True)
    elif index==5:
        panel(im,CLIPS['selection'].get(t),(72,177+shift),476)
        text(im,'Handle several\nmessages at once.',(608,350+shift),43,WHITE,True)
        text(im,'Transcribe. Mark read.\nDelete from the carrier\nwith a retained copy.',(610,520+shift),27,MUTED)
        chip(im,'Native multi-select',610,726,292)
    elif index==6:
        text(im,'Fits your phone.',(72,154+shift),68,WHITE,True)
        text(im,'Light, Dark, and true-black AMOLED.',(74,250+shift),30,MUTED)
        for i,(source,label) in enumerate(zip(THEMES,['Light','Dark','AMOLED'])):
            x=63+i*326;delay=ease((t-i*.12)/.7)
            panel(im,source,(x,425+(1-delay)*45),302,(0,0,1440,2670),24)
            text(im,label,(x+151,1040),28,WHITE,False,True)
        text(im,'Readable text. Comfortable touch targets.',(540,1173),27,MUTED,False,True)
    else:
        im.alpha_composite(icon(148),(466,304))
        text(im,'Voice Mail',(540,526+shift),98,WHITE,True,True)
        text(im,'Built for Android.\nTested on Mint Mobile.',(540,689+shift),36,MUTED,False,True)
        chip(im,'Version 0.33 preview',378,905,324)
        text(im,'A work in progress, ready for feedback.',(540,1120),29,WHITE,False,True)
    return im

def frame_at(seconds):
    index=next((i for i,end in enumerate(CUTS[1:]) if seconds<end),len(CUTS)-2)
    elapsed=seconds-CUTS[index];im=scene(index,elapsed)
    # Brief cross-dissolves join actual native footage and the engineering interlude.
    if index and elapsed<.22:
        previous=scene(index-1,CUTS[index]-CUTS[index-1]-.01)
        im=Image.blend(previous,im,ease(elapsed/.22))
    return im.convert('RGB')

def music(path):
    """Original 112 BPM pad/pluck arrangement, synthesized without audio samples."""
    rate = 48000
    size = DURATION * rate
    track = np.zeros((size, 2), dtype=np.float64)
    rng = np.random.default_rng(24)
    beat = 60 / 112

    def frequency(note):
        return 440 * 2 ** ((note - 69) / 12)

    def add(when, sound, gain, pan=0):
        offset = round(when * rate)
        length = min(len(sound), size - offset)
        if length <= 0:
            return
        track[offset:offset + length, 0] += sound[:length] * gain * math.sqrt((1 - pan) / 2)
        track[offset:offset + length, 1] += sound[:length] * gain * math.sqrt((1 + pan) / 2)

    chords = ((48, 55, 59, 64), (45, 52, 55, 60), (41, 48, 52, 57), (43, 50, 55, 59))
    melodies = ((72, 76, 79, 74, 76, 72), (72, 76, 79, 76, 72, 69), (69, 72, 76, 72, 69, 67), (71, 74, 79, 74, 71, 67))
    for bar in range(math.ceil(DURATION / (beat * 4))):
        chord = chords[bar % 4]
        when = bar * beat * 4
        t = np.arange(round(beat * 5 * rate)) / rate
        envelope = np.minimum(t / .35, 1) * np.minimum((t[-1] - t) / .85, 1)
        pad = sum(np.sin(2 * np.pi * frequency(note) * t) + .18 * np.sin(4 * np.pi * frequency(note) * t)
                  for note in chord) / len(chord)
        add(when, pad * envelope, .105, -.18 if bar % 2 else .18)
        for count, position in enumerate((0, .75, 1.5, 2.25, 3, 3.5)):
            t = np.arange(round(.9 * rate)) / rate
            pitch = frequency(melodies[bar % 4][count])
            pluck = (np.sin(2 * np.pi * pitch * t) + .22 * np.sin(2 * np.pi * pitch * 2 * t))
            pluck *= (1 - np.exp(-t * 650)) * np.exp(-t * 7)
            add(when + position * beat, pluck, .074, (-.35, .25, -.1, .35, -.25, .1)[count])
        for position in (0, 2):
            t = np.arange(round(.18 * rate)) / rate
            kick = np.sin(2 * np.pi * (58 * t + 28 * (1 - np.exp(-t * 25)) / 25)) * np.exp(-t * 24)
            add(when + position * beat, kick, .065)
        for position in (1, 3):
            t = np.arange(round(.055 * rate)) / rate
            noise = rng.normal(0, 1, len(t))
            tick = np.diff(noise, prepend=noise[0]) * np.exp(-t * 100)
            add(when + position * beat, tick, .008, .35)
    # Sparse transition accents. No voicemail recording or system notification sound.
    for index, when in enumerate(CUTS[1:-1]):
        t = np.arange(round(.16 * rate)) / rate
        accent = np.sin(2 * np.pi * (620 + index * 35) * t) * np.exp(-t * 35) * np.minimum(t * 1000, 1)
        add(when, accent, .025)
    for delay, gain in ((.16, .11), (.29, .06)):
        offset = round(delay * rate)
        track[offset:] += track[:-offset, ::-1] * gain
    time = np.arange(size) / rate
    fade = np.minimum(time / .45, 1) * np.minimum(np.maximum(DURATION - time, 0) / 1.8, 1)
    track *= fade[:, None]
    track = np.tanh(track * 1.4)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(2)
        output.setsampwidth(2)
        output.setframerate(rate)
        output.writeframes((np.clip(track, -1, 1) * 32767).astype("<i2").tobytes())


def stamp(t):
    ms=round(t*1000);return f"00:{ms//60000:02d}:{ms//1000%60:02d},{ms%1000:03d}"
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--preview',action='store_true');args=parser.parse_args()
    EXPORTS.mkdir(exist_ok=True);WORK.mkdir(exist_ok=True)
    captions=[('Voice Mail. Visual voicemail for Android.','Tested on Mint Mobile.'),('Use your existing mailbox and your chosen dialer.','Swipe between All, Unread, and Saved.'),('Search contacts and saved transcript text.',''),('Tap a phrase to play its source audio.','Search inside the transcript. Timing is approximate.'),('Audio stays in private app storage. Metadata is encrypted.','Whisper transcribes on the phone: Tiny, Base, or Large v3.'),('Select several messages for transcription or read status.','Carrier deletion retains a verified Saved copy.'),('Light, Dark, and true-black AMOLED.',''),('Voice Mail 0.33. Android and Mint Mobile preview.','Synthetic demo messages. Feedback is welcome.')]
    (EXPORTS/'voice-mail-product-demo.srt').write_text('\n'.join(f'{i+1}\n{stamp(CUTS[i])} --> {stamp(CUTS[i+1])}\n'+a+('\n'+b if b else '')+'\n' for i,(a,b) in enumerate(captions)),encoding='utf-8')
    if args.preview:
        for i,start in enumerate(CUTS[:-1]):frame_at(start+1.2).save(WORK/f'product-scene-{i+1}.png')
        frame_at(1.4).save(EXPORTS/'voice-mail-product-demo-cover.png')
        for c in CLIPS.values():c.close()
        print('Product storyboard ready');return
    music(WORK/'product-soundtrack.wav')
    target=EXPORTS/'voice-mail-product-demo.mp4'
    command=[FFMPEG,'-y','-hide_banner','-loglevel','warning','-f','rawvideo','-pixel_format','rgb24','-video_size',f'{WIDTH}x{HEIGHT}','-framerate',str(FPS),'-i','pipe:0','-i',str(WORK/'product-soundtrack.wav'),'-map','0:v:0','-map','1:a:0','-map_metadata','-1','-c:v','libx264','-preset','medium','-crf','19','-maxrate','10M','-bufsize','20M','-pix_fmt','yuv420p','-r',str(FPS),'-vf','scale=in_range=full:out_range=tv:out_color_matrix=bt709,setsar=1','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-color_range','tv','-profile:v','high','-level:v','4.1','-c:a','aac','-b:a','160k','-ar','48000','-af','loudnorm=I=-18:TP=-2:LRA=7','-movflags','+faststart','-shortest','-metadata','title=Voice Mail - native engineering demo',str(target)]
    with (WORK/'product-ffmpeg.log').open('w') as log:
        p=subprocess.Popen(command,stdin=subprocess.PIPE,stderr=log)
        try:
            for n in range(DURATION*FPS):
                frame=frame_at(n/FPS)
                if n==round(1.4*FPS):frame.save(EXPORTS/'voice-mail-product-demo-cover.png')
                p.stdin.write(frame.tobytes())
                if n%(FPS*4)==0:print(f'Rendered {n/FPS:.0f}/{DURATION}s',flush=True)
            p.stdin.close()
            if p.wait():raise RuntimeError('Encoding failed; see work/product-ffmpeg.log')
        except BaseException:
            p.kill();p.wait();raise
        finally:
            for c in CLIPS.values():c.close()
    print(target,flush=True)
if __name__=='__main__':main()
