"""Fetch the licensed soundtrack from the composer's official download link."""
from pathlib import Path
import hashlib
import subprocess

DESTINATION = Path(__file__).resolve().parent / "work/music/origami.mp3"
URL = "https://www.scottbuckley.com.au/library/wp-content/uploads/2025/10/Origami.mp3"
SHA256 = "8b8edd81ee2110a62169b48e3233e1556c0f9de10003b21f41a28e5b4bcbfe14"

if DESTINATION.exists():
    if hashlib.sha256(DESTINATION.read_bytes()).hexdigest() != SHA256:
        raise SystemExit("Existing music does not match the licensed source; refusing to replace it.")
else:
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    temporary = DESTINATION.with_suffix(".download")
    subprocess.run([
        "curl", "-fL", "--max-time", "90", "-A",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36",
        "-H", "Accept: */*", "-e", "https://www.scottbuckley.com.au/library/origami/",
        "-o", str(temporary), URL,
    ], check=True)
    if hashlib.sha256(temporary.read_bytes()).hexdigest() != SHA256:
        temporary.unlink()
        raise SystemExit("Downloaded music did not match the verified SHA-256.")
    temporary.replace(DESTINATION)
print("Verified Origami by Scott Buckley (CC BY 4.0). Keep MUSIC-CREDITS.md and the post credit.")
