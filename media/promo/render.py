"""Render the LinkedIn product preview from reviewed app assets and original music.

Requires Python 3.9+, Pillow, NumPy, and ffmpeg/ffprobe on PATH.
No phone connection, raw captures, network access, or third-party music is used.
"""
from __future__ import annotations

import argparse
import math
import os
from pathlib import Path
import shutil
import subprocess
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
EXPORTS = HERE / "exports"
WORK = HERE / "work"
WIDTH, HEIGHT, FPS, DURATION = 1080, 1350, 30, 26
MINT = (181, 240, 212)
WHITE = (242, 248, 245)
MUTED = (168, 193, 183)
CUTS = (0, 3.2, 7.4, 11.6, 15.8, 20.2, 26)
SCENES = (
    ("I'm building Voice Mail.", "A visual voicemail app for Android."),
    ("Keep your mailbox and your dialer.", "Tested on Mint Mobile. No call-forwarding setup."),
    ("Search your saved inbox.", "Contacts, numbers, and dates."),
    ("Control playback.", "Seek, skip, and change speed."),
    ("Choose your theme.", "Light, Dark, and true-black AMOLED."),
    ("Voice Mail for Android.", "Development preview on Mint Mobile. Transcription planned. Feedback on the UI is welcome."),
)


def font(size: int, bold: bool = False):
    override = os.environ.get("PROMO_FONT_BOLD" if bold else "PROMO_FONT_REGULAR")
    candidates = [override] if override else []
    candidates += [
        "C:/Windows/Fonts/seguisb.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return ImageFont.truetype(candidate, size)
    raise RuntimeError("Set PROMO_FONT_REGULAR and PROMO_FONT_BOLD to installed .ttf fonts.")


FONTS = {(size, bold): font(size, bold) for size, bold in
         [(18, True), (21, False), (22, True), (25, True), (27, False), (29, False),
          (30, False), (31, False), (33, False), (34, False), (70, True), (78, True), (98, True), (108, True)]}


def text(image, value, xy, size, color=WHITE, bold=False, center=False, line_gap=1.12):
    draw = ImageDraw.Draw(image)
    face = FONTS[(size, bold)]
    for line_number, line in enumerate(value.split("\n")):
        box = draw.textbbox((0, 0), line, font=face)
        x, y = xy[0], xy[1] + round(line_number * size * line_gap)
        if center:
            x -= (box[2] - box[0]) / 2
        if x < 55 or x + box[2] > WIDTH - 55:
            raise ValueError("Text exceeds horizontal safe area: " + line)
        draw.text((round(x), round(y)), line, font=face, fill=color)


def background():
    y, x = np.mgrid[0:HEIGHT, 0:WIDTH]
    glow = np.exp(-(((x - 820) / 760) ** 2 + ((y - 330) / 900) ** 2))
    lower = np.exp(-(((x - 80) / 680) ** 2 + ((y - 1250) / 590) ** 2))
    base = np.empty((HEIGHT, WIDTH, 3), dtype=np.float32)
    for channel, color in enumerate((9, 22, 20)):
        base[:, :, channel] = color + glow * (9, 27, 21)[channel] + lower * (3, 8, 6)[channel]
    # Static, subpixel grain avoids banding without flickering between frames.
    base += np.random.default_rng(14).normal(0, .32, (HEIGHT, WIDTH, 1))
    return Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


BG = background()
ICON = Image.open(ROOT / "docs/branding/app-icon.png").convert("RGBA")
SOURCES = {name: Image.open(ROOT / f"docs/screenshots/{name}.png").convert("RGBA")
           for name in ("inbox-dark", "playback-dark", "inbox-light", "inbox-amoled")}


def pill(image, label, xy, width):
    x, y = xy
    ImageDraw.Draw(image).rounded_rectangle((x, y, x + width, y + 54), radius=27,
                                           fill=(34, 72, 59, 255), outline=(69, 108, 91, 255), width=1)
    text(image, label, (x + width / 2, y + 11), 22, MINT, True, center=True)


def common(scene):
    image = BG.copy()
    image.alpha_composite(ICON.resize((48, 48), Image.Resampling.LANCZOS), (76, 69))
    text(image, "VOICE MAIL", (139, 77), 25, WHITE, True)
    ImageDraw.Draw(image).rounded_rectangle((706, 74, 1004, 112), radius=19, outline=(65, 101, 87), width=1)
    text(image, "DEVELOPMENT PREVIEW", (855, 80), 18, MUTED, True, center=True)
    text(image, "Android · Mint Mobile", (76, 1248), 21, MUTED)
    text(image, f"{scene + 1:02d} / 06", (918, 1248), 21, MUTED)
    return image


def panel(name, width, crop=None, radius=38):
    source = SOURCES[name]
    if crop:
        source = source.crop(crop)
    height = round(width * source.height / source.width)
    source = source.resize((width, height), Image.Resampling.LANCZOS)
    inset = 35
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=radius, fill=255)
    shadow = Image.new("RGBA", (width + 2 * inset, height + 2 * inset), (0, 0, 0, 0))
    shadow_mask = Image.new("L", shadow.size, 0)
    shadow_mask.paste(mask, (inset, inset + 12))
    shadow_mask = shadow_mask.filter(ImageFilter.GaussianBlur(19)).point(lambda pixel: round(pixel * .30))
    shadow.putalpha(shadow_mask)
    source.putalpha(mask)
    shadow.alpha_composite(source, (inset, inset))
    ImageDraw.Draw(shadow).rounded_rectangle((inset, inset, width + inset - 1, height + inset - 1),
                                            radius=radius, outline=(125, 164, 145, 75), width=2)
    return shadow


