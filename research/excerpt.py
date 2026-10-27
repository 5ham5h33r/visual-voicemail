"""Print numbered decompiler excerpts without large Kotlin metadata blobs."""
import argparse
import pathlib

p = argparse.ArgumentParser()
p.add_argument('path')
p.add_argument('start', type=int, nargs='?', default=1)
p.add_argument('end', type=int, nargs='?', default=250)
a = p.parse_args()
for n, line in enumerate(pathlib.Path(a.path).read_text(encoding='utf-8').splitlines(), 1):
    if a.start <= n <= a.end:
        print(f'{n}: {line}' if len(line) < 700 else f'{n}: [long metadata line omitted]')
