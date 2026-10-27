"""Fetch the exact public base APKs described by saved Aptoide metadata."""
import datetime
import hashlib
import json
import pathlib
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = pathlib.Path(__file__).resolve().parent

def fetch(label):
    data = json.loads((ROOT / 'downloads' / f'{label}-aab.json').read_text())['nodes']['meta']['data']
    apk = data['file']
    dest = ROOT / 'downloads' / f"{data['package']}-{apk['vername']}.apk"
    with urllib.request.urlopen(apk['path'], timeout=45) as response:
        raw = response.read()
    assert len(raw) == apk['filesize'], 'Incorrect APK size'
    assert hashlib.md5(raw).hexdigest() == apk['md5sum'], 'Incorrect APK checksum'
    dest.write_bytes(raw)
    record = {'package': data['package'], 'version': apk['vername'],
              'version_code': apk['vercode'], 'source_url': apk['path'],
              'fetched_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'size': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
              'catalog_signing_certificate_sha1': apk['signature']['sha1'],
              'path': str(dest.relative_to(ROOT))}
    dest.with_suffix('.apk.source.json').write_text(json.dumps(record, indent=2))
    return record

with ThreadPoolExecutor(max_workers=2) as pool:
    for record in pool.map(fetch, ['myvvm', 'tmobile']):
        print(json.dumps(record))
