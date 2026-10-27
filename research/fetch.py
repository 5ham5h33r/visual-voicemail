"""Download public research inputs and record their original URLs."""
import argparse
import hashlib
import json
import pathlib
import urllib.request

p = argparse.ArgumentParser()
p.add_argument('url')
p.add_argument('output')
args = p.parse_args()
out = pathlib.Path(args.output)
out.parent.mkdir(parents=True, exist_ok=True)
req = urllib.request.Request(args.url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=45) as response:
    data = response.read()
    metadata = {'source': args.url, 'final_url': response.url,
                'content_type': response.headers.get('Content-Type'),
                'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
out.write_bytes(data)
out.with_suffix(out.suffix + '.source.json').write_text(json.dumps(metadata, indent=2))
print(json.dumps(metadata))
