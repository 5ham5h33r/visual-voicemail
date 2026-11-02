# Voice Mail product demo

A 44-second engineering/product demo of version 0.34. Native app footage shows inbox swipes, contact/transcript search, phrase playback, transcription quality, and multi-selection. An animated diagram explains the carrier/private-storage/Whisper path, followed by Light/Dark/AMOLED captures. Focused crops keep the app readable in a LinkedIn feed.

## Deliverables

- [Product demo MP4](exports/voice-mail-product-demo.mp4): 1080x1350, 4:5, 60 fps, H.264 and stereo AAC, with fast-start metadata.
- [Cover image](exports/voice-mail-product-demo-cover.png).
- [Optional captions](exports/voice-mail-product-demo.srt). Main titles also appear in the picture.
- [Editable LinkedIn post](linkedin-post.md), including the required music credit.
- [Music source, credit, and license](MUSIC-CREDITS.md).

<img src="exports/voice-mail-product-demo-cover.png" width="360" alt="Voice Mail product demo cover with the current native inbox and original app icon">

The video labels its messages as synthetic. Filming uses eight reserved sample numbers, fictional names, and manually scripted text on the S24-shaped API 36 emulator. The displayed transcript text illustrates the interface; it is not recognition output from the public playback fixture. Voicemail audio is muted and excluded from the edit. The soundtrack is Origami by Scott Buckley, licensed under CC BY 4.0. No personal phone data, real contacts, credentials, or proprietary app footage are used.

The emulator has no Mint entitlement. Its demo label represents the tested carrier; real carrier compatibility comes from earlier, separate S24 Ultra checks. Version 0.34 work is emulator-only. The diagram distinguishes app-private audio from encrypted metadata. It makes no promise of instant carrier push or guaranteed transcription accuracy. The repository remains private, and the video makes no public download/availability claim.

## Motion and capture

The earlier guest-side screenrecord path captured roughly 15 frames per second, even with host GPU acceleration. Repeating those frames in a 60 fps export would retain the stutter. The replacement reads the emulator's native screenshot stream through its token-protected loopback gRPC interface and encodes on the host with NVIDIA NVENC. The active GLES renderer identifies the NVIDIA GeForce RTX 5070 Ti Laptop GPU. The stream preserves actual action timing; it uses no optical-flow interpolation or generated intermediate text.

Filming uses a 720x1560 framebuffer at 300 dpi: the same 384x832 dp app viewport as 1440x3120 at 600 dpi. This reduces transfer cost while retaining the S24 Ultra layout. Pixel-specific emulated camera/corner overlays are temporarily disabled for clean filming, then restored. This is not Samsung One UI or a hardware-performance benchmark. Continuous redraw is a temporary test-harness behavior, absent from the release app.

The release app separately updates playback progress on display frames. Text/status updates keep their slower timer; dragging, pending seeks, pause, and background behavior retain native control. The video renderer uses fractional camera transforms, anti-aliased panel masks, eased entrances, and short cross-dissolves. Diagram particles fade at their endpoints rather than visibly snapping to their starting positions.

## Capture and rebuild

Prepare an unauthenticated emulator with the [public sample inbox](../../docs/emulator-testing.md). Use the signed app on an emulator that already adopted the release signer; the temporary check APK uses the authorized instrumentation signing lineage. Never uninstall a personal installation to switch signing keys.

Launch the existing AVD with host rendering and local token authentication, for example:

```sh
emulator -avd Pixel_9 -port 5554 -gpu host -grpc 8554 -grpc-use-token -skin 720x1560 -dpi-device 300 -no-window -no-snapshot-load -no-snapshot-save -no-audio
adb -s emulator-5554 shell wm size 720x1560
adb -s emulator-5554 shell wm density 300
```

Use only emulator-5554. Install grpcio==1.76.0 and grpcio-tools==1.76.0 into ignored research/tools/grpc. Copy emulator/lib/emulator_controller.proto from the installed SDK there and generate its Python/grpc bindings using grpc_tools.protoc (including grpc_tools/_proto for Google's Empty message). The protocol comes from the [Android SDK's public emulator interface](https://android.googlesource.com/platform/prebuilts/android-emulator/+/master/linux-x86_64/lib/emulator_controller.proto). Bindings, authorization tokens, and local discovery files are never committed.

```sh
python media/promo/capture_stream.py
# Or repeat only playback:
python media/promo/capture_stream.py --scene playback
```

ProductDemoCapture refuses real hardware, carrier sessions, non-sample IDs, unexpected sample counts, and numbers outside the reserved fixture range. Only after these guards pass does it emit a random per-run scene marker that starts host capture. The host reads only that dedicated log tag. The helper temporarily replaces sample metadata/text, supplies fictional names in memory, and hides reserved number lines. It neither calls carrier mutation endpoints nor places a call. It restores the sample inbox/transcripts and touched preferences in finally and reports restoration errors. This helper runs only in a temporary test APK, never the app release.

The host saves work/{inbox,search,playback,quality,selection}-stream.mp4 and per-scene capture timing reports. Copy demo-theme-{1,2,3}.png from the app external files directory to work/theme-{1,2,3}.png. Raw footage, detailed timing, and review frames stay ignored. Inspect them before rendering. The fallback research/check_device_ui.py --test product-demo --serial emulator-5554 retains the slower guest recorder for diagnostics; the final renderer requires the host-stream clips.

With Python, Pillow, NumPy, FFmpeg, and the verified soundtrack:

```sh
python media/promo/fetch_music.py
python media/promo/product_demo.py --preview
python media/promo/product_demo.py
```

Fonts come from the local system (Segoe UI with a DejaVu fallback); no font files are redistributed. Keep the composer's attribution in the video and published post. The MP3 stays ignored. The repository MIT license does not replace the soundtrack's CC BY license.

The older render.py and voice-mail-linkedin.* exports are the historical version 0.11 screenshot preview. They remain in Git for reference and are superseded by this product demo.

## Verification

The [verification report](exports/voice-mail-product-demo-verification.json) records the final file's dimensions, frame count, cadence, loudness, full-decode result, byte size, and SHA-256. It also summarizes source capture counts. Native near-60 fps capture and a constant 60 fps edit address different parts of the recording pipeline; neither establishes performance on every Android device.

The complete native capture passed its emulator/data guards and restoration path. CI compiles the native helpers and media Python sources; it does not film or judge the rendered video. Reviewed encoded frames must include both moving pages and the final transcript-search state after the keyboard is dismissed.

## Posting

Upload the product demo as a native LinkedIn video with its optional cover and SRT. Review/edit the post copy and retain the music credit and links. The format fits LinkedIn's [published video requirements](https://www.linkedin.com/help/linkedin/answer/a7494039). This task creates files without posting to LinkedIn.
