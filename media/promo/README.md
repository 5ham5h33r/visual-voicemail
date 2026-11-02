# Voice Mail launch preview

A 32-second product film for LinkedIn, using the version 0.34 app. The story moves from one sample message to phrase playback, transcript search, the existing mailbox, on-device transcription, and Light/Dark/AMOLED. See the [creative brief and shot list](../../docs/product-launch-brief.md).

## Deliverables

- [Launch MP4](exports/voice-mail-product-demo.mp4): 1080x1350, 4:5, 60 fps, H.264/AAC, with fast-start metadata.
- [Cover](exports/voice-mail-product-demo-cover.png).
- [Optional captions](exports/voice-mail-product-demo.srt).
- [Posting copy](linkedin-post.md).
- [Internal soundtrack license record](MUSIC-LICENSE.md).
- [File and capture verification](exports/voice-mail-product-demo-verification.json).

<img src="exports/voice-mail-product-demo-cover.png" width="360" alt="Voice Mail launch cover with one sample message on a flat charcoal canvas">

The film shows real native app controls and their own card shapes. It includes no device border, physical rounded screen corners, camera cutout, or phone mockup. The new generic rectangular AVD retains the S24 Ultra's 384x832 dp app proportions. Editing crops to the relevant controls instead of shrinking full device screens. Native UI, type, and camera movement stay at 60 fps. No motion interpolation or generated intermediate text is used.

Eight fictional callers, reserved sample numbers, and scripted text supply the demo. The public playback fixture is muted and absent from the edit. The opening and ending label the preview/sample content. The film does not imply that scripted sample text is recognition output. All work on this edit used the emulator; the physical phone was not connected.

The emulator has no Mint entitlement. Previous phone checks establish the separately tested carrier path. The video does not promise independent carrier push, instant speech recognition, or public store availability. This repository remains private.

## Rectangular emulator

```sh
python research/create_demo_emulator.py
```

This creates a separate generic AVD in ignored research/analysis/rectangular-avd. It never copies another emulator's user data or overwrites an existing unrelated profile. Install the API 36 Google Play x86_64 system image first. Set ANDROID_AVD_HOME to that directory and start Voicemail_Rectangular after stopping the previous emulator:

```sh
emulator -avd Voicemail_Rectangular -port 5554 -gpu host -grpc 8554 -grpc-use-token -skin 720x1560 -no-window -no-snapshot-load -no-snapshot-save -no-audio
adb -s emulator-5554 shell wm size 720x1560
adb -s emulator-5554 shell wm density 300
```

Authorize debugging on the fresh emulator. Its 720x1560/300 dpi capture has the same logical layout as the normal 1440x3120/600 dpi preview. The generic hardware profile avoids Pixel camera and rounded-corner overlays. Use only emulator-5554, verify its active GLES renderer, and install the signed app. Seed it with the [public sample inbox](../../docs/emulator-testing.md).

## Capture and render

Install grpcio==1.76.0 and grpcio-tools==1.76.0 into ignored research/tools/grpc. Copy emulator/lib/emulator_controller.proto from the SDK and generate Python/grpc bindings with grpc_tools.protoc, including grpc_tools/_proto. The bindings use the [public Android emulator screenshot interface](https://android.googlesource.com/platform/prebuilts/android-emulator/+/master/linux-x86_64/lib/emulator_controller.proto). No authorization token or local discovery file is committed.

```sh
python media/promo/capture_stream.py
# Optional isolated retake:
python media/promo/capture_stream.py --scene playback
python media/promo/fetch_music.py
python media/promo/product_demo.py --preview
python media/promo/product_demo.py
```

The temporary native filming helper refuses real hardware, carrier sessions, unexpected IDs/counts, and numbers outside its reserved fixture range. A random per-run scene marker starts authenticated loopback capture only after the guards pass. The host reads only that dedicated log tag, records the native screenshot stream, and uses host NVENC encoding. No carrier mutation or call occurs. The helper restores synthetic inbox/transcripts and touched preferences in finally and reports restoration errors. It never ships in the app APK.

The playback take includes a two-second pause before opening search, giving the edit a clean reading hold. Copy demo-theme-{1,2,3}.png from the app external files directory to work/theme-{1,2,3}.png. Raw clips, detailed timing, music, and review frames remain ignored. Restore the normal emulator override to 1440x3120/600 dpi after filming.

The guest-side screenrecord fallback remains available for diagnostics; it was too slow for the final film. The current renderer requires host-stream clips. System fonts come from Segoe UI or a DejaVu fallback; font files are not redistributed.

The replacement soundtrack allows social-media advertising without public attribution. The film, cover, captions, MP4 description, and post copy contain no artist credit. Keep the internal license record for traceability. The original MP3 is not a standalone repository asset, and its license is separate from the code's MIT license.

## Verification and posting

The verification report records dimensions, exact frame count, timing, full-decode result, soundtrack loudness, file SHA-256, and source cadence. Review the actual encoded movie's opening, phrase jump, final search state, moving pages, three themes, and end card. Capture throughput and constant-rate output measure different parts of the pipeline; they do not establish Android performance on every device.

The complete rectangular-emulator capture passed its sample-data guards and restoration path. CI compiles the original Python sources and native helper; it does not film or judge the result. No app behavior changed in this media revision.

Upload the MP4 as a native LinkedIn video with the optional cover and SRT. Review the post copy before publication. This task creates deliverables without posting to LinkedIn. The historical 44-second engineering edit and older screenshot slideshow remain in Git history.
