"""Build/run local instrumentation without logging caller data or recognized text."""
import argparse
import os
import pathlib
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--test', choices=['recording', 'onboarding', 'quality', 'navigation', 'bulk', 'header', 'settings', 'selection', 'transition', 'inbox-navigation', 'notification-eligibility'], default='recording')
parser.add_argument('--serial', help='ADB device serial; omit only with one connected device')
parser.add_argument('--speech-fixture', help='Public 16 kHz PCM WAV used only by the bulk check')
parser.add_argument('--compile-only', action='store_true')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
tools = root / 'research/tools'
bt = tools / 'build-tools/android-16'
java = next((tools / 'jre21').glob('*/bin/java.exe'), None) or shutil.which('java')
if not java:
    raise SystemExit('Install Java 17+ and build the app first.')
name = {'recording': 'RecordingFlowCheck', 'onboarding': 'OnboardingFlowCheck', 'quality': 'QualityMotionCheck', 'navigation': 'NavigationCheck', 'bulk': 'BulkFlowCheck', 'header': 'InboxHeaderCheck', 'settings': 'SettingsSearchCheck', 'selection': 'SelectionMotionCheck', 'transition': 'SelectionTransitionCheck', 'inbox-navigation': 'InboxNavigationCheck', 'notification-eligibility': 'NotificationEligibilityCheck'}[args.test]
output = root / 'app/build' / ('device-' + args.test)
classes, dex = output / 'classes', output / 'dex'
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(exist_ok=True)

def run(*parts, capture=False):
    return subprocess.run([str(p) for p in parts], check=True, capture_output=capture, text=capture)

sources = sorted((root / 'app/device-tests').glob('*.java'))
run(java, '-jar', tools / 'ecj.jar', '-8', '-encoding', 'UTF-8', '-proc:none',
    '-bootclasspath', os.pathsep.join(map(str, [tools / 'android.jar', bt / 'core-lambda-stubs.jar'])),
    '-d', classes, *sources)
if args.compile_only:
    raise SystemExit(0)
adb = tools / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')
if not adb.exists():
    raise SystemExit('Install Android platform-tools under research/tools/platform-tools first.')
device = [adb] + (['-s', args.serial] if args.serial else [])
package = 'org.visualvoicemail.checks'
run(*device, 'get-state', capture=True)
existing = subprocess.run([str(p) for p in [*device, 'shell', 'pm', 'path', package]], capture_output=True, text=True).stdout
if existing.strip():
    raise SystemExit('The temporary check package is already installed; refusing to replace it.')
manifest = output / 'AndroidManifest.xml'
manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
<application android:label="Voicemail UI checks" android:debuggable="true"/>
<instrumentation android:name=".{name}" android:targetPackage="org.visualvoicemail.app"/>
</manifest>''', encoding='utf-8')
run(java, '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26',
    '--lib', tools / 'android.jar', '--output', dex, *classes.rglob('*.class'))
unsigned, apk = output / 'unsigned.apk', output / 'checks.apk'
run(bt / ('aapt2.exe' if os.name == 'nt' else 'aapt2'), 'link', '-I', tools / 'android.jar',
    '--manifest', manifest, '-o', unsigned)
with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex / 'classes.dex', 'classes.dex')
    if args.test == 'bulk':
        if not args.speech_fixture or not pathlib.Path(args.speech_fixture).is_file():
            raise SystemExit('--test bulk requires --speech-fixture with a public PCM WAV fixture.')
        archive.write(args.speech_fixture, 'assets/speech-fixture.wav')
run(java, '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', root / 'app/local/research-debug.p12',
    '--ks-pass', 'pass:android', '--ks-key-alias', 'research', '--out', apk, unsigned)
run(*device, 'shell', 'am', 'force-stop', 'org.visualvoicemail.app')
run(*device, 'install', apk)
try:
    result = run(*device, 'shell', 'am', 'instrument', '-w', package + '/.' + name, capture=True).stdout
    print(result)
    if 'passed=true' not in result or 'INSTRUMENTATION_CODE: -1' not in result:
        raise SystemExit('Device UI checks failed.')
finally:
    run(*device, 'uninstall', package)
