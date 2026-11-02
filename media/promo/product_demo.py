"""Render a 32-second launch film from real sample-emulator interactions."""
from __future__ import annotations

import argparse
from functools import lru_cache
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import subprocess

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
WORK, EXPORTS = HERE / 'work', HERE / 'exports'
WIDTH, HEIGHT, FPS, DURATION = 1080, 1350, 60, 32
# 135 BPM, four beats per bar. Holds give each native interaction time to read.
BAR = 16 / 9
CUTS = tuple(b * BAR for b in (0, 2, 5, 7, 10, 12, 16, 18))
BG = (16, 17, 20)
WHITE, MINT, MUTED = (242, 245, 247), (152, 226, 195), (165, 173, 182)
FFMPEG = shutil.which('ffmpeg')
MUSIC = WORK / 'music/stylz.mp3'
MUSIC_SHA256 = 'a0307e929eb60ed10488eacfdf8b3fddc893cf9b8680f5a55b65fb51b7075fdc'
ICON = Image.open(ROOT / 'docs/branding/app-icon.png').convert('RGBA')


@lru_cache(None)
def font(size, bold=False):
    override = os.environ.get('PROMO_FONT_BOLD' if bold else 'PROMO_FONT_REGULAR')
    for file in (override,
                 'C:/Windows/Fonts/seguisb.ttf' if bold else 'C:/Windows/Fonts/segoeui.ttf',
                 '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf' if bold else
                 '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if file and Path(file).is_file():
            return ImageFont.truetype(file, size)
    raise RuntimeError('Set PROMO_FONT_REGULAR and PROMO_FONT_BOLD to installed fonts')


@lru_cache(None)
def lettering(value, size, color, bold):
    face = font(size * 2, bold)
    bounds = ImageDraw.Draw(Image.new('L', (1, 1))).multiline_textbbox(
        (0, 0), value, font=face, spacing=round(size * .2))
    width, height = bounds[2] - bounds[0] + 16, bounds[3] - bounds[1] + 16
    layer = Image.new('RGBA', (width, height))
    ImageDraw.Draw(layer).multiline_text((8 - bounds[0], 8 - bounds[1]), value,
                                        font=face, spacing=round(size * .2), fill=color)
    return layer.resize((math.ceil(width / 2), math.ceil(height / 2)), Image.Resampling.LANCZOS)


def composite(im, layer, x, y, opacity=1):
    """Preserve fractional placement so slow motion does not snap to whole pixels."""
    left, top = math.floor(x), math.floor(y)
    dx, dy = x - left, y - top
    if dx or dy:
        layer = layer.transform((layer.width + 1, layer.height + 1), Image.Transform.AFFINE,
                                (1, 0, -dx, 0, 1, -dy), Image.Resampling.BICUBIC)
    if opacity < 1:
        layer = layer.copy()
        layer.putalpha(layer.getchannel('A').point(lambda a: round(a * max(0, opacity))))
    im.alpha_composite(layer, (left, top))


def text(im, value, x, y, size, color=WHITE, bold=False, opacity=1, center=False):
    layer = lettering(value, size, color, bold)
    if center:
        x -= layer.width / 2
    if x < 64 or x + layer.width > WIDTH - 64:
        raise ValueError('Text exceeds safe area: ' + value)
    composite(im, layer, x, y, opacity)


def ease(t):
    t = min(1, max(0, t))
    return 1 - (1 - t) ** 3


def smooth(t):
    t = min(1, max(0, t))
    return t * t * (3 - 2 * t)


@lru_cache(None)
def icon(size):
    return ICON.resize((size, size), Image.Resampling.LANCZOS)


class Clip:
    def __init__(self, name):
        self.path = WORK / (name + '-stream.mp4')
        self.process, self.index, self.last = None, -1, None
        metadata = json.loads(subprocess.check_output([
            'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
            'stream=width,height', '-of', 'json', str(self.path)]))['streams'][0]
        self.size = metadata['width'], metadata['height']

    def close(self):
        if self.process:
            self.process.stdout.close()
            self.process.terminate()
            self.process.wait()
            self.process = None

    def get(self, seconds):
        target = max(0, int(seconds * FPS + 1e-6))
        if target < self.index:
            self.close()
            self.index, self.last = -1, None
        if self.process is None:
            self.process = subprocess.Popen([
                FFMPEG, '-v', 'error', '-i', str(self.path), '-an', '-vf', 'fps=60',
                '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        while self.index < target:
            data = self.process.stdout.read(self.size[0] * self.size[1] * 3)
            if len(data) != self.size[0] * self.size[1] * 3:
                break
            self.last = Image.frombytes('RGB', self.size, data)
            self.index += 1
        if self.last is None:
            raise RuntimeError('Missing native frames: ' + self.path.name)
        return self.last


CLIPS = None
THEMES = None


def prepare():
    global CLIPS, THEMES
    CLIPS = {name: Clip(name) for name in ['inbox', 'playback', 'quality']}
    THEMES = [Image.open(WORK / f'theme-{i}.png').convert('RGB') for i in (1, 2, 3)]


def surface(im, source, box, x, y, width, radius=0, opacity=1):
    """Place actual UI content only. No device shell, outline, bezel, or screen mask."""
    ratio = source.width / 720
    left, top, right, bottom = (v * ratio for v in box)
    scale = (right - left) / width
    height = (bottom - top) / scale
    layer = source.convert('RGBA').transform(
        (math.ceil(width), math.ceil(height)), Image.Transform.AFFINE,
        (scale, 0, left, 0, scale, top), Image.Resampling.BICUBIC)
    if radius:
        # Match the app card's own corners, never a phone-shaped outer container.
        alpha = Image.new('L', (layer.width * 2, layer.height * 2))
        ImageDraw.Draw(alpha).rounded_rectangle((0, 0, alpha.width, alpha.height),
                                                radius=radius / scale * 2, fill=255)
        layer.putalpha(alpha.resize(layer.size, Image.Resampling.LANCZOS))
    composite(im, layer, x, y, opacity)


def brand(im, opacity=1, color=WHITE):
    composite(im, icon(38), 88, 66, opacity)
    text(im, 'VOICE MAIL', 139, 76, 19, color, True, opacity)


def header(im, title, subtitle, t, size=64, color=WHITE):
    entry = ease(t / .42)
    text(im, title, 88, 176 + 14 * (1 - entry), size, color, bold=True, opacity=entry)
    if subtitle:
        text(im, subtitle, 90, 275 + 10 * (1 - entry), 28, MUTED, opacity=entry)


def scene(index, t):
    theme_text, theme_muted, theme_accent = WHITE, MUTED, MINT
    canvas = BG
    if index == 5:
        interval = (CUTS[6] - CUTS[5]) / 3
        current = min(2, int(t / interval))
        alpha = smooth((t - current * interval) / .3) if current else 1
        palettes = [((243, 244, 247), (23, 29, 35), (83, 96, 108), (18, 106, 83)),
                    (BG, WHITE, MUTED, MINT), ((0, 0, 0), WHITE, MUTED, MINT)]
        previous = palettes[max(0, current - 1)]
        colors = [tuple(round(a + (b - a) * alpha) for a, b in zip(old, new))
                  for old, new in zip(previous, palettes[current])]
        canvas, theme_text, theme_muted, theme_accent = colors
    im = Image.new('RGBA', (WIDTH, HEIGHT), canvas)
    if index != 6:
        brand(im, color=theme_text)
    if index == 0:
        entry = ease(t / .55)
        text(im, 'Your voicemail.\nMade clearer.', 86, 262 + (1 - entry) * 22,
             96, bold=True, opacity=entry)
        text(im, 'Read, find, and play your messages.', 90, 532, 30, MUTED,
             opacity=ease((t - .22) / .5))
        reveal = ease((t - .25) / .7)
        surface(im, CLIPS['inbox'].get(.4), (38, 600, 682, 865),
                90, 741 + (1 - reveal) * 36, 900, 44, reveal)
        text(im, 'ANDROID PREVIEW', 90, 1202, 19, MUTED)
        text(im, 'Sample messages', 774, 1202, 18, MUTED)
    elif index == 1:
        header(im, 'Go straight to the moment.', 'Tap a phrase to play from there.', t, 62)
        source = CLIPS['playback'].get(.25 + t)
        # Two native app cards, with no full-screen or device-shaped container.
        surface(im, source, (38, 248, 682, 529), 92, 356, 896, 44)
        surface(im, source, (38, 560, 682, 900), 92, 788, 896, 44)
        if t > 3.7:
            text(im, 'Phrase timing is approximate.', 540, 1286, 18, MUTED, center=True)
    elif index == 2:
        header(im, 'Find the detail.', 'Search inside your saved transcripts.', t, 72)
        source = CLIPS['playback'].get(6.3 + t)
        travel = smooth(t / .42)
        # The same transcript rises into focus; the frame never resembles a handset.
        surface(im, source, (38, 560, 682, 1105), 92,
                788 - 407 * travel, 896, 44)
    elif index == 3:
        entry = ease(t / .42)
        text(im, 'Your mailbox.\nYour chosen dialer.', 88, 174 + (1 - entry) * 14,
             66, bold=True, opacity=entry)
        text(im, 'Keep your existing Mint voicemail.', 90, 354, 29, MUTED)
        source = CLIPS['inbox'].get(.9 + t)
        # Tabs + one complete message prove navigation without a tiny whole-phone shot.
        surface(im, source, (24, 370, 696, 880), 92, 476, 896)
        text(im, 'No forwarding setup.', 540, 1225, 27, MINT, center=True)
    elif index == 4:
        header(im, 'Transcribe on your phone.', 'Whisper runs locally.', t, 62)
        source = CLIPS['quality'].get(.35 + t)
        surface(im, source, (38, 495, 682, 925), 92, 432, 896, 44)
        text(im, 'Choose a quality. Download its model.', 540, 1129, 27, MUTED, center=True)
    elif index == 5:
        header(im, 'Make it yours.', '', t, 78, color=theme_text)
        interval = (CUTS[6] - CUTS[5]) / 3
        current = min(2, int(t / interval))
        alpha = smooth((t - current * interval) / .3) if current else 1
        player = (38, 248, 682, 529)
        transcript = (38, 560, 682, 900)
        for box, y in [(player, 340), (transcript, 772)]:
            if current:
                surface(im, THEMES[current - 1], box, 92, y, 896, 44)
            surface(im, THEMES[current], box, 92, y, 896, 44, alpha)
        for i, name in enumerate(['Light', 'Dark', 'AMOLED']):
            text(im, name, [257, 540, 823][i], 1271, 26,
                 theme_accent if i == current else theme_muted, i == current, center=True)
    else:
        entry = ease(t / .6)
        composite(im, icon(136), 472, 329 + (1 - entry) * 18, entry)
        text(im, 'Voice Mail', 540, 534, 104, bold=True, center=True, opacity=entry)
        text(im, 'Built for Android.', 540, 707, 34, MUTED, center=True)
        text(im, 'Tested on Mint Mobile.', 540, 758, 34, MUTED, center=True)
        text(im, 'DEVELOPMENT PREVIEW', 540, 1031, 19, MINT, center=True)
        text(im, 'Sample messages shown', 540, 1225, 18, MUTED, center=True)
    return im


def frame_at(seconds):
    index = next((i for i, end in enumerate(CUTS[1:]) if seconds < end), 6)
    return scene(index, seconds - CUTS[index]).convert('RGB')


def stamp(seconds):
    ms = round(seconds * 1000)
    return f'00:{ms // 60000:02d}:{ms // 1000 % 60:02d},{ms % 1000:03d}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    if not FFMPEG:
        raise RuntimeError('FFmpeg is required')
    prepare()
    EXPORTS.mkdir(exist_ok=True)
    captions = [
        'Your voicemail. Made clearer.',
        'Tap a phrase to play from there. Phrase timing is approximate.',
        'Search inside your saved transcripts.',
        'Keep your existing Mint voicemail and chosen dialer. No forwarding setup.',
        'Whisper transcribes on your phone. Models download separately.',
        'Light, Dark, and true-black AMOLED.',
        'Voice Mail. Android preview. Sample messages shown.',
    ]
    (EXPORTS / 'voice-mail-product-demo.srt').write_text('\n'.join(
        f'{i + 1}\n{stamp(CUTS[i])} --> {stamp(CUTS[i + 1])}\n{copy}\n'
        for i, copy in enumerate(captions)), encoding='utf-8')
    try:
        if args.preview:
            for i, start in enumerate(CUTS[:-1]):
                frame_at(start + min(1.2, (CUTS[i + 1] - start) / 2)).save(
                    WORK / f'launch-scene-{i + 1}.png')
            frame_at(1.5).save(EXPORTS / 'voice-mail-product-demo-cover.png')
            print('Launch storyboard ready')
            return
        if not MUSIC.is_file() or hashlib.sha256(MUSIC.read_bytes()).hexdigest() != MUSIC_SHA256:
            raise RuntimeError('Run media/promo/fetch_music.py for the verified soundtrack')
        target = EXPORTS / 'voice-mail-product-demo.mp4'
        command = [
            FFMPEG, '-y', '-hide_banner', '-loglevel', 'warning',
            '-f', 'rawvideo', '-pixel_format', 'rgb24', '-video_size', f'{WIDTH}x{HEIGHT}',
            '-framerate', str(FPS), '-i', 'pipe:0', '-i', str(MUSIC),
            '-map', '0:v:0', '-map', '1:a:0', '-map_metadata', '-1',
            '-c:v', 'libx264', '-preset', 'medium', '-crf', '18',
            '-maxrate', '12M', '-bufsize', '24M', '-pix_fmt', 'yuv420p', '-r', str(FPS),
            '-vf', 'scale=in_range=full:out_range=tv:out_color_matrix=bt709,setsar=1',
            '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
            '-color_range', 'tv', '-profile:v', 'high', '-level:v', '4.2',
            '-c:a', 'aac', '-b:a', '192k', '-ar', '48000',
            '-af', 'atrim=0:32,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=0.18,'
                   'afade=t=out:st=30.65:d=1.35,loudnorm=I=-18:TP=-2:LRA=7',
            '-movflags', '+faststart', '-shortest',
            '-metadata', 'title=Voice Mail - product launch preview',
            '-metadata', 'comment=Synthetic demo messages. Android development preview.', str(target),
        ]
        with (WORK / 'launch-ffmpeg.log').open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, stdin=subprocess.PIPE, stderr=log)
            try:
                for n in range(FPS * DURATION):
                    frame = frame_at(n / FPS)
                    if n == 90:
                        frame.save(EXPORTS / 'voice-mail-product-demo-cover.png')
                    process.stdin.write(frame.tobytes())
                    if n % (FPS * 4) == 0:
                        print(f'Rendered {n / FPS:.0f}/{DURATION}s', flush=True)
                process.stdin.close()
                if process.wait():
                    raise RuntimeError('Encoding failed; see work/launch-ffmpeg.log')
            except BaseException:
                process.kill()
                process.wait()
                raise
        print(target, flush=True)
    finally:
        for clip in CLIPS.values():
            clip.close()


if __name__ == '__main__':
    main()
