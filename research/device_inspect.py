"""Read-only, narrowly scoped diagnostics for the user-authorized ADB device."""
import argparse
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent
ADB = ROOT / 'tools/platform-tools/adb.exe'
p = argparse.ArgumentParser()
p.add_argument('device')
a = p.parse_args()
prefix = [str(ADB), '-s', a.device]

def adb(*args):
    return subprocess.run(prefix + list(args), capture_output=True, timeout=30)

private = ROOT / 'device-private'
private.mkdir(exist_ok=True)
screen = adb('exec-out', 'screencap', '-p')
screen.check_returncode()
assert screen.stdout.startswith(b'\x89PNG\r\n\x1a\n')
(private / 'screen.png').write_bytes(screen.stdout)
print('Screenshot saved:', private / 'screen.png')

for package in ['com.tmobile.vvm.application', 'com.vna.service.vvm']:
    paths = adb('shell', 'pm', 'path', package).stdout.decode().splitlines()
    if not paths:
        print(package, 'not installed')
        continue
    data = adb('shell', 'dumpsys', 'package', package).stdout.decode(errors='replace')
    lines = [s.strip() for s in data.splitlines() if any(k in s for k in
             ['versionName=', 'versionCode=', 'READ_PHONE_STATE:', 'READ_PRIVILEGED_PHONE_STATE:',
              'INTERNET:', 'CHANGE_NETWORK_STATE:', 'SEND_SMS:', 'RECEIVE_SMS:', 'pkgFlags='])]
    print(package, json.dumps(lines))
    (private / f'{package}-package-summary.json').write_text(json.dumps(lines, indent=2))
    out = private / package
    out.mkdir(exist_ok=True)
    for line in paths:
        remote = line.removeprefix('package:').strip()
        dest = out / pathlib.PurePosixPath(remote).name
        result = adb('pull', remote, str(dest))
        result.check_returncode()
        print('Pulled', dest.name, dest.stat().st_size, 'bytes')

role = adb('shell', 'cmd', 'role', 'get-role-holders', 'android.app.role.DIALER')
print('Default dialer:', role.stdout.decode().strip())
