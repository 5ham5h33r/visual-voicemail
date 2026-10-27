"""Fetch a fixed Android probe toolchain from official sources and verify SHA-256."""
import hashlib
import os
import pathlib
import platform
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent
TOOLS = ROOT.parent / 'research/tools'
TOOLS.mkdir(parents=True, exist_ok=True)
ANDROID = TOOLS / 'build-tools/android-16'
ARCHIVES = TOOLS / 'archives'
ARCHIVES.mkdir(exist_ok=True)
WINDOWS = os.name == 'nt'
if not WINDOWS and (platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'AMD64')):
    raise SystemExit('The probe toolchain currently supports Windows x64 and Linux x64.')

def fetch(name, url, checksum):
    existing = ROOT.parent / 'research/downloads' / name
    target = existing if existing.exists() else ARCHIVES / name
    if not target.exists():
        temporary = target.with_suffix(target.suffix + '.part')
        with urllib.request.urlopen(url, timeout=45) as source, temporary.open('wb') as output:
            while data := source.read(1024 * 1024):
                output.write(data)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != checksum:
            raise RuntimeError(f'Checksum mismatch: {name}')
        temporary.replace(target)
    if hashlib.sha256(target.read_bytes()).hexdigest() != checksum:
        raise RuntimeError(f'Checksum mismatch: {name}')
    return target

def extract(archive, entry, destination):
    with zipfile.ZipFile(archive) as source:
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(source.read(entry))
        if not WINDOWS and pathlib.PurePosixPath(entry).name == 'aapt2':
            destination.chmod(0o755)

sdk = fetch('platform36.zip', 'https://dl.google.com/android/repository/platform-36_r02.zip',
            '37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e')
extract(sdk, 'android-36/android.jar', TOOLS / 'android.jar')
if WINDOWS:
    archive = fetch('build-tools.zip', 'https://dl.google.com/android/repository/build-tools_r36.1_windows.zip',
                    '23189d2d52b40a070a05e9cf7e497c9563f67fee76902e8fd3135ef29ef4dbeb')
    entries = ['aapt2.exe', 'libwinpthread-1.dll']
else:
    archive = fetch('build-tools-linux.zip', 'https://dl.google.com/android/repository/build-tools_r36.1_linux.zip',
                    'a7b5889e4a79fcf3b0976bef40d401f4240fb1eed891d9d91169da1111e11d78')
    entries = ['aapt2', 'lib64/libc++.so', 'lib64/libc++.so.1']
for entry in entries + ['core-lambda-stubs.jar', 'lib/d8.jar', 'lib/apksigner.jar']:
    extract(archive, 'android-16/' + entry, ANDROID / entry)
ecj = fetch('ecj-3.42.0.jar', 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.42.0/ecj-3.42.0.jar',
            '29f6d3918ee02db4400c103bc25dd90a22491c3a395867d9393070cb96a7dd29')
(TOOLS / 'ecj.jar').write_bytes(ecj.read_bytes())
print('Verified Android SDK 36, build-tools 36.1, and ECJ 3.42.0. Java 17+ is required to build.')
