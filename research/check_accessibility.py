"""Temporarily enable installed TalkBack on an emulator and restore its exact prior settings."""
import argparse
import pathlib
import shlex
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--serial', default='emulator-5554')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
adb = [str(root / 'research/tools/platform-tools/adb.exe'), '-s', args.serial]


def run(*parts, capture=False):
    return subprocess.run([*adb, *parts], check=True, capture_output=capture, text=True)


hardware = run('shell', 'getprop', 'ro.hardware', capture=True).stdout.strip()
if not args.serial.startswith('emulator-') or hardware not in ('ranchu', 'goldfish'):
    raise SystemExit('This check only changes accessibility settings on an Android emulator.')
service = 'com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService'
run('shell', 'pm', 'path', 'com.google.android.marvin.talkback', capture=True)
keys = ['enabled_accessibility_services', 'accessibility_enabled']
before = {key: run('shell', 'settings', 'get', 'secure', key, capture=True).stdout.strip() for key in keys}
services = [] if before[keys[0]] in ('null', '') else before[keys[0]].split(':')
if service not in services:
    services.append(service)
try:
    run('shell', 'settings', 'put', 'secure', keys[0], shlex.quote(':'.join(services)))
    run('shell', 'settings', 'put', 'secure', keys[1], '1')
    subprocess.run([sys.executable, str(root / 'research/check_device_ui.py'), '--test', 'accessibility',
                    '--serial', args.serial], check=True)
finally:
    for key, value in before.items():
        if value == 'null':
            run('shell', 'settings', 'delete', 'secure', key)
        else:
            run('shell', 'settings', 'put', 'secure', key, shlex.quote(value))
    print('Restored the emulator accessibility settings.')
