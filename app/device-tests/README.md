# Local device checks

These instrumentation sources use reflection to exercise the real native views and player. Results contain counts/booleans, without caller details or transcript text. They are compiled in CI, and run separately on an authorized Android device.

Build/install the project's debug APK first. The device must have a cached voicemail with a completed transcript. Playback is muted for the recording checks. The onboarding checks use sample actions, then open a real recording without playing it. Neither check changes carrier read flags or deletes messages. Introduction preferences are restored. The runner stops the app, installs a temporary check package signed with the local debug key, runs it, and removes that package.

```sh
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test recording
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test onboarding
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test quality
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test navigation
```

Use `--compile-only` to type-check both sources without accessing a phone. Full speech recognition and leading-silence checks were run separately on the S24 Ultra; see docs/transcription.md for results and limits.

The quality check switches between Standard and Enhanced without regenerating or downloading a personal transcript. It checks both entry points, retains the cached text, exercises actual tap/cancel events, and restores the original quality/reduced-motion settings.

Navigation checks exercise normal parent stacks and isolated pages opened by tooling. They use an existing cached voicemail without playing or changing it, and check both top-left and system Back.
