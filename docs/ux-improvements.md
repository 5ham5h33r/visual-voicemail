# Approved UX improvement implementation

The user approved the full follow-up plan on 27 October 2026. Development continues on an emulator at 1440 x 3120 / 600 dpi, with smaller-window and large-text validation retained. Personal voicemail and the phone are not used for these implementation tests.

## 1. Storage, read behavior, and local actions

Version 0.28 distinguishes Saved recordings inside the app from files exported to the chosen folder. Privacy and cache-clear explanations use the same terms. Uninstalling removes app-local recordings and transcripts; exported files follow the selected folder's retention rules.

Playback Settings adds **Mark as read when playback starts**, off by default. Only a successful playback start requests the change, once per recording activity session. Opening a recording alone does not mark it read. Carrier confirmation is required before the cached inbox changes; a failed or unavailable update reports that it could not be confirmed. The user can retry through existing inbox read actions. Saved-only recordings update locally without a carrier connection. Playback never waits for a read update.

Playback, Share, Export, and transcription use the same cache-first audio resolution. Missing audio falls back to the existing verified download/error path. Saved-only read updates bypass the carrier monitor. Export has its own file-work queue, so a network operation already queued in the inbox cannot delay local export preparation. Cache clearing waits for active audio/export/mailbox work and continues to protect Saved recordings.

## 2. Model download recovery

Version 0.29 retains partial files with their pinned source URL, expected size, hash, and server validator. Resume checks continuation offsets and the complete model hash before promotion. If a host ignores a range request, the current file restarts safely. Changed, corrupt, or unidentified partial content is never accepted as a ready model. Completed verified files are reused. Pausing interrupts the active connection off the UI thread, and saved progress survives process restart without automatically restarting network work.

The quality page and recording use Pause download, Resume/Retry/Verify download, and Remove download actions. Removal affects only the selected model's complete/partial files and transfer metadata. Existing transcripts and recordings remain. Paused progress remains visible. Storage checks account for retained data rather than always requiring twice the model size.

Sixty-two deterministic host checks cover continuation/fallback, invalid ranges, changed identity, corruption, truncated transfer, unavailable host, cancellation, orphaned progress, strong validators, and complete partial-file verification. A separate emulator test downloaded the public English Tiny model: nine checks confirmed retained partial progress and the Resume/Remove UI; after the app process ended, fourteen checks confirmed the same prefix survived, download resumed, and all four pinned model hashes passed. This is not evidence of Large inference performance on the 2 GB emulator.

## Remaining approved phases

- A shorter first-use route to playback, connection prerequisites, simpler speech-model recommendations, and improved transcript-search discovery.
- TalkBack/focus/state, display/theme/motion, interruption, revoked-folder, process-restart, and low-storage checks with public fixtures.
- Final five-question review with completed evidence and explicit phone/carrier or human-study limits. Real arrivals/idle/reboot and user-chosen greeting replacement remain dependent on physical-phone availability and the chosen test action.

Each implementation phase uses a descriptive branch, coherent commits, CI, a pull request, merge, and completed-branch cleanup.
