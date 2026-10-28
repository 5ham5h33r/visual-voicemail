# Emulator UI development

UI development can continue on an Android emulator while the physical phone is unavailable. The APK includes x86_64 native speech libraries. An emulator has no verified Mint entitlement or carrier push path, so its results are not evidence of carrier authentication, deletion, or greeting changes.

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
