# Local device checks

These instrumentation sources use reflection to exercise the real native views and player. Results contain counts/booleans, without caller details or transcript text. They are compiled in CI, and run separately on an authorized Android device.

Build/install the project's debug APK first. The device must have a cached voicemail with a completed transcript. Playback is muted for the recording checks. The onboarding checks use sample actions, then open a real recording without playing it. Neither check changes carrier read flags or deletes messages. Introduction preferences are restored. The runner stops the app, installs a temporary check package signed with the local debug key, runs it, and removes that package.

```sh
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test recording
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test onboarding
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test quality
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test navigation
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test header
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test settings
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test selection
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test transition
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test inbox-navigation
```

Use `--compile-only` to type-check all sources without accessing a phone. Full speech recognition and leading-silence checks were run separately on the S24 Ultra; see docs/transcription.md for results and limits.

The quality check switches between Standard, Enhanced, and Highest quality without regenerating or downloading a personal transcript. It checks both entry points, retains the cached text, exercises actual tap/cancel events, and restores the original quality/reduced-motion settings.

Navigation checks exercise normal parent stacks and isolated pages opened by tooling. They use an existing cached voicemail without playing or changing it, and check both top-left and system Back.


Bulk checks require Standard already downloaded and a supplied public 16 kHz PCM WAV speech fixture. The local validation used the upstream sherpa-onnx tiny.en test WAV, converted to 16 kHz mono with two seconds of leading silence and one second of trailing silence. Do not supply personal audio. CI only compiles these checks; it does not access a phone or audio fixture.

```sh
python research/check_device_ui.py --serial YOUR_ADB_SERIAL --test bulk --speech-fixture /path/to/public-speech-fixture.wav
```

Selection checks use synthetic inbox rows and restore the original encrypted deletion marks and preferences. Speech checks use temporary public audio in the app cache, then remove that audio and its transcripts. Bulk carrier write behavior is covered by host tests with a fake carrier; no personal read flags are changed in the phone test.

Header checks inject synthetic inbox rows only into temporary views, exercise native touch/fling, search and keyboard, filters, short-list behavior, selection, Back, recreation, Settings return, and reduced motion. They do not request audio or change carrier flags. Original app preferences are restored.

Settings checks cover horizontal header alignment, real filtered controls, no-results states, switching and restoring Reduce motion, keyboard dismissal, child-page return, query/cursor recreation, clear focus/visibility, scroll restoration during a fling, and Back to the inbox. Only temporary app preferences are changed and restored; no carrier messages, downloads, folders, or audio are modified.

Selection motion checks use three synthetic rows and observe the animated glyph on the native checkbox, retained row/widget instances, immediate checked/model state, color completion, rapid toggles, Select all, interrupted mode transitions, accessibility, and reduced motion. They restore app preferences and never request carrier audio or change carrier flags.

Transition checks inject synthetic inbox rows, perform a real long press, sample rendered header/list coordinates, physically tap the selection close and normal Settings controls, and check reversal, scrolled selection, reduced motion, and recreation during a transition. App preferences are restored; no carrier flags, downloads, or personal audio are changed.

Inbox-navigation checks exercise actual horizontal swipes through All/Unread/Saved, vertical scrolling, first/last boundaries, cancelled gestures, rapid tab changes, rendered transition frames, query preservation, scope-specific empty messages and recovery actions, the inbox menu selection route, and reduced motion. Selection checks also inject a sample transcript in memory and verify preview/status alignment with caller text when entering and leaving selection. Fixtures never change carrier flags or saved transcripts.

Temporary test activities use FLAG_KEEP_SCREEN_ON without changing phone preferences. Physical gesture checks require the test app to hold foreground focus. Navigation also checks direct finger tracking, a visible neighboring-page gap, cached view reuse, and selection through the actual themed popup.

`--test notification-eligibility` performs six read-only capability queries in the app UID: default dialer, carrier privileges, system-app status, MODIFY_PHONE_STATE, READ_PHONE_STATE, and RECEIVE_SMS. A successful run reports the actual booleans; it does not prove push delivery or grant/change any role, permission, registration, or provisioning.

The `saved-mailbox` check requires `--speech-fixture` with public PCM WAV audio. It uses a random isolated app-files directory, exercises saved-recording persistence and playback, and removes only its own fixtures. No carrier requests are made. Bulk tests cancel the carrier removal confirmation; archive request ordering and partial outcomes are checked by the host simulated-carrier suite.