def build_scenes():
    result = []
    image = common(0)
    text(image, "I'm building\nVoice Mail.", (540, 534), 98, WHITE, True, center=True)
    text(image, "Visual voicemail for Android", (540, 794), 33, MUTED, center=True)
    pill(image, "Mint Mobile preview", (366, 1000), 348)
    result.append((image, [(ICON.resize((210, 210), Image.Resampling.LANCZOS), 435, 257, 0)]))

    image = common(1)
    text(image, "Keep your\nmailbox.", (76, 265), 78, WHITE, True)
    text(image, "Use Mint voicemail\nwith your usual dialer.", (76, 489), 31, MUTED)
    pill(image, "No forwarding setup", (76, 635), 330)
    text(image, "Tested on Mint Mobile", (76, 747), 27, MUTED)
    result.append((image, [(panel("inbox-dark", 430), 535, 198, 1)]))

    image = common(2)
    text(image, "Search your saved inbox.", (76, 196), 70, WHITE, True)
    text(image, "Contacts, numbers, and dates.", (76, 300), 30, MUTED)
    # The real search/filter area fills the frame, so it stays readable in a mobile feed.
    pill(image, "Optional contact names", (320, 1126), 440)
    result.append((image, [(panel("inbox-light", 928, (60, 780, 1380, 1530), 28), 41, 465, 2)]))

    image = common(3)
    text(image, "Control playback.", (76, 190), 70, WHITE, True)
    text(image, "Seek, skip, and change speed.", (76, 294), 30, MUTED)
    result.append((image, [(panel("playback-dark", 928, (60, 1600, 1380, 2760), 28), 41, 365, 3)]))

    image = common(4)
    text(image, "Choose your theme.", (76, 190), 70, WHITE, True)
    text(image, "Light, Dark, and true-black AMOLED.", (76, 294), 29, MUTED)
    layers = []
    for index, (name, label) in enumerate((("inbox-light", "Light"), ("inbox-dark", "Dark"), ("inbox-amoled", "AMOLED"))):
        x = 80 + 320 * index
        layers.append((panel(name, 280, radius=30), x - 35, 410, 4 + index))
        text(image, label, (x + 140, 1102), 27, WHITE, center=True)
    result.append((image, layers))

    image = common(5)
    text(image, "Voice Mail", (540, 571), 108, WHITE, True, center=True)
    text(image, "An Android app in development", (540, 728), 33, MUTED, center=True)
    pill(image, "Mint Mobile", (422, 839), 236)
    text(image, "Transcription planned", (540, 950), 30, MINT, center=True)
    text(image, "Feedback on the UI is welcome.", (540, 1080), 34, WHITE, center=True)
    result.append((image, [(ICON.resize((210, 210), Image.Resampling.LANCZOS), 435, 273, 7)]))
    return result


COMPOSITIONS = build_scenes()


