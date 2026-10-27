"""Run plain-Java inbox matching tests with the already pinned compiler/runtime."""
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
TOOLS = ROOT / 'research/tools'
bundled = list((TOOLS / 'jre21').glob('*/bin/java.exe'))
JAVA = str(bundled[0]) if bundled else shutil.which('java')
if not JAVA:
    raise SystemExit('Install Java 17+ and run probe/bootstrap.py first.')
output = ROOT / 'app/build/query-tests'
output.mkdir(parents=True, exist_ok=True)
subprocess.run([JAVA, '-jar', str(TOOLS / 'ecj.jar'), '-8', '-encoding', 'UTF-8', '-proc:none',
                '-d', str(output), str(ROOT / 'app/src/org/visualvoicemail/app/InboxQuery.java'),
                str(ROOT / 'app/tests/InboxQueryTest.java'),
                str(ROOT / 'app/src/org/visualvoicemail/app/RecordingCache.java'),
                str(ROOT / 'app/tests/SettingsLogicTest.java'),
                str(ROOT / 'app/src/org/visualvoicemail/app/ExportAudioFormat.java'),
                str(ROOT / 'app/tests/ExportAudioFormatTest.java'),
                str(ROOT / 'app/src/org/visualvoicemail/app/NotificationLedger.java'),
                str(ROOT / 'app/tests/NotificationLedgerTest.java')], check=True)
subprocess.run([JAVA, '-cp', str(output), 'org.visualvoicemail.app.InboxQueryTest'], check=True)
subprocess.run([JAVA, '-cp', str(output), 'org.visualvoicemail.app.SettingsLogicTest'], check=True)
subprocess.run([JAVA, '-cp', str(output), 'org.visualvoicemail.app.ExportAudioFormatTest'], check=True)
subprocess.run([JAVA, '-cp', str(output), 'org.visualvoicemail.app.NotificationLedgerTest'], check=True)
