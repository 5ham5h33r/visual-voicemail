# App settings in version 0.7

Open the gear at the top-right of the inbox. Settings uses the same light/dark/AMOLED palette as the inbox, remembers its scroll position, and keeps preferences after reopening.

## Available controls

- **Appearance:** device default, Light, Dark, and AMOLED (true black). Changing the theme updates Settings and the inbox.
- **Inbox and contacts:** optional local contact-name lookup and newest/oldest message ordering. Unknown dates sort last in either order. The Contacts permission explanation and enable/disable controls now live here.
- **Playback:** 0.75×, 1×, 1.25×, 1.5×, and 2× speed; 5/10/15/30-second skips; follow-device, phone speaker, or phone earpiece output. Preferences apply when playback starts. The player displays its actual output route and stops if a requested route cannot be used. Leaving the app always pauses playback.
- **Mailbox and sync:** last successful update, refresh on open, refresh now, reconnect, and connection help. Refresh on open defaults on for an already-connected mailbox, checks stale data on returning, and is limited to one attempt per five minutes. It does not initiate first-time authentication or run in the background. Reconnect renews carrier authentication using Mint mobile data and preserves the saved inbox/downloads.
- **Storage:** downloaded-file count/size and a confirmed clear-downloads action. Clearing releases the player and runs after existing audio work on the same worker. It removes only recognized recording files and atomic-write remnants in the audio folder. It does not delete carrier messages, inbox/session records, or local deletion marks. A connection may be needed to replay recordings afterward.
- **Privacy and app information:** Android app-permission settings, plain-language data handling, installed version, source-code link, and the full MIT license.

Notification delivery, transcription, and permanent carrier-message deletion are explicitly described as unavailable. There are no notification or transcription toggles pretending those features work.

## Validation

On the user's S24 Ultra, native playback reported 1.5× speed and actual output type 2 for the speaker and type 1 for the earpiece. A configured 15-second skip moved the paused position to 17,146 ms; skipping backward returned it to 2,146 ms. Both routes played the existing cached recording.

Theme switching was checked across Settings and inbox: AMOLED screenshot canvas pixels were RGB (0,0,0); Light pixels were (243,244,246). Contact names were disabled/re-enabled from Settings. Playback speed, skip interval, message order, and output preferences survived force-stop/reopen. Test preferences were restored afterward.

The clear-downloads confirmation was canceled and the saved recording count remained one. Actual cache deletion was tested using disposable synthetic files on the host, preserving inbox, credentials, marks, unrelated files, and nested directories. Privacy and license dialogs were opened on the phone. Reconnect returned successful carrier authentication codes, mailbox HTTP 200, and the original single-message inbox.

Turning refresh-on-open off survived force-stop/reopen and kept the app on its saved inbox without a sync stage. Re-enabled the preference and used Refresh mailbox from Settings; the carrier returned HTTP 200. The host suite now has 27 inbox/search/read/order checks and 10 cache-safety checks. Existing palette-contrast and APK signature checks continue in CI. Audio behavior uses Android's [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) and [AudioRouting](https://developer.android.com/reference/android/media/AudioRouting) APIs.

This remains a development build. Bluetooth/wired-device transitions, unsupported output hardware, live incoming-call interruption, broad locale/accessibility testing, and large mailboxes need more device coverage. No user's carrier messages, greetings, forwarding settings, or PIN were changed by these settings tests.
