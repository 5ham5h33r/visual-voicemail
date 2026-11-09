"""Prepare the local native-UI assets and editable Hyperframes launch composition."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
COMP = HERE / 'composition'
ASSETS = COMP / 'assets'
WORK = ROOT / 'media/promo/work'


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def write(path, text):
    path.write_text('\n'.join(line.rstrip() for line in text.splitlines()).rstrip() + '\n',
                    encoding='utf-8', newline='\n')


def prepare():
    for folder in ('native', 'music', 'sfx', 'vendor'):
        (ASSETS / folder).mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / 'docs/branding/app-icon.png', ASSETS / 'app-icon.png')
    write(ASSETS / 'vendor/gsap.min.js',
          (COMP / 'node_modules/gsap/dist/gsap.min.js').read_text(encoding='utf-8'))
    run('ffmpeg', '-y', '-v', 'error', '-ss', '0.25', '-i', WORK / 'playback-stream.mp4',
        '-t', '11', '-vf', 'crop=644:858:38:248,fps=60', '-an', '-c:v', 'libx264',
        '-crf', '16', '-preset', 'medium', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
        ASSETS / 'native/recording.mp4')
    run('ffmpeg', '-y', '-v', 'error', '-i', ASSETS / 'native/recording.mp4',
        '-frames:v', '1', ASSETS / 'native/recording.png')
    for i in (1, 2, 3):
        run('ffmpeg', '-y', '-v', 'error', '-i', WORK / f'theme-{i}.png',
            '-vf', 'crop=644:652:38:248', '-frames:v', '1', ASSETS / f'native/theme-{i}.png')
    music = WORK / 'music/coffee-or-tea.mp3'
    expected = 'af5eb4518d456b997e322a1f89bd65a9a88134f5149d503a30735ec79b3bcf96'
    if hashlib.sha256(music.read_bytes()).hexdigest() != expected:
        raise RuntimeError('The cached soundtrack does not match its verified source hash')
    run('ffmpeg', '-y', '-v', 'error', '-i', music, '-af',
        'atrim=start=12:duration=22,asetpts=PTS-STARTPTS,loudnorm=I=-19:TP=-2:LRA=7',
        '-ar', '48000', '-c:a', 'pcm_s16le', ASSETS / 'music/bed.wav')
    skill = Path.home() / '.codex/plugins/cache/brag/brag/0.5.0/skills/brag'
    shutil.copyfile(skill / 'assets/sfx/impact/impactSoft_medium_001.ogg', ASSETS / 'sfx/soft.ogg')
    extractor = ROOT / 'research/tools/hyperframes-source/skills/media-use/audio/scripts/extract-audio-data.py'
    import sys
    run(sys.executable, extractor, ASSETS / 'music/bed.wav', '--fps', '60', '--bands', '8',
        '-o', ASSETS / 'audio-data.json')
    audio = json.loads((ASSETS / 'audio-data.json').read_text(encoding='utf-8'))
    write(ASSETS / 'audio-data.js', 'window.LAUNCH_AUDIO=' + json.dumps(audio) + ';\n')


def compose():
    variables = [
        {'id': 'color-ground', 'type': 'color', 'label': 'Background', 'default': '#101214'},
        {'id': 'color-ink', 'type': 'color', 'label': 'Text', 'default': '#F3F5F7'},
        {'id': 'color-accent', 'type': 'color', 'label': 'Mint', 'default': '#93E2C0'},
        {'id': 'font-display', 'type': 'string', 'label': 'Font', 'default': 'Roboto'},
        {'id': 'hook', 'type': 'string', 'label': 'Hook', 'default': 'Read it. Tap it. Hear it.'},
        {'id': 'product-name', 'type': 'string', 'label': 'Product', 'default': 'Voice Mail'},
    ]
    root = '''<!doctype html>
<html lang="en" data-composition-variables='VARIABLES'>
<head><meta charset="UTF-8"><meta name="viewport" content="width=1080, height=1350">
<script src="assets/vendor/gsap.min.js"></script><script src="assets/audio-data.js"></script>
<style>*{margin:0;padding:0;box-sizing:border-box}html,body{width:1080px;height:1350px;overflow:hidden;background:#101214}
#root{position:relative;width:100%;height:100%;font-family:var(--font-display,Roboto);color:var(--color-ink,#F3F5F7)}
.stage{position:absolute;inset:0}</style></head><body>
<div id="root" data-composition-id="main" data-start="0" data-duration="22" data-width="1080" data-height="1350" data-fps="60">
<div id="flow" class="clip stage" data-composition-id="flow" data-composition-src="compositions/flow.html" data-start="0" data-duration="15" data-track-index="1" data-track-kind="graphics" data-width="1080" data-height="1350"></div>
<div id="appearance" class="clip stage" data-composition-id="appearance" data-composition-src="compositions/appearance.html" data-start="15" data-duration="4" data-track-index="2" data-track-kind="graphics" data-width="1080" data-height="1350"></div>
<div id="outro" class="clip stage" data-composition-id="outro" data-composition-src="compositions/outro.html" data-start="19" data-duration="3" data-track-index="3" data-track-kind="graphics" data-width="1080" data-height="1350"></div>
<audio id="music" class="clip" data-start="0" data-duration="22" data-track-index="10" data-timeline-role="music" data-volume="1" data-fade-in="0.35" data-fade-out="1.2" src="assets/music/bed.wav"></audio>
<audio id="opening-accent" class="clip" data-start="0.1" data-duration="0.18" data-track-index="11" data-volume="0.14" src="assets/sfx/soft.ogg"></audio>
<audio id="ending-accent" class="clip" data-start="19.05" data-duration="0.18" data-track-index="12" data-volume="0.14" src="assets/sfx/soft.ogg"></audio>
</div><script>window.__timelines=window.__timelines||{};window.__timelines.main=gsap.timeline({paused:true});</script>
</body></html>'''.replace('VARIABLES', json.dumps(variables))
    write(COMP / 'index.html', root)
    common = '''#root{position:absolute;inset:0;background:var(--color-ground,#101214);color:var(--color-ink,#F3F5F7);font-family:var(--font-display,Roboto)}
    .brand{position:absolute;left:108px;top:92px;display:flex;align-items:center;gap:14px;font-size:28px;font-weight:700;letter-spacing:2px}
    .brand img{width:40px;height:40px}.heading{position:absolute;left:108px;top:178px;font-size:68px;font-weight:700;line-height:1.1;letter-spacing:-2px;width:864px}
    .note{position:absolute;left:108px;bottom:70px;font-size:28px;color:#AEB7C2}.viewport{position:absolute;left:98px;top:325px;width:884px;height:930px;overflow:hidden}
    .native{position:absolute;left:0;top:0;width:884px;height:1178px;object-fit:contain;object-position:top}
    '''
    brand = '<div class="brand"><img src="assets/app-icon.png" alt=""><span>VOICE MAIL</span></div>'
    flow = '''<template><style>COMMON
    #flow-hook{font-size:66px}.subcopy{position:absolute;left:108px;top:260px;font-size:30px;color:#AEB7C2}
    </style><div id="root" data-composition-id="flow" data-width="1080" data-height="1350">
    BRAND
    <div id="flow-hook-clip" class="clip" data-start="0" data-duration="4" data-track-index="1" data-hold="true"><h1 id="flow-hook" class="heading" data-var-text="hook">Read it. Tap it. Hear it.</h1></div>
    <div id="flow-phrase-clip" class="clip" data-start="4" data-duration="7" data-track-index="2"><h1 id="flow-phrase" class="heading">Tap a phrase.</h1><p id="flow-phrase-sub" class="subcopy">Hear that moment.</p></div>
    <div id="flow-find-clip" class="clip" data-start="11" data-duration="4" data-track-index="3"><h1 id="flow-find" class="heading">Find the detail.</h1><p id="flow-find-sub" class="subcopy">Search inside the transcript.</p></div>
    <div class="viewport" data-layout-allow-overflow="true">
      <div id="flow-panel"><img id="flow-still" class="clip native" data-start="0" data-duration="4" data-track-index="4" src="assets/native/recording.png" alt="Sample voicemail player and transcript">
      <video id="flow-native-video" class="clip native" data-start="4" data-duration="11" data-track-index="5" src="assets/native/recording.mp4" muted playsinline></video></div>
    </div>
    <p class="note">Android preview · Sample message</p>
    </div><script>
    const tl=gsap.timeline({paused:true});
    // Adapted from the registry soft-blur-in primitive, without a CSS transform conflict.
    tl.fromTo('#flow-hook',{opacity:0,filter:'blur(6px)'},{opacity:1,filter:'blur(0px)',duration:.45,ease:'power4.out'},0);
    tl.fromTo('#flow-panel',{y:28,opacity:.85},{y:0,opacity:1,duration:.55,ease:'power4.out'},0);
    tl.fromTo('#flow-phrase',{opacity:0,filter:'blur(6px)'},{opacity:1,filter:'blur(0px)',duration:.35,ease:'power3.out'},4);
    tl.fromTo('#flow-phrase-sub',{opacity:0},{opacity:1,duration:.3},4.15);
    // Carry the same real transcript into focus as its native search controls open.
    tl.to('#flow-panel',{y:-405,duration:.65,ease:'power2.inOut'},10.85);
    tl.fromTo('#flow-find',{opacity:0,filter:'blur(6px)'},{opacity:1,filter:'blur(0px)',duration:.35,ease:'power3.out'},11);
    tl.fromTo('#flow-find-sub',{opacity:0},{opacity:1,duration:.3},11.1);
    window.__timelines.flow=tl;
    </script></template>'''.replace('COMMON', common).replace('BRAND', brand)
    write(COMP / 'compositions/flow.html', flow)
    appearance = '''<template><style>COMMON
    #root{background:#F3F4F6;color:#161C24}.theme{position:absolute;inset:0}
    .theme img{position:absolute;left:98px;top:325px;width:884px;height:895px}.theme-name{position:absolute;left:108px;top:270px;font-size:30px}
    #appearance-dark{background:#101214;color:#F3F5F7}#appearance-black{background:#000;color:#F3F5F7}
    </style><div id="root" data-composition-id="appearance" data-width="1080" data-height="1350">
    <div id="appearance-light" class="theme"><img src="assets/native/theme-1.png" alt="Light appearance"></div>
    <div id="appearance-dark" class="theme"><img src="assets/native/theme-2.png" alt="Dark appearance"></div>
    <div id="appearance-black" class="theme"><img src="assets/native/theme-3.png" alt="AMOLED appearance"></div>
    <p id="appearance-label-light" class="clip theme-name" data-start="0" data-duration="1.15" data-track-index="4">Light</p>
    <p id="appearance-label-dark" class="clip theme-name" style="color:#F3F5F7" data-start="1.55" data-duration="1" data-track-index="4">Dark</p>
    <p id="appearance-label-black" class="clip theme-name" style="color:#F3F5F7" data-start="2.95" data-duration="1.05" data-track-index="4">AMOLED</p>
    <div id="appearance-ink">BRAND<h1 id="appearance-title" class="heading">Make it yours.</h1></div>
    </div><script>const tl=gsap.timeline({paused:true});
    tl.fromTo('#appearance-dark',{opacity:0},{opacity:1,duration:.4,ease:'power2.inOut'},1.15);
    tl.fromTo('#appearance-black',{opacity:0},{opacity:1,duration:.4,ease:'power2.inOut'},2.55);
    tl.fromTo('#appearance-ink',{color:'#161C24'},{color:'#F3F5F7',duration:.4,ease:'power2.inOut'},1.15);
    window.__timelines.appearance=tl;</script></template>'''.replace('COMMON', common).replace('BRAND', brand)
    write(COMP / 'compositions/appearance.html', appearance)
    outro = '''<template><style>COMMON
    #outro-content{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:34px}
    #outro-logo{width:144px;height:144px}#outro-name{font-size:104px;letter-spacing:-3px;font-weight:700}
    #outro-line{font-size:32px;color:#AEB7C2}#outro-preview{font-size:28px;color:#93E2C0;margin-top:68px}
    </style><div id="root" data-composition-id="outro" data-width="1080" data-height="1350">
    <div id="outro-content" data-hold="true"><img id="outro-logo" src="assets/app-icon.png" alt="Voice Mail icon"><h1 id="outro-name" data-var-text="product-name">Voice Mail</h1><p id="outro-line">On-device transcripts. Your chosen dialer.</p><p id="outro-preview">Android / Mint preview</p></div>
    <p class="note">Sample messages · Design feedback welcome</p>
    </div><script>const tl=gsap.timeline({paused:true});
    tl.fromTo('#outro-content',{y:22,opacity:0},{y:0,opacity:1,duration:.55,ease:'power4.out'},0);
    // Build-time audio modulation affects only the brand icon, never readable text.
    const audio=window.LAUNCH_AUDIO;
    if(audio&&audio.frames){for(let i=0;i<180;i+=6){const frame=audio.frames[1140+i];const bands=frame?.bands||[];const bass=Math.max(bands[0]||0,bands[1]||0);tl.to('#outro-logo',{opacity:.96+.04*bass,duration:.1,ease:'none'},i/60);}}
    window.__timelines.outro=tl;</script></template>'''.replace('COMMON', common)
    write(COMP / 'compositions/outro.html', outro)
    write(HERE / 'share-copy.txt', "I built an Android voicemail app with on-device transcripts, search, and phrase playback, using my existing Mint mailbox and chosen dialer. This is a private preview with sample messages. Design feedback is welcome.\n")


if __name__ == '__main__':
    prepare()
    compose()
