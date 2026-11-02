"""Fetch the licensed soundtrack from the official Mixkit download link."""
from pathlib import Path
import hashlib
import subprocess

DESTINATION = Path(__file__).resolve().parent / "work/music/stylz.mp3"
URL = "https://assets.mixkit.co/music/102/102.mp3"
SHA256 = "a0307e929eb60ed10488eacfdf8b3fddc893cf9b8680f5a55b65fb51b7075fdc"

if DESTINATION.exists():
    if hashlib.sha256(DESTINATION.read_bytes()).hexdigest() != SHA256:
        raise SystemExit("Existing music does not match the licensed source; refusing to replace it.")
else:
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    temporary = DESTINATION.with_suffix(".download")
    subprocess.run([
        "curl", "-fL", "--max-time", "90", "-A",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36",
        "-H", "Accept: */*", "-e", "https://mixkit.co/free-stock-music/electronic/",
        "-o", str(temporary), URL,
    ], check=True)
    if hashlib.sha256(temporary.read_bytes()).hexdigest() != SHA256:
        temporary.unlink()
        raise SystemExit("Downloaded music did not match the verified SHA-256.")
    temporary.replace(DESTINATION)
print("Verified launch soundtrack. See MUSIC-LICENSE.md for its source and permitted uses.")
