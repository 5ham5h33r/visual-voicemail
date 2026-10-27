# Personal voicemail greeting setup

Version 0.26 adds Settings → Voicemail greeting. The page reads the current personal greeting, offers local Record or Choose audio, and requires preview before enabling Save greeting. Microphone access is requested only after Record is chosen. File import and existing-mailbox playback do not require microphone permission. Carrier refresh is separate from local draft work, so a slow connection does not disable Record or Choose audio. Recording stops when leaving the page; a valid draft is kept locally.

Drafts are limited to 1–60 seconds. Supported platform audio formats can be imported through Android's document picker and converted on the phone to AMR narrowband. Import is limited to 25 MB. Conversion and validation must complete before replacing a previous local draft. Draft metadata is encrypted with Android Keystore; audio uses app-private storage. Playback-cache clearing preserves the separate draft directory. Discard draft affects the local draft, not the carrier greeting.

## Explicit carrier save

The confirmation is the only entry point for carrier changes. A verified snapshot protects the previewed draft from subsequent local edits. The client uploads a personal greeting with its active flag unset, remembers its identifier locally, activates it, and checks a fresh carrier listing. Only one active personal greeting with the expected identifier counts as confirmed. If the carrier leaves old personal entries active, their flags are cleared only after the new greeting is confirmed active. No greeting recordings are deleted, and the name greeting is not changed.

An upload timeout is reconciled by its persisted draft/request identity before another upload is attempted. Activation and flag-update timeouts are also checked against a new listing. Unconfirmed outcomes remain unconfirmed; a local draft and remembered upload identity are kept for retry. A process restart never automatically resumes a destructive operation. Full carrier greeting storage does not cause old greetings to be erased to make room.

Call voicemail for greeting help opens the default phone app. If it cannot handle the voicemail URI, the fallback explains pressing and holding 1 in Phone. This is a separate carrier phone-menu route, not a claim that a direct save succeeded. No call-forwarding, PIN, or default-dialer setting is changed.

## Evidence and limits

Read-only Mint requests on the S24 Ultra returned personal and name greeting entries. Endpoint names, multipart fields, and activation flags were observed in static analysis of the original client; vendor implementation/configuration is excluded from the repository. Carrier read access does not prove a replacement upload has succeeded.

Thirty-two simulated-carrier checks cover staging, identity persistence before activation, wrong identities, rejected/ambiguous requests, verification, retries without duplicate upload, independent activation flags, and preservation of old/name recordings. Thirty-four Android checks use public WAV audio and isolated storage: import, real AMR encoding/decoding and playback, metadata, hashes, snapshot checks, invalid imports, multipart boundaries/fields, preview gating, cancelled Save, picker cancellation, optional permission handling, and Up navigation. Settings and navigation regressions passed. No ambient microphone audio was captured by automated tests, and the user's live carrier greeting was not replaced. A user-chosen recording and explicit Save are required to verify actual carrier upload/activation.

The native capture lifecycle follows Android's [MediaRecorder guidance](https://developer.android.com/media/platform/mediarecorder); conversion uses the platform [MediaCodec API](https://developer.android.com/reference/android/media/MediaCodec).
