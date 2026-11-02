# Emulator UI development

UI development can continue on an Android emulator while the physical phone is unavailable. The APK includes x86_64 native speech libraries. An emulator has no verified Mint entitlement or carrier push path, so its results are not evidence of carrier authentication, deletion, or greeting changes.

## Primary viewport: S24 Ultra

The active emulator uses 1440 × 3120 pixels and 600 dpi, matching the measured S24 Ultra display configuration in this session: a 384 dp wide app viewport at normal text size. Configure the emulator explicitly:

```powershell
adb -s emulator-5554 shell wm size 1440x3120
adb -s emulator-5554 shell wm density 600
adb -s emulator-5554 shell wm size
adb -s emulator-5554 shell wm density
```

These commands target only the emulator. They change its display override, not the phone or the AVD's base hardware definition. The emulator runs Android API 36 with standard Android system UI; matching its display does not reproduce Samsung One UI, carrier provisioning, or exact hardware performance.

The initial Pixel 9 viewport (1080 × 2424 at 420 dpi, about 411 dp wide) also passed the recording/layout/Settings/navigation checks. Per-activity 320 × 480 dp checks at 160%/200% text remain the narrow/short stress configuration. Keep S24 Ultra as the main preview while retaining those other sizes. To restore the original emulator display, use `wm size reset` and `wm density reset` with the same explicit serial.

## Sample inbox

The explicit `emulator-setup` instrumentation action populates eight clearly labeled sample transcripts and copies a public WAV fixture into private playback storage. It refuses non-emulator hardware, any carrier session file, or an inbox containing non-sample identifiers. It never imports phone backups, credentials, contacts, or personal audio. It is not included in the application APK or normal first-use flow.

After installing the built APK on a disposable or unconnected development emulator, run:

```powershell
python research/check_device_ui.py --test emulator-setup --serial emulator-5554 --speech-fixture <public-16-kHz-mono-PCM.wav>
python research/check_device_ui.py --test recording --serial emulator-5554
python research/check_device_ui.py --test layout --serial emulator-5554
```

The fixture uses reserved sample caller numbers. Its labeled text is UI test data, not a recognition result for the WAV. No authentication session is created. Automatic transcription, refresh-on-open, and alerts are disabled for this sample inbox. Re-running setup is allowed only while all cached messages remain sample data and no carrier session exists.

Recording checks exercise local preparation while the carrier monitor is held, all five playback speeds, balanced spacing, phrase navigation/search, and intercepted call/share-sheet launches. Layout checks use per-activity overrides at 160%/200% font scaling with 320 × 480 dp bounds. They do not change the emulator's global font or display settings. Test packages are uninstalled after each run.

The full Large model requires much more memory than a typical 2 GB emulator provides. Model inference performance and carrier behavior remain separate from these UI checks. Existing S24 Ultra validations are recorded in the feature documents.

On the Pixel 9 API 36 emulator, version 0.27 passed 47 recording assertions and 412 adaptive-layout assertions across 112 visible-control observations. The same APK had already passed 47 recording and 455 adaptive-layout assertions on the S24 Ultra/API 37 before testing moved to the emulator. Different counts reflect visible content and platform layouts. Thirty Settings and 33 navigation assertions also passed on the emulator. Navigation waits for bounded window-focus readiness instead of assuming a fixed 450 ms transition on every host.

After applying the S24 Ultra display override, all 47 recording assertions passed again, and a physical row tap opened the paused recording with its sample transcript. The primary preview remains at that size.

## Version 0.31 recovery and accessibility checks

The approved follow-up pass adds emulator-only public-data checks. Run one instrumentation process at a time, after building and installing the current app:

```powershell
python research/check_device_ui.py --test first-use --serial emulator-5554
python research/check_accessibility.py --serial emulator-5554
python research/check_resilience.py --serial emulator-5554 --speech-fixture <public-16-kHz-mono-PCM.wav>
python research/check_device_ui.py --test public-speech --serial emulator-5554 --speech-fixture <public-16-kHz-mono-PCM.wav>
```

The TalkBack wrapper requires the TalkBack package already installed on the emulator. It temporarily enables that service, preserves other enabled services, and restores the exact prior secure settings in `finally`. The instrumentation checks focus/actions/state with the installed service active. It does not assess the audible spoken experience or enable accessibility services on a physical phone.

The resilience wrapper requires emulator-console authentication under the same user that owns the running emulator. It injects and ends one emulator-local incoming call; no real call is placed. The temporary test APK alone contains a fixture DocumentsProvider/URI-grant receiver. It exports generated public audio while carrier/inbox work is blocked, injects full-storage and revoked-access errors, verifies original and earlier-export retention, and exercises atomic-state failure, audio focus, and recreation. It restores the sample cache file and preferences. Never register these components in the app APK or run synthetic mailbox setup against the phone.

The public-speech check requires Standard/Tiny already downloaded and verified. It downloads no model, records no microphone audio, logs no recognized text, and runs actual local Whisper/VAD while the carrier monitor is held. Its synthetic Saved entry and generated transcript are removed afterward.

Explicit partial-transfer validation is separate:

```powershell
python research/check_device_ui.py --test download-recovery --download-stage pause --serial emulator-5554
python research/check_device_ui.py --test download-recovery --download-stage resume --serial emulator-5554
```

These two actions transfer the public 104 MB Tiny model. They require an unconnected sample emulator, preserve retained bytes across process termination, and verify all four pinned files. Do not run them as an incidental UI test or substitute Large on a memory-constrained emulator.

Version 0.31 passed 419 TalkBack/focus assertions (135 focus operations), a final 471 adaptive-layout assertions (125 control observations including the compact Update state), 42 recovery assertions including a simulated incoming call, 11 real local-inference assertions, 35 selection assertions, 33 guided-onboarding assertions, 41 first-use/discovery assertions, and 38 quality/motion assertions. The earlier matched-quality layout passed 480 assertions/128 observations; counts depend on visible content. Host checks passed 223 behavior assertions plus contrast in 24 combinations. See the [final review](ux-final-review.md) for the distinction between these checks and remaining human/carrier validation.


## Host GPU and demo capture

Use -gpu host when starting the AVD. Confirm the renderer rather than assuming GPU selection from the flag alone:

```powershell
adb -s emulator-5554 shell dumpsys SurfaceFlinger | Select-String 'GLES:'
```

Version 0.34 capture reported NVIDIA GeForce RTX 5070 Ti Laptop GPU as its active renderer. Guest-side screenrecord was still a bottleneck, so the [demo workflow](../media/promo/README.md) uses token-protected local screenshot streaming and host NVENC encoding. A 720x1560/300 dpi capture retains the same 384x832 dp layout as the normal 1440x3120/600 dpi preview. The normal display override and emulator-only overlays were restored after filming. No physical phone was used.
