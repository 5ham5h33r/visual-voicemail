"""Capture guarded sample-emulator interactions through its authenticated local gRPC stream.

Requires SDK emulator_controller bindings in research/tools/grpc. Never connects to a phone.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
WORK = Path(__file__).resolve().parent / "work"
sys.path.insert(0, str(ROOT / "research/tools/grpc"))
import grpc
import emulator_controller_pb2 as pb
import emulator_controller_pb2_grpc as api

ADB = ROOT / "research/tools/platform-tools/adb.exe"
SERIAL = "emulator-5554"
SCENES = {"inbox", "search", "playback", "quality", "selection"}


def emulator():
    hardware = subprocess.check_output(
        [str(ADB), "-s", SERIAL, "shell", "getprop", "ro.hardware"], text=True
    ).strip()
    if hardware not in {"ranchu", "goldfish"}:
        raise RuntimeError("Capture requires emulator-5554")
    discovery = Path(os.environ["LOCALAPPDATA"]) / "Temp/avd/running"
    for file in discovery.glob("pid_*.ini"):
        values = dict(
            line.split("=", 1)
            for line in file.read_text(encoding="utf-8").splitlines()
            if "=" in line
        )
        if values.get("port.serial") == "5554" and values.get("grpc.token"):
            port = int(values["grpc.port"])
            channel = grpc.insecure_channel(
                f"127.0.0.1:{port}",
                options=[("grpc.max_receive_message_length", 32 * 1024 * 1024)],
            )
            return api.EmulatorControllerStub(channel), [
                ("authorization", "Bearer " + values["grpc.token"])
            ]
    raise RuntimeError("Start the local emulator with -grpc 8554 -grpc-use-token")


class Capture(threading.Thread):
    def __init__(self, stub, metadata, scene, start, duration):
        super().__init__()
        self.stub, self.metadata = stub, metadata
        self.scene, self.start_time, self.duration = scene, start, duration
        self.call = None
        self.stop_requested = threading.Event()
        self.error = None

    def stop(self):
        self.stop_requested.set()
        if self.call is not None:
            self.call.cancel()

    def run(self):
        process = None
        timestamps, sequences = [], []
        written, last = 0, None
        try:
            self.call = self.stub.streamScreenshot(
                pb.ImageFormat(format=pb.ImageFormat.RGB888, width=720, height=1560),
                metadata=self.metadata,
                timeout=self.duration + 10,
            )
            try:
                for image in self.call:
                    if (image.format.width, image.format.height) != (720, 1560):
                        raise RuntimeError("Use -skin 720x1560 -dpi-device 300 for filming")
                    if len(image.image) != 720 * 1560 * 3:
                        raise RuntimeError("Incomplete emulator image")
                    timestamps.append(image.timestampUs / 1e6)
                    sequences.append(image.seq)
                    if process is None:
                        process = subprocess.Popen(
                            [
                                "ffmpeg", "-y", "-v", "error", "-f", "rawvideo",
                                "-pix_fmt", "rgb24", "-s", "720x1560", "-r", "60",
                                "-i", "pipe:0", "-an", "-c:v", "h264_nvenc",
                                "-preset", "p5", "-tune", "hq", "-rc", "vbr",
                                "-cq", "16", "-b:v", "0", "-pix_fmt", "yuv420p",
                                "-movflags", "+faststart", str(WORK / (self.scene + "-stream.mp4")),
                            ], stdin=subprocess.PIPE,
                        )
                    target = min(self.duration * 60, max(0, round((timestamps[-1] - self.start_time) * 60)))
                    while written < target:
                        process.stdin.write(last if last is not None else image.image)
                        written += 1
                    last = image.image
                    if self.stop_requested.is_set():
                        break
            except grpc.RpcError as error:
                if not self.stop_requested.is_set() or error.code() != grpc.StatusCode.CANCELLED:
                    raise
            if process is None or last is None:
                raise RuntimeError("No native emulator frames received")
            while written < self.duration * 60:
                process.stdin.write(last)
                written += 1
            process.stdin.close()
            if process.wait(timeout=30):
                raise RuntimeError("Host video encoding failed")
            deltas = [b - a for a, b in zip(timestamps, timestamps[1:])]
            report = {
                "scene": self.scene, "native_frames": len(timestamps),
                "output_frames": written, "fps": 60, "duration": self.duration,
                "native_timestamps_seconds": [round(t - self.start_time, 6) for t in timestamps],
                "native_sequences": sequences,
                "median_native_interval_ms": sorted(deltas)[len(deltas) // 2] * 1000 if deltas else None,
                "method": "Authenticated loopback emulator screenshot stream; original timing; no interpolation",
            }
            (WORK / (self.scene + "-capture.json")).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
            print(f"{self.scene}: {len(timestamps)} native frames, {written} output frames", flush=True)
        except BaseException as error:
            self.error = error
            if process is not None and process.poll() is None:
                process.kill()
                process.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scene", choices=["all", "playback"], default="all")
    args = parser.parse_args()
    WORK.mkdir(exist_ok=True)
    stub, metadata = emulator()
    token = uuid.uuid4().hex
    # Only this tag's synthetic scene markers are read; authorization tokens are never logged.
    logcat = subprocess.Popen(
        [str(ADB), "-s", SERIAL, "logcat", "-v", "epoch", "-T", "1", "VoiceMailDemo:I", "*:S"],
        stdout=subprocess.PIPE, text=True, encoding="utf-8",
    )
    captures = []
    errors = []
    pattern = re.compile(r"^(\d+\.\d+).*?" + token + r" (START|END) (\w+)(?: (\d+))?$")

    def markers():
        active = None
        try:
            for line in logcat.stdout:
                match = pattern.match(line.strip())
                if not match:
                    continue
                timestamp, action, scene, duration = match.groups()
                if scene not in SCENES:
                    raise RuntimeError("Unexpected sample scene")
                if action == "START":
                    if active is not None:
                        raise RuntimeError("Overlapping capture markers")
                    active = Capture(stub, metadata, scene, time.time(), int(duration))
                    captures.append(active)
                    active.start()
                else:
                    if active is None or active.scene != scene:
                        raise RuntimeError("Unmatched capture marker")
                    active.stop()
                    active.join(timeout=35)
                    if active.is_alive():
                        raise RuntimeError("Capture did not finish")
                    if active.error:
                        raise active.error
                    active = None
        except BaseException as error:
            errors.append(error)
        finally:
            if active is not None:
                active.stop()

    watcher = threading.Thread(target=markers, daemon=True)
    watcher.start()
    try:
        with (WORK / "capture-instrumentation.log").open("w", encoding="utf-8") as output:
            result = subprocess.run(
                [sys.executable, str(ROOT / "research/check_device_ui.py"), "--test", "product-demo",
                 "--serial", SERIAL, "--demo-scene", args.scene, "--demo-capture-token", token],
                cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, timeout=240,
            )
        for capture in captures:
            capture.join(timeout=35)
        expected = 1 if args.scene == "playback" else 5
        if result.returncode or errors or len(captures) != expected or any(c.error or c.is_alive() for c in captures):
            raise RuntimeError("Sample capture failed; inspect work/capture-instrumentation.log and local capture reports") from (errors[0] if errors else None)
    finally:
        for capture in captures:
            capture.stop()
        logcat.terminate()
        logcat.wait(timeout=5)
        watcher.join(timeout=5)
    print("Native capture and fixture restoration passed.")


if __name__ == "__main__":
    main()
