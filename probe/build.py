"""Build app/probe APKs with the pinned local Android toolchain."""
import pathlib
import os
import shutil
import subprocess
import zipfile
import argparse
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--module', choices=['probe', 'app'], default='probe')
parser.add_argument('--release', action='store_true', help='Build a non-debuggable app release')
parser.add_argument('--unsigned', action='store_true', help='Validate release packaging without private keys; not installable')
parser.add_argument('--signing-config', help='Local JSON references to release keystores/password files')
options = parser.parse_args()
if (options.release and options.module != 'app') or (options.unsigned and not options.release):
    parser.error('Release packaging is supported only for app; --unsigned requires --release.')
if options.signing_config and not options.release:
    parser.error('--signing-config requires --release.')
ROOT = pathlib.Path(__file__).resolve().parent.parent / options.module
release_signing = []
if options.release and not options.unsigned:
    from signing_tools import signing_args
    config = pathlib.Path(options.signing_config) if options.signing_config else ROOT / 'local/release-signing.json'
    if not config.is_file():
        parser.error('Missing local signing configuration. Run research/prepare_release_signing.py first.')
    release_signing = signing_args(config, ROOT.parent)
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
BUILD = ROOT / 'build' / 'release' if options.release else ROOT / 'build'
CLASSES = BUILD / 'classes'
DEX = BUILD / 'dex'
LOCAL = ROOT / 'local'
for path in (CLASSES, DEX, LOCAL):
    path.mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

generated = BUILD / 'generated'
generated.mkdir(exist_ok=True)
manifest = ROOT / 'AndroidManifest.xml'
version = ET.parse(manifest).getroot().get('{http://schemas.android.com/apk/res/android}versionName')
if options.release:
    tree = ET.parse(manifest)
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    tree.getroot().find('application').set('{http://schemas.android.com/apk/res/android}debuggable', 'false')
    manifest = BUILD / 'AndroidManifest.xml'
    tree.write(manifest, encoding='utf-8', xml_declaration=True)
unsigned = BUILD / 'probe-unsigned.apk'
resources = ROOT / 'res'
compiled_resources = []
if resources.is_dir():
    resource_zip = BUILD / 'resources.zip'
    run(BUILD_TOOLS / ('aapt2' + EXEC), 'compile', '--dir', resources, '-o', resource_zip)
    compiled_resources = [resource_zip]
run(BUILD_TOOLS / ('aapt2' + EXEC), 'link', '-I', TOOLS / 'android.jar',
    '--manifest', manifest, '--java', generated, '-o', unsigned, *compiled_resources)
sources = sorted((ROOT / 'src').rglob('*.java')) + sorted(generated.rglob('*.java'))
dependencies, speech_archive = [], None
if options.module == 'app':
    from transcription_tools import prepare
    dependencies, speech_archive = prepare()
for compiled in CLASSES.rglob('*.class'):
    if not compiled.resolve().is_relative_to(CLASSES.resolve()):
        raise RuntimeError('Unexpected class file outside the build directory')
    compiled.unlink()
run(JAVA, '-jar', TOOLS / 'ecj.jar', '-8', '-encoding', 'UTF-8', '-proc:none',
    '-bootclasspath', os.pathsep.join(map(str, [TOOLS / 'android.jar', BUILD_TOOLS / 'core-lambda-stubs.jar'])),
    *(['-classpath', os.pathsep.join(map(str, dependencies))] if dependencies else []),
    '-d', CLASSES, *sources)
run(JAVA, '-cp', BUILD_TOOLS / 'lib/d8.jar', 'com.android.tools.r8.D8',
    *(['--release'] if options.release else []),
    '--min-api', '26', '--lib', TOOLS / 'android.jar', '--output', DEX,
    *sorted(CLASSES.rglob('*.class')), *dependencies)
with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(DEX / 'classes.dex', 'classes.dex')
    if speech_archive:
        with zipfile.ZipFile(speech_archive) as speech:
            for name in speech.namelist():
                if name.startswith(('jni/arm64-v8a/', 'jni/x86_64/')) and name.endswith('.so'):
                    archive.writestr(name.replace('jni/', 'lib/', 1), speech.read(name))
keystore = LOCAL / 'research-debug.p12'
if not options.release and not keystore.exists():
    run(KEYTOOL, '-genkeypair', '-keystore', keystore, '-storepass', 'android',
        '-alias', 'research', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '365', '-dname', 'CN=Visual Voice Mail Research Debug')
if options.release:
    if options.unsigned:
        apk = BUILD / ('visual-voice-mail-' + version + '-unsigned.apk')
        shutil.copyfile(unsigned, apk)
        print('Unsigned release packaging only; not installable: ' + str(apk))
        raise SystemExit(0)
    apk = BUILD / ('visual-voice-mail-' + version + '.apk')
    run(JAVA, '-jar', BUILD_TOOLS / 'lib/apksigner.jar', 'sign',
        '--debuggable-apk-permitted', 'false', '--v4-signing-enabled', 'false',
        *release_signing, '--out', apk, unsigned)
    run(JAVA, '-jar', BUILD_TOOLS / 'lib/apksigner.jar', 'verify', '--verbose', apk)
    print(apk)
    raise SystemExit(0)
apk = BUILD / ('visual-voice-mail.apk' if options.module == 'app' else 'vvm-research-probe.apk')
run(JAVA, '-jar', BUILD_TOOLS / 'lib/apksigner.jar', 'sign', '--ks', keystore,
    '--ks-pass', 'pass:android', '--ks-key-alias', 'research', '--out', apk, unsigned)
run(JAVA, '-jar', BUILD_TOOLS / 'lib/apksigner.jar', 'verify', '--verbose', apk)
print(apk)
