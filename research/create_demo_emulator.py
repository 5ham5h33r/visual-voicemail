"""Create a separate rectangular, generic Android AVD without copying user data."""
import argparse
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NAME = "Voicemail_Rectangular"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--sdk", type=Path, default=Path(
    os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    or str(Path.home() / "AppData/Local/Android/Sdk")))
args = parser.parse_args()
image = args.sdk.resolve() / "system-images/android-36/google_apis_playstore/x86_64"
if not (image / "system.img").is_file():
    raise SystemExit("Install the API 36 Google Play x86_64 image in Android Studio first.")
avd_root = ROOT / "research/analysis/rectangular-avd"
avd = avd_root / (NAME + ".avd")
config = avd / "config.ini"
if config.exists():
    values = dict(line.split("=", 1) for line in config.read_text(encoding="utf-8").splitlines()
                  if "=" in line)
    if values.get("AvdId") != NAME or values.get("hw.device.name") != "medium_phone":
        raise SystemExit("Existing AVD is not this demo profile; refusing to modify it.")
    print("The rectangular demo AVD already exists; its data and configuration were preserved.")
elif avd.exists() or (avd_root / (NAME + ".ini")).exists():
    raise SystemExit("Existing incomplete AVD profile; refusing to overwrite it.")
else:
    values = {
        "AvdId": NAME,
        "avd.ini.displayname": "Voicemail Rectangular S24 viewport",
        "avd.ini.encoding": "UTF-8",
        "PlayStore.enabled": "true",
        "abi.type": "x86_64",
        "tag.id": "google_apis_playstore",
        "target": "android-36",
        "image.sysdir.1": str(image) + os.sep,
        "hw.device.manufacturer": "Generic",
        "hw.device.name": "medium_phone",
        "hw.cpu.arch": "x86_64",
        "hw.cpu.ncore": "6",
        "hw.ramSize": "4096",
        "vm.heapSize": "336",
        "hw.lcd.width": "1440",
        "hw.lcd.height": "3120",
        "hw.lcd.density": "600",
        "hw.gpu.enabled": "yes",
        "hw.gpu.mode": "host",
        "hw.mainKeys": "no",
        "hw.keyboard": "yes",
        "hw.battery": "yes",
        "hw.audioInput": "yes",
        "hw.camera.back": "none",
        "hw.camera.front": "none",
        "disk.dataPartition.size": "6G",
        "showDeviceFrame": "no",
        "skin.dynamic": "yes",
        "skin.name": "1440x3120",
        "skin.path": "1440x3120",
        "fastboot.forceColdBoot": "yes",
        "fastboot.forceFastBoot": "no",
    }
    avd.mkdir(parents=True, exist_ok=True)
    config.write_text("\n".join(k + "=" + v for k, v in values.items()) + "\n", encoding="utf-8")
    (avd_root / (NAME + ".ini")).write_text(
        "avd.ini.encoding=UTF-8\npath=" + str(avd) + "\ntarget=android-36\n", encoding="utf-8")
    print("Created a separate generic rectangular AVD. No existing user data was copied.")
print("Set ANDROID_AVD_HOME to", avd_root)
print("Start", NAME, "with -port 5554 -gpu host and stop the previous emulator first.")