def ease(value):
    value = min(1, max(0, value))
    return 1 - (1 - value) ** 3


def scene_frame(index, elapsed):
    base, layers = COMPOSITIONS[index]
    frame = base.copy()
    entry = ease(elapsed / .65)
    for layer, x, y, phase in layers:
        offset = round((1 - entry) * 40 + math.sin(elapsed * 1.25 + phase * .55) * 5)
        frame.alpha_composite(layer, (x, y + offset))
    return frame


def frame_at(seconds):
    index = next((i for i in range(len(CUTS) - 1) if CUTS[i] <= seconds < CUTS[i + 1]), 5)
    elapsed = seconds - CUTS[index]
    frame = scene_frame(index, elapsed)
    if index > 0 and elapsed < .30:
        previous = scene_frame(index - 1, CUTS[index] - CUTS[index - 1] + elapsed)
        frame = Image.blend(previous, frame, ease(elapsed / .30))
    draw = ImageDraw.Draw(frame)
    draw.rounded_rectangle((76, 1302, 1004, 1306), radius=2, fill=(55, 83, 71))
    draw.rounded_rectangle((76, 1302, 76 + round(928 * min(seconds / DURATION, 1)), 1306), radius=2, fill=MINT)
    return frame.convert("RGB")


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


def timestamp(seconds):
    milliseconds = round(seconds * 1000)
    return f"00:{milliseconds // 60000:02d}:{milliseconds // 1000 % 60:02d},{milliseconds % 1000:03d}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", action="store_true", help="Only render storyboard frames and cover")
    args = parser.parse_args()
    EXPORTS.mkdir(exist_ok=True)
    WORK.mkdir(exist_ok=True)
    for index, start in enumerate(CUTS[:-1]):
        frame_at(start + 1.1).save(WORK / f"scene-{index + 1}.png")
    frame_at(1.8).save(EXPORTS / "voice-mail-linkedin-cover.png")
    subtitles = []
    for i, ((heading, detail), start, end) in enumerate(zip(SCENES, CUTS, CUTS[1:]), 1):
        subtitles.append(f"{i}\n{timestamp(start)} --> {timestamp(end)}\n{heading}\n{detail}\n")
    (EXPORTS / "voice-mail-linkedin.srt").write_text("\n".join(subtitles), encoding="utf-8")
    if args.preview:
        print("Storyboard and cover ready.")
        return
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise RuntimeError("Install ffmpeg with the libx264 and AAC encoders.")
    soundtrack = WORK / "soundtrack.wav"
    music(soundtrack)
    destination = EXPORTS / "voice-mail-linkedin.mp4"
    command = [ffmpeg, "-y", "-hide_banner", "-loglevel", "warning", "-f", "rawvideo", "-pixel_format", "rgb24",
               "-video_size", f"{WIDTH}x{HEIGHT}", "-framerate", str(FPS), "-i", "pipe:0", "-i", str(soundtrack),
               "-map", "0:v:0", "-map", "1:a:0", "-map_metadata", "-1", "-c:v", "libx264", "-preset", "medium",
               "-crf", "20", "-maxrate", "8M", "-bufsize", "16M", "-pix_fmt", "yuv420p", "-r", str(FPS),
               "-vf", "scale=in_range=full:out_range=tv:out_color_matrix=bt709,setsar=1",
               "-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709", "-color_range", "tv",
               "-profile:v", "high", "-level:v", "4.1", "-c:a", "aac", "-b:a", "160k", "-ar", "48000",
               "-af", "loudnorm=I=-18:TP=-2:LRA=7", "-movflags", "+faststart", "-shortest",
               "-metadata", "title=Voice Mail - Android development preview", str(destination)]
    with (WORK / "ffmpeg.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, stdin=subprocess.PIPE, stderr=log)
        try:
            for number in range(DURATION * FPS):
                process.stdin.write(frame_at(number / FPS).tobytes())
                if number % (FPS * 4) == 0:
                    print(f"Rendered {number / FPS:.0f} / {DURATION} seconds", flush=True)
            process.stdin.close()
            if process.wait() != 0:
                raise RuntimeError("ffmpeg failed; inspect work/ffmpeg.log")
        except BaseException:
            process.kill()
            process.wait()
            raise
    print(destination, flush=True)


if __name__ == "__main__":
    main()
