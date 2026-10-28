"""Coordinate a synthetic incoming call with the emulator-only public-data recovery test."""
import argparse
import pathlib
import subprocess
import sys
import time

parser = argparse.ArgumentParser()
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--speech-fixture', required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
adb = [str(root / 'research/tools/platform-tools/adb.exe'), '-s', args.serial]


def run(*parts, capture=False, check=True):
    return subprocess.run([*adb, *parts], check=check, capture_output=capture, text=True)


hardware = run('shell', 'getprop', 'ro.hardware', capture=True).stdout.strip()
if not args.serial.startswith('emulator-') or hardware not in ('ranchu', 'goldfish'):
    raise SystemExit('Simulated call checks require an Android emulator.')
run('shell', 'run-as', 'org.visualvoicemail.app', 'rm', '-f', 'cache/resilience-ready-for-call')
number = '+12025550199'
process = subprocess.Popen([sys.executable, str(root / 'research/check_device_ui.py'), '--test',
                           'resilience', '--simulated-call', '--serial', args.serial,
                           '--speech-fixture', args.speech_fixture])
sent = False
try:
    deadline = time.monotonic() + 150
    while process.poll() is None and time.monotonic() < deadline:
        ready = run('shell', 'run-as', 'org.visualvoicemail.app', 'test', '-f',
                    'cache/resilience-ready-for-call', capture=True, check=False)
        if ready.returncode == 0:
            result = run('emu', 'gsm', 'call', number, capture=True)
            if 'OK' not in result.stdout or 'KO:' in result.stdout:
                raise RuntimeError('Emulator console did not accept the simulated call.')
            sent = True
            print('Injected a local emulator incoming call.', flush=True)
            time.sleep(2)
            break
        time.sleep(.15)
    if not sent and process.poll() is None:
        raise RuntimeError('Recovery test did not reach the simulated-call checkpoint.')
    code = process.wait(timeout=45)
    if code:
        raise SystemExit(code)
finally:
    if sent:
        run('emu', 'gsm', 'cancel', number, capture=True, check=False)
        print('Ended the local emulator call.', flush=True)
    if process.poll() is None:
        try:
            process.wait(timeout=35)
        except subprocess.TimeoutExpired:
            run('shell', 'am', 'force-stop', 'org.visualvoicemail.app', capture=True, check=False)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=15)
