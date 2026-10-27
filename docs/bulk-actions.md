# Inbox selection and bulk actions in version 0.17

Long-press a voicemail, or tap Select above the inbox. Tap rows or checkboxes to add or remove messages. Selection mode hides playback/menu buttons so a selection tap cannot accidentally play audio. Select all selects the current search/filter results; Deselect all clears them. Changing search/filter removes hidden messages from selection. Refresh removes missing messages. The selection count, native checkbox states, and checkable row accessibility state stay aligned. Back or the close control leaves selection without closing the app. Selection is saved across screen recreation.

The contextual actions are Transcribe, Mark read, and Delete. More also provides Mark unread and Restore deletion marks. Read/unread applies immediately; deletion and speech setup explain their effects before starting.

## Transcription queue

The confirmation shows the selected quality and the number of existing transcripts. Generate missing preserves existing text. Update all explicitly regenerates selected transcripts with the current model, keeping each previous result until its replacement is saved successfully. If the model is missing, the same quality/download page opens without losing selection. Model setup does not silently start a queued batch.

The process-scoped speech worker serializes the snapshot with normal single-message recognition/model downloads. Each result is encrypted and saved separately. A damaged recording does not prevent later messages from being tried; results report saved, kept, failed, and unfinished counts. Low memory stops further attempts and explains the smaller-model alternatives. Keep the app open, especially with Large v3. The queue survives screen recreation and navigation while the process stays alive; it is not a persistent background service and must be restarted after process termination.

Cancel stops before the next item or phrase; an in-progress native phrase decode is not interruptible. Already saved transcripts stay saved. Opening a recording during a queue does not schedule overlapping recognition. The inbox shows progress, cancellation feedback, and a dismissible result, then reloads saved transcripts for search.

## Read/unread and deletion marks

A separate process-scoped worker sends the existing carrier flag request per selected ID, then refreshes the mailbox once. Only states confirmed in that fresh mailbox count as successful. A timeout response can still have applied at the carrier, so the final snapshot is authoritative. Partial results and failed refreshes are reported without pretending every request succeeded. No voicemail recording DELETE is sent. A second bulk read operation is blocked until the first completes.

Delete saves encrypted, reversible deletion marks on this phone, with one atomic write for the selection. Recordings, transcripts, and the carrier mailbox remain. Restore removes those marks. Permanent voicemail deletion is not implemented.

## Validation

Host cases cover deduplication, request rejection, lost responses, partial success, disappearing messages, invalid IDs, and failed confirmation refreshes. Device instrumentation uses synthetic inbox entries and a supplied public audio fixture. It exercises selection/search, select all/deselect all, marking/restoring synthetic entries, Back, recreation, mixed speech success/failure, keeping existing transcripts, retaining an old transcript after a failed update, overlapping-job rejection, and cancellation. The test never changes personal carrier flags or rewrites personal transcripts. See [device checks](../app/device-tests/README.md).

On the S24 Ultra, all 42 bulk device checks and 12 host read-confirmation checks passed. The caller data and transcript text in selection screenshots were hidden before capture, and Light, Dark, and AMOLED were reviewed. The original app preferences and deletion marks were restored after tests.

The existing 14 recording, 28 quality/motion, 33 navigation, and 32 onboarding device regressions also passed. Native theme contrast checks and all 86 host behavior checks passed. Bulk carrier writes themselves were not exercised against personal messages; the existing single-message request format is reused and bulk result handling is tested with a fake carrier.

Version 0.21 keeps the existing row/checkbox views during selection changes, restoring native checkmark animations and adding short card/toolbar transitions. State changes remain immediate, including with reduced motion. See [motion](motion.md).
