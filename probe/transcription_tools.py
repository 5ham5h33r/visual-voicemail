"""Pinned upstream Android speech runtime. Models are downloaded separately by the app."""
import hashlib
import pathlib
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent / 'research/tools/transcription'
FILES = [
    ('sherpa-onnx-static-link-onnxruntime-2.0.1.aar',
     'https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/',
     '07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575'),
    ('kotlin-stdlib-2.2.0.jar',
     'https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.2.0/',
     '65d12d85a3b865c160db9147851712a64b10dadd68b22eea22a95bf8a8670dca'),
]


def prepare():
    ROOT.mkdir(parents=True, exist_ok=True)
    for name, base, checksum in FILES:
        path = ROOT / name
        if not path.exists():
            temporary = path.with_suffix('.part')
            urllib.request.urlretrieve(base + name, temporary)
            if hashlib.file_digest(temporary.open('rb'), 'sha256').hexdigest() != checksum:
                raise RuntimeError('Speech runtime checksum mismatch')
            temporary.replace(path)
        with path.open('rb') as source:
            if hashlib.file_digest(source, 'sha256').hexdigest() != checksum:
                raise RuntimeError('Speech runtime checksum mismatch')
    with zipfile.ZipFile(ROOT / FILES[0][0]) as archive:
        archive.extract('classes.jar', ROOT)
    return [ROOT / 'classes.jar', ROOT / FILES[1][0]], ROOT / FILES[0][0]


if __name__ == '__main__':
    prepare()
