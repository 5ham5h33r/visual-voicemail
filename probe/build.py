"""Build the original research APK with the pinned, local Android toolchain."""
import pathlib
import os
import shutil
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent
TOOLS = ROOT.parent / 'research/tools'
WINDOWS = os.name == 'nt'
EXEC = '.exe' if WINDOWS else ''
bundled_java = list((TOOLS / 'jre21').glob('*/bin/java.exe'))
system_java = shutil.which('java')
if bundled_java:
    JAVA = bundled_java[0]
elif system_java:
    JAVA = pathlib.Path(system_java).resolve()
else:
    raise SystemExit('Install Java 17+ before building the probe.')
KEYTOOL = JAVA.with_name('keytool' + EXEC)
BUILD_TOOLS = TOOLS / 'build-tools/android-16'
BUILD = ROOT / 'build'
CLASSES = BUILD / 'classes'
DEX = BUILD / 'dex'
LOCAL = ROOT / 'local'
for path in (CLASSES, DEX, LOCAL):
    path.mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

sources = sorted((ROOT / 'src').rglob('*.java'))
for compiled in CLASSES.rglob('*.class'):
    if not compiled.resolve().is_relative_to(CLASSES.resolve()):
        raise RuntimeError('Unexpected class file outside the build directory')
    compiled.unlink()
run(JAVA, '-jar', TOOLS / 'ecj.jar', '-8', '-encoding', 'UTF-8', '-proc:none',
    '-bootclasspath', os.pathsep.join(map(str, [TOOLS / 'android.jar', BUILD_TOOLS / 'core-lambda-stubs.jar'])),
    '-d', CLASSES, *sources)
run(JAVA, '-cp', BUILD_TOOLS / 'lib/d8.jar', 'com.android.tools.r8.D8',
    '--min-api', '26', '--lib', TOOLS / 'android.jar', '--output', DEX,
    *sorted(CLASSES.rglob('*.class')))
unsigned = BUILD / 'probe-unsigned.apk'
run(BUILD_TOOLS / ('aapt2' + EXEC), 'link', '-I', TOOLS / 'android.jar',
    '--manifest', ROOT / 'AndroidManifest.xml', '-o', unsigned)
with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(DEX / 'classes.dex', 'classes.dex')
keystore = LOCAL / 'research-debug.p12'
if not keystore.exists():
    run(KEYTOOL, '-genkeypair', '-keystore', keystore, '-storepass', 'android',
        '-alias', 'research', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '365', '-dname', 'CN=Visual Voice Mail Research Debug')
apk = BUILD / 'vvm-research-probe.apk'
run(JAVA, '-jar', BUILD_TOOLS / 'lib/apksigner.jar', 'sign', '--ks', keystore,
    '--ks-pass', 'pass:android', '--ks-key-alias', 'research', '--out', apk, unsigned)
run(JAVA, '-jar', BUILD_TOOLS / 'lib/apksigner.jar', 'verify', '--verbose', apk)
print(apk)
