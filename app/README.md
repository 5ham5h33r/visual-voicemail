# Voice Mail Android app

Original Android application for Mint's existing carrier voicemail mailbox. This milestone authenticates over Mint cellular data, displays the actual inbox, downloads recordings privately, and plays them through Android's native player. Samsung Phone remains the default dialer. The app has no carrier privileges. Network access is required; Contacts access is optional for caller-name lookup and search.

View the [screenshot gallery](../README.md#screenshots) for the inbox, player, settings, themes, and tutorial. Caller details are hidden in the documentation images.

The app icon combines voicemail reels with a mint speech bubble on a deep-green background. Adaptive launcher layers support device masks; Android 13+ receives an explicit monochrome layer for themed icons. The notification symbol uses the same mark. [Branding assets and native previews](../docs/branding/README.md).

Build with Python 3.11+ and Java 17+:

```sh
python probe/bootstrap.py
python probe/build.py --module app
```

The verified debug APK is `app/build/visual-voice-mail.apk`. The app uses its own package (`org.visualvoicemail.app`) and generated debug signing key, separate from the diagnostic probe. CI builds and verifies both modules. The app build downloads checksum-pinned sherpa-onnx 2.0.1 and Kotlin 2.2.0 artifacts from their official release hosts. Native libraries support arm64-v8a phones and x86_64 emulators, with 16 KB ELF load alignment. Models are downloaded on the phone, separately from the APK.

Authentication establishes an independent service instance and retrieves a service-instance token. The carrier mailbox interface requires its public `VVMOTT` profile/version headers, a subscriber `tel:` URI, and an encoded bearer token. These are protocol fields, not an official signing identity or an extracted credential. The app's User-Agent identifies this project.

Mailbox credentials and the inbox are persisted with authenticated AES-GCM encryption using a key held in Android Keystore. On reopening, the saved inbox appears immediately and downloaded recordings can play from app-private storage. A valid restored token can refresh the mailbox over Wi-Fi; initial authentication or renewal still needs Mint cellular data. Tokens and subscriber identifiers are never written to diagnostic output or sent to a project server.

Open the top-right Settings gear for appearance, contact names, message order, playback, sync, storage, privacy, permissions, and app information. Theme offers Use device setting, Light, Dark, and AMOLED (true black). AMOLED uses pure-black backgrounds with grouped dark cards. The recording detail player supports seeking, adjustable skip intervals, playback speed, and device/speaker/earpiece output choices. It displays the active audio route. Playback pauses when leaving the app and never automatically resumes on return. Opening a message reveals its player and transcript. See [Settings](../docs/settings.md) for options and validation.

The top-left carrier label uses the default mobile-data SIM's service-provider name and refreshes when returning to the inbox. It falls back to Mint Mobile when SIM information is unavailable. This display requires no new permission; the mailbox backend remains Mint-only. See [SIM carrier label](../docs/sim-carrier-label.md).

Four optional introduction/setup screens lead into spotlights on the real inbox and recording controls. The setup screen requests Contacts or notifications only after their Enable action. Examples are labeled and do not alter real messages. Replay from **Settings → Help & tutorial → App tutorial**. See [tutorial behavior and validation](../docs/tutorial.md).

Search filters the saved inbox by contact name, full/partial phone number, displayed or ISO date, and saved transcript text, with All, Unread, and Marked filters. Enable Settings → Contact names for local name lookup with Android's Contacts permission. Declining access keeps number/date search and playback available; names can also be turned off inside the app. Only numbers in the inbox are looked up, on a separate worker; matched names stay in memory and are not uploaded or saved in the inbox cache. Each message's three-dot menu offers carrier-synced mark-as-read/unread, a reversible local deletion mark, and **Save audio copy**. Marked messages stay in the mailbox and can be restored from the Marked filter. See [search and message actions](../docs/search-actions.md) for validation and limits.

Choose **Settings → Storage → Download location** for saved audio copies. The system folder picker grants access only to the selected folder and remembers it across app restarts. Save audio copy writes a separate AMR/AMR-WB file with a date-based, unique filename. Playback uses the private cache regardless of this setting. Changing or forgetting the folder keeps existing copies, and Clear playback cache leaves exported copies intact. No broad storage permission is required. Other apps with access to that folder can open its saved copies.

Enable **Settings → Notifications → New voicemail alerts** for background mailbox checks and a notification when new unread messages are discovered. The default interval is about 15 minutes, with 30/60-minute choices; Android can delay checks. The app asks for notification permission, offers Android channel controls and a labeled test, and keeps caller details out of alerts. Tapping an alert opens the unread inbox. Checks retrieve metadata, without downloading audio, and survive ordinary process exit. Force-stop pauses checks until reopening. See [notification behavior and validation limits](../docs/notifications.md).

No recording deletion, greeting, forwarding, PIN, or push-registration requests are implemented. Recordings are cached in the app's private storage, using atomic writes. English transcription runs locally using Whisper and Silero VAD after an optional Standard (104 MB) or Enhanced (161 MB) model download. Transcripts are encrypted at rest, searchable offline, and provide approximate phrase-based playback anchors. [Implementation and validation](../docs/transcription.md). See [design and device validation](../docs/inbox-design.md) for the earlier playback milestone and its limits.

Transcription quality is available in Settings and directly below the recording's Transcript heading. Plain-language choices include the model name in parentheses, explain speed/storage, and retain existing transcripts until Update transcript is selected. Shared tap feedback covers buttons, icons, inbox rows, settings, transcript phrases, and dialog actions; Reduce motion and Android animation settings are respected. See [motion behavior](../docs/motion.md).
