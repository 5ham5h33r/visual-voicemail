"""Verify signatures and decompile public APKs; never launch the applications."""
import concurrent.futures
import json
import os
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent
JAVA = next((ROOT / 'tools' / 'jre21').glob('*/bin/java.exe'))
JADX = ROOT / 'tools/jadx-cli/lib/jadx-1.5.6-all.jar'
SIGNER = ROOT / 'tools/build-tools/android-16/lib/apksigner.jar'
env = dict(os.environ, JADX_CONFIG_DIR=str(ROOT / 'tools/jadx-config'),
           JADX_CACHE_DIR=str(ROOT / 'tools/jadx-cache'))

def analyze(label):
    data = json.loads((ROOT / 'downloads' / f'{label}-aab.json').read_text())['nodes']['meta']['data']
    version = data['file']['vername']
    apk = ROOT / 'downloads' / f"{data['package']}-{version}.apk"
    output = ROOT / 'analysis' / f'{label}-{version}'
    verify = subprocess.run([str(JAVA), '-jar', str(SIGNER), 'verify', '--verbose',
                             '--print-certs', str(apk)], capture_output=True, text=True)
    (ROOT / 'analysis' / f'{label}-signature.txt').write_text(verify.stdout + verify.stderr)
    if verify.returncode:
        raise RuntimeError(f'{label}: APK signature verification failed')
    with (ROOT / 'analysis' / f'{label}-jadx.log').open('w') as log:
        decompile = subprocess.run([str(JAVA), '-Xmx3g', '-cp', str(JADX), 'jadx.cli.JadxCLI',
                                   '--no-imports', '-j', '3', '-d', str(output), str(apk)],
                                  stdout=log, stderr=subprocess.STDOUT, env=env)
    return {'app': label, 'version': version, 'signature_verified': True,
            'jadx_exit_code': decompile.returncode, 'output': str(output)}

with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    for result in pool.map(analyze, ['tmobile', 'myvvm']):
        print(json.dumps(result), flush=True)
