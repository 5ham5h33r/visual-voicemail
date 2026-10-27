# Carrier removal and saved recordings

Version 0.24 replaces new local deletion marks with an explicit **Remove from carrier · Keep copy** action. The confirmation explains permanent carrier removal and app-local retention. Single-message and scoped bulk actions use the same process.

1. Obtain the recording and check that Android can read a positive audio duration.
2. Atomically save it under app-private `saved-recordings`, separate from the temporary playback cache. Verify its length and SHA-256 checksum.
3. Save encrypted message metadata, checksum, length, and pending removal status. Read the metadata back and verify the saved recording again. If any step fails, do not request carrier deletion for that message.
4. Request `DELETE voicemail/{encoded object identifier}`. An HTTP success alone is insufficient; a timeout may also have completed the deletion.
5. Fetch the carrier mailbox again. Only a message absent from this fresh listing, with persisted confirmation, counts as removed. Otherwise retain its local copy with an unconfirmed status and allow retry.

**Saved** contains retained recordings, including copies whose carrier removal is unconfirmed. All includes both carrier messages and saved copies; Unread and contact/date/transcript search also include saved copies. Saved-only read flags are local and can change offline. New-message notification comparisons continue to use the carrier's actual listing, before local copies are merged.

Clearing the playback cache preserves Saved. **Export audio copy** writes an ordinary audio file to the folder selected in Settings. Uninstalling removes app-private recordings and encrypted transcripts, so export important recordings first. **Remove saved copy from phone** is a separate confirmation; it is only available for messages with confirmed carrier removal and removes their local recording, metadata, and transcript.

Older deletion marks never trigger automatic carrier deletion. They remain visible in Saved with an explicit old-mark label and an explanation. Clear the old mark to keep its carrier message, or explicitly use the new removal flow. The app does not promise to restore removed recordings to the carrier.

## Validation scope

Host workflow tests use a simulated carrier to cover ordering, deduplication, archive failures preventing deletion, rejected requests, ambiguous timeouts, partial results, unavailable refresh, retry, and failed confirmation persistence. The isolated Android check uses a public audio fixture and a random test-only files directory to exercise encrypted metadata, atomic saved audio, cold-start recovery, read flags, cache protection, playable saved audio, carrier reappearance, corruption, and fail-closed unreadable metadata.

The device check never calls CarrierClient and never sends a carrier DELETE. The UI bulk test cancels the destructive confirmation. Existing personal voicemail is preserved. The carrier delete path is based on the observed original client's endpoint; actual deletion followed by absence in the live Mint mailbox still requires a user-chosen disposable voicemail. Do not describe simulated tests as proof of live carrier deletion.

Validation passed on the S24 Ultra: 19 isolated saved-mailbox checks, 42 bulk-flow checks, 34 inbox-navigation checks, 35 selection checks, 30 Settings checks, and 32 onboarding checks. The host suite passed 124 checks, including 16 new archive safety cases. The APK built and its signature verified. Visual labels use the shared native controls and all existing appearance themes.
