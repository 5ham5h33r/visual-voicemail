# Voice Mail Android app

Original Android application for Mint's existing carrier voicemail mailbox. This milestone authenticates over Mint cellular data, displays the actual inbox, downloads recordings privately, and plays them through Android's native player. Samsung Phone remains the default dialer. The app has no carrier privileges and requests only network permissions.

Build with Python 3.9+ and Java 17+:

```sh
python probe/bootstrap.py
python probe/build.py --module app
```

The verified debug APK is `app/build/visual-voice-mail.apk`. The app uses its own package (`org.visualvoicemail.app`) and generated debug signing key, separate from the diagnostic probe. CI builds and verifies both modules.

Authentication establishes an independent service instance and retrieves a service-instance token. The carrier mailbox interface requires its public `VVMOTT` profile/version headers, a subscriber `tel:` URI, and an encoded bearer token. These are protocol fields, not an official signing identity or an extracted credential. The app's User-Agent identifies this project.

Credentials currently remain only in app memory; reopening the app may require Mint mobile data for authentication again. Tokens and subscriber identifiers are never written to diagnostic output. Metadata is displayed locally and is not sent to a project server.

No delete, mark-read, greeting, forwarding, PIN, or push-registration requests are implemented. Recordings are cached in the app's private storage. Pause/resume and completion are supported; persisted inbox/session storage and the playback interface are being improved next. Transcription is deferred at the user's request.
