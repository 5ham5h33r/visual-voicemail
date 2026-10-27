# Transcription requirements

Transcription implementation remains deferred until the core voicemail milestone is reviewed and the user gives the go-ahead. These requirements guide that later work; they describe planned behavior, not functionality already present in the app.

## Tap a transcript line to play from there

Tapping a transcript line must seek to the start of its corresponding audio segment and start playback. This applies whether the recording was paused, playing, or not yet opened. Use the existing single player and its playback speed, output, audio-focus, and background-pause behavior.

- Preserve timestamped transcript segments, with text and start/end offsets in milliseconds relative to the original recording. Choose a transcription engine or alignment step that supplies usable timestamps; plain text alone cannot fulfill this requirement. Do not invent evenly spaced timestamps from text length.
- A displayed line or sentence activates its owning segment's start time. Text wrapping, font size, and orientation must not change that mapping. A segment may wrap onto several visual lines, which share the segment's anchor unless finer timing is available.
- Highlight the segment corresponding to the current playback position. Seeking with player controls updates the highlight as well. Expose the segment text, timestamp, and play action to accessibility services.
- If audio needs downloading or preparing, show loading feedback and begin at the chosen offset once ready. Rapid taps use the most recent selection. Switching voicemail must invalidate pending work for the previous recording.
- Validate timestamps against the recording duration. Missing, invalid, or unavailable timing must not cause playback at a fabricated location. Explain when a segment cannot be played. Download/preparation failures preserve the transcript and offer retry.

## Acceptance checks for the later implementation

Check the first, middle, and final segments against their actual spoken audio, including recordings with leading silence. Test taps from paused and playing states, initial preparation, repeated rapid taps, and switching messages while loading. Confirm that no two recordings play together.

Repeat the interaction with large fonts and wrapped text, after orientation changes, using accessibility navigation, with cached audio offline, and with unavailable audio or invalid timing. Verify that speed/output preferences and leaving the app retain the established playback behavior. Timestamp precision must be measured against real recordings before claiming line-level accuracy.
