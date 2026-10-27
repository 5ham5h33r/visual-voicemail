# Transcription requirements

The user authorized transcription after reviewing playback. Version 0.13 implements private English recognition, transcript search, and phrase-based tap-to-play. See [implementation and device checks](transcription.md). These requirements remain the acceptance criteria; word-level alignment and broader device/language coverage remain open.

## Tap a transcript line to play from there

Tapping a transcript line must seek to the start of its corresponding audio segment and start playback. This applies whether the recording was paused, playing, or not yet opened. Use the existing single player and its playback speed, output, audio-focus, and background-pause behavior.

- Preserve timestamped transcript segments, with text and start/end offsets in milliseconds relative to the original recording. Choose a transcription engine or alignment step that supplies usable timestamps; plain text alone cannot fulfill this requirement. Do not invent evenly spaced timestamps from text length.
- A displayed line or sentence activates its owning segment's start time. Text wrapping, font size, and orientation must not change that mapping. A segment may wrap onto several visual lines, which share the segment's anchor unless finer timing is available.
- Highlight the segment corresponding to the current playback position. Seeking with player controls updates the highlight as well. Expose the segment text, timestamp, and play action to accessibility services.
- If audio needs downloading or preparing, show loading feedback and begin at the chosen offset once ready. Rapid taps use the most recent selection. Switching voicemail must invalidate pending work for the previous recording.
- Validate timestamps against the recording duration. Missing, invalid, or unavailable timing must not cause playback at a fabricated location. Explain when a segment cannot be played. Download/preparation failures preserve the transcript and offer retry.

## Search transcript content

Provide a **Find in transcript** action inside an individual voicemail. Search words and phrases without case sensitivity, highlight all matches, show the current match and total count, and offer next/previous-match controls. Searching or navigating matches must not start playback by itself. Selecting a matching segment's play action uses the timestamp behavior above.

Inbox search must also be able to match saved transcript text alongside the existing contact-name, number, and date fields. Show a matching text excerpt so the user can tell why a voicemail was found. Selecting the result opens that message at the matching transcript segment; tapping the segment starts playback there.

- Search locally over available transcripts, including offline copies. Queries, transcript text, and matched excerpts must stay out of network requests and diagnostics. Do not start transcription or download audio merely because the user searches.
- Keep search-match highlighting distinguishable from the currently playing segment in Light, Dark, and AMOLED themes, without relying only on color. Provide accessible match counts and navigation labels.
- Preserve the query and selected match across rotation and appearance changes. Clearing search restores the full transcript without changing playback position or carrier read flags.
- Distinguish a transcript that is unavailable, still being generated, or failed from a completed transcript with no matches. Inbox search should explain when some voicemail has no searchable transcript yet.
- A result with missing or invalid timing can still reveal its matching text, but must not offer playback from an invented offset. Keep the normal recording player available.

## Acceptance checks

Check the first, middle, and final segments against their actual spoken audio, including recordings with leading silence. Test taps from paused and playing states, initial preparation, repeated rapid taps, and switching messages while loading. Confirm that no two recordings play together.

Repeat the interaction with large fonts and wrapped text, after orientation changes, using accessibility navigation, with cached audio offline, and with unavailable audio or invalid timing. Verify that speed/output preferences and leaving the app retain the established playback behavior. Timestamp precision must be measured against real recordings before claiming line-level accuracy.

Test transcript search with repeated matches, mixed case, phrases spanning segment boundaries, no results, empty queries, and non-English text. Verify next/previous navigation and selection of the correct timestamped segment. Check search within one transcript and across the inbox, including existing contact/number/date matching, available offline transcripts, unavailable/failed transcripts, larger mailboxes, rotation, and each theme. Search and match navigation must not start audio on their own.
