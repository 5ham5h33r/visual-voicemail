# App settings in version 0.8

Open the gear at the top-right of the inbox. Settings uses the same light/dark/AMOLED palette as the inbox, remembers its scroll position, and keeps preferences after reopening.

## Available controls

- **Appearance:** device default, Light, Dark, and AMOLED (true black). Changing the theme updates Settings and the inbox.
- **Inbox and contacts:** optional local contact-name lookup and newest/oldest message ordering. Unknown dates sort last in either order. The Contacts permission explanation and enable/disable controls now live here.
- **Playback:** 0.75×, 1×, 1.25×, 1.5×, and 2× speed; 5/10/15/30-second skips; follow-device, phone speaker, or phone earpiece output. Preferences apply when playback starts. The player displays its actual output route and stops if a requested route cannot be used. Leaving the app always pauses playback.
- **Mailbox and sync:** last successful update, refresh on open, refresh now, reconnect, and connection help. Refresh on open defaults on for an already-connected mailbox, checks stale data on returning, and is limited to one attempt per five minutes. It does not initiate first-time authentication or run in the background. Reconnect renews carrier authentication using Mint mobile data and preserves the saved inbox/downloads.
- **Storage:** Download location opens Android's folder picker and remembers access to the selected folder. A message's **Save audio copy** action writes a new file there; if no usable location is set, it asks for one first. Changing or forgetting the location does not move or delete existing copies. Playback continues to use a separate private cache, with its own count/size and confirmed **Clear playback cache** action. Clearing releases the player and runs after existing audio work on the same worker. It removes only recognized private recording files and atomic-write remnants. Saved audio copies, carrier messages, inbox/session records, and local deletion marks are kept. A connection may be needed to replay recordings afterward.
- **Privacy and app information:** Android app-permission settings, plain-language data handling, installed version, source-code link, and the full MIT license.

Notification delivery, transcription, and permanent carrier-message deletion are explicitly described as unavailable. There are no notification or transcription toggles pretending those features work.

## Validation

On the user's S24 Ultra, native playback reported 1.5× speed and actual output type 2 for the speaker and type 1 for the earpiece. A configured 15-second skip moved the paused position to 17,146 ms; skipping backward returned it to 2,146 ms. Both routes played the existing cached recording.

Theme switching was checked across Settings and inbox: AMOLED screenshot canvas pixels were RGB (0,0,0); Light pixels were (243,244,246). Contact names were disabled/re-enabled from Settings. Playback speed, skip interval, message order, and output preferences survived force-stop/reopen. Test preferences were restored afterward.

The cache-clear confirmation was canceled and the cached recording count remained one. Actual cache deletion was tested using disposable synthetic files on the host, preserving inbox, credentials, marks, unrelated files, and nested directories. Privacy and license dialogs were opened on the phone. Reconnect returned successful carrier authentication codes, mailbox HTTP 200, and the original single-message inbox.

Turning refresh-on-open off survived force-stop/reopen and kept the app on its saved inbox without a sync stage. Re-enabled the preference and used Refresh mailbox from Settings; the carrier returned HTTP 200. The host suite has 27 inbox/search/read/order checks, 10 cache-safety checks, and 8 audio export-format checks. Existing palette-contrast and APK signature checks continue in CI. Audio behavior uses Android's [MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) and [AudioRouting](https://developer.android.com/reference/android/media/AudioRouting) APIs.

Version 0.8 was tested by selecting an internal-storage subfolder through the system picker and saving one existing recording. A read-only device check confirmed persisted read/write access after force-stop, the correct AMR MIME type/extension, a byte-for-byte match to the 29,606-byte private recording, and an 18,500 ms duration using Android's native player preparation. The private recording remained intact. Canceling a subsequent folder selection retained the existing location. No private audio, paths, filenames, or folder URIs are included in the repository.

Folder access uses Android's [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), without broad storage permissions. Android restricts selection of some roots, including Download itself; selecting a child folder works. Saved copies are ordinary files governed by that folder's access and backup rules. Export detects AMR and AMR-WB headers and uses the corresponding MIME type and extension; unknown formats fail rather than being mislabeled. Filenames contain the message date and a random suffix, without caller names or numbers. Each save creates a new file; a failed write attempts to remove only that newly created partial file. Cloud providers, removable storage, revoked access, and full-storage failures still need device coverage.

This remains a development build. Bluetooth/wired-device transitions, unsupported output hardware, live incoming-call interruption, broad locale/accessibility testing, and large mailboxes need more device coverage. No user's carrier messages, greetings, forwarding settings, or PIN were changed by these settings tests.
