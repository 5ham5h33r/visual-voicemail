# Voice Mail Android app

Original Android application for Mint's existing carrier voicemail mailbox. This milestone authenticates over Mint cellular data, displays the actual inbox, downloads recordings privately, and plays them through Android's native player. Samsung Phone remains the default dialer. The app has no carrier privileges and requests only network permissions.

Build with Python 3.9+ and Java 17+:

```sh
python probe/bootstrap.py
python probe/build.py --module app
```

The verified debug APK is `app/build/visual-voice-mail.apk`. The app uses its own package (`org.visualvoicemail.app`) and generated debug signing key, separate from the diagnostic probe. CI builds and verifies both modules.

Authentication establishes an independent service instance and retrieves a service-instance token. The carrier mailbox interface requires its public `VVMOTT` profile/version headers, a subscriber `tel:` URI, and an encoded bearer token. These are protocol fields, not an official signing identity or an extracted credential. The app's User-Agent identifies this project.

Mailbox credentials and the inbox are persisted with authenticated AES-GCM encryption using a key held in Android Keystore. On reopening, the saved inbox appears immediately and downloaded recordings can play from app-private storage. A valid restored token can refresh the mailbox over Wi-Fi; initial authentication or renewal still needs Mint cellular data. Tokens and subscriber identifiers are never written to diagnostic output or sent to a project server.

The native inbox has system-following light/dark themes and saved appearance overrides, formatted phone numbers and local dates, and an expandable player with seeking and ten-second skips. One recording plays at a time. Leaving the app or losing audio focus pauses playback; returning does not automatically resume it. Refresh preserves the active player when its message remains in the inbox.

Search filters the saved inbox by full/partial phone number and displayed or ISO date, with All, Unread, and Marked filters. Each message's three-dot menu offers carrier-synced mark-as-read/unread and a reversible local deletion mark. Marked messages stay in the mailbox and can be restored from the Marked filter. See [search and message actions](../docs/search-actions.md) for validation and limits.

No recording deletion, greeting, forwarding, PIN, or push-registration requests are implemented. Recordings are cached in the app's private storage, using atomic writes. Background notifications and transcription are deferred. See [design and device validation](../docs/inbox-design.md) for the earlier playback milestone and its limits.
