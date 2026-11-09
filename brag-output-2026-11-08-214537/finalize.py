"""Bake a deliberately selected poster, then verify the encoded delivery."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

HERE = Path(__file__).resolve().parent


def run(*args, capture=False):
    return subprocess.run([str(a) for a in args], check=True,
                          capture_output=capture, text=capture, encoding='utf-8' if capture else None)


def main():
    work = HERE / 'work'
    work.mkdir(exist_ok=True)
    raw = work / 'hyperframes-render.mp4'
    verification = HERE / 'verification.json'
    previous = json.loads(verification.read_text(encoding='utf-8')) if verification.exists() else {}
    incoming_hash = hashlib.sha256((HERE / 'brag.mp4').read_bytes()).hexdigest()
    if not raw.exists() or incoming_hash != previous.get('sha256'):
        shutil.copyfile(HERE / 'brag.mp4', raw)
    run('ffmpeg', '-y', '-v', 'error', '-ss', '5.5', '-i', raw,
        '-frames:v', '1', '-q:v', '2', HERE / 'brag.jpg')
    run('ffmpeg', '-y', '-v', 'error', '-i', raw, '-i', HERE / 'brag.jpg',
        '-filter_complex', "[0:v][1:v]overlay=0:0:enable='eq(n,0)'[v]",
        '-map', '[v]', '-map', '0:a?', '-map_metadata', '-1',
        '-c:v', 'libx264', '-crf', '16', '-preset', 'slow', '-pix_fmt', 'yuv420p',
        '-c:a', 'copy', '-movflags', '+faststart', HERE / 'brag.mp4')
    probe = json.loads(run('ffprobe', '-v', 'error', '-show_streams', '-show_format',
                           '-of', 'json', HERE / 'brag.mp4', capture=True).stdout)
    video = next(s for s in probe['streams'] if s['codec_type'] == 'video')
    audio = next(s for s in probe['streams'] if s['codec_type'] == 'audio')
    assert (video['width'], video['height'], video['r_frame_rate']) == (1080, 1350, '60/1')
    assert int(video['nb_frames']) == 1320
    assert abs(float(probe['format']['duration']) - 22) < .05
    run('ffmpeg', '-v', 'error', '-i', HERE / 'brag.mp4', '-f', 'null', '-')
    check = json.loads((HERE / 'check.json').read_text(encoding='utf-8-sig'))
    assert check['ok'] and check['contrast']['checked'] > 0
    loudness = run('ffmpeg', '-hide_banner', '-i', HERE / 'brag.mp4', '-vn', '-af',
                   'loudnorm=I=-19:TP=-2:LRA=7:print_format=json', '-f', 'null', '-', capture=True)
    (work / 'loudness.txt').write_text(loudness.stderr, encoding='utf-8')
    stats, _ = json.JSONDecoder().raw_decode(loudness.stderr[loudness.stderr.rfind('{'):])
    assert float(stats['input_tp']) < -1
    report = {
        'file': 'brag.mp4', 'sha256': hashlib.sha256((HERE / 'brag.mp4').read_bytes()).hexdigest(),
        'bytes': (HERE / 'brag.mp4').stat().st_size,
        'width': video['width'], 'height': video['height'], 'fps': video['r_frame_rate'],
        'frames': int(video['nb_frames']), 'seconds': float(probe['format']['duration']),
        'video_codec': video['codec_name'], 'audio_codec': audio['codec_name'],
        'audio_hz': audio['sample_rate'], 'audio_channels': audio['channels'],
        'integrated_lufs': stats['input_i'], 'true_peak_dbtp': stats['input_tp'],
        'hyperframes_version': '0.9.26', 'check_passed': check['ok'],
        'contrast_checked': check['contrast']['checked'], 'contrast_passed': check['contrast']['passed'],
        'layout_errors': check['layout']['errorCount'],
        'poster_source_seconds': 5.5, 'poster_baked_frame': 0,
        'audio_copied_through_poster_encode': True, 'full_decode_passed': True,
        'source': 'Reviewed synthetic-message native emulator capture; no physical phone access',
        'known_warning': 'Nested video start is deliberately composition-local at 4 seconds',
    }
    verification.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    for seconds in (0, 1.5, 5.5, 9, 10.9, 11.2, 12.5, 15.8, 17, 18.5, 20.5):
        run('ffmpeg', '-y', '-v', 'error', '-ss', str(seconds), '-i', HERE / 'brag.mp4',
            '-frames:v', '1', work / f'encoded-{seconds}.png')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
