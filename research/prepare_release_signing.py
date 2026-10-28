"""Create a long-lived local release key once; never upload private signing files."""
import argparse
import json
import os
import pathlib
import secrets
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--migrate-debug', action='store_true',
                    help='Authorize an upgrade from this checkout\'s installed development key')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parent.parent
local = root / 'app/local'
local.mkdir(parents=True, exist_ok=True)
config = local / 'release-signing.json'
if config.exists():
    raise SystemExit('Release signing already exists. Reuse and back up app/local; no files changed.')
java = next((root / 'research/tools/jre21').glob('*/bin/java.exe'), None) or shutil.which('java')
if not java:
    raise SystemExit('Prepare the Java toolchain first.')
keytool = pathlib.Path(java).with_name('keytool.exe' if pathlib.Path(java).suffix == '.exe' else 'keytool')
keystore, password = local / 'voice-mail-release.p12', local / 'voice-mail-release.password'
lineage, development = local / 'release.lineage', local / 'research-debug.p12'
if any(p.exists() for p in (keystore, password, lineage)):
    raise SystemExit('Existing signing files found; refusing to replace any signing identity.')
if args.migrate_debug and not development.is_file():
    raise SystemExit('Existing development key is required for its upgrade lineage.')
with os.fdopen(os.open(password, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w', encoding='ascii') as output:
    output.write(secrets.token_urlsafe(48) + '\n')
subprocess.run([str(keytool), '-genkeypair', '-storetype', 'PKCS12', '-keystore', str(keystore),
                '-storepass:file', str(password), '-keypass:file', str(password),
                '-alias', 'voice-mail-release', '-keyalg', 'RSA', '-keysize', '3072',
                '-validity', '10000', '-dname', 'CN=Voice Mail Release,O=Visual Voice Mail'], check=True)
keystore.chmod(0o600)
release = dict(keystore='app/local/voice-mail-release.p12', alias='voice-mail-release',
               password_file='app/local/voice-mail-release.password')
data = dict(signers=[release])
if args.migrate_debug:
    subprocess.run([str(java), '-jar', str(root / 'research/tools/build-tools/android-16/lib/apksigner.jar'),
                    'rotate', '--out', str(lineage), '--old-signer', '--ks', str(development),
                    '--ks-key-alias', 'research', '--ks-pass', 'pass:android',
                    '--set-installed-data', 'true', '--set-rollback', 'false',
                    '--new-signer', '--ks', str(keystore), '--ks-key-alias', release['alias'],
                    '--ks-pass', 'file:' + str(password)], check=True)
    data['signers'].insert(0, dict(keystore='app/local/research-debug.p12', alias='research', development_key=True))
    data['lineage'] = 'app/local/release.lineage'
with config.open('x', encoding='utf-8') as output:
    json.dump(data, output, indent=2)
    output.write('\n')
print('Created local release signing. Keep an encrypted backup of app/local; do not commit or upload it.')
