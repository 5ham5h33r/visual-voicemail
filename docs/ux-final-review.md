# Final five-question review

Reviewed version 0.31 on 27 October 2026 after implementing the approved follow-up plan. This is a heuristic review supported by source inspection, native UI checks, public audio, and documented device results. It is not a first-time-user study. The emulator uses the measured S24 Ultra display configuration, 1440 x 3120 at 600 dpi; this matches layout dimensions, not Samsung hardware or carrier behavior.

## 1. Where are users most likely to drop off?

**Connection and the first model download remain the main friction.** Welcome now offers Get Started directly into the inbox. Show me around retains the interactive introduction and real-control spotlights. Permissions and model downloads are contextual, and playback does not wait for transcription setup.

Known Airplane-mode/no-SIM conditions offer a specific recovery path before connection. Unknown SIM states are allowed. Standard is recommended for English; the quality page explains when Large is needed for other languages or English translation. The user still confirms the download size. A new-user session must establish whether Mint data-route guidance is understandable; passing assertions cannot measure that.

## 2. What assumptions might not be true?

| Assumption challenged | Implemented response |
| --- | --- |
| Saved means an external backup | Saved consistently means app-private retained recordings. Export means a file in the selected folder. Privacy/storage copy explains uninstall consequences. |
| Everyone expects the same listening/read behavior | Optional Mark as read when playback starts is off by default. Successful playback requests a confirmed update; opening alone does not. Saved-only read changes work locally. |
| People will find transcript search in a menu | Search is now a direct icon next to transcript quality, with an explanatory disabled state when no text exists. Closing search stays on the recording. |
| Search understands all audio automatically | Search matches contact information and already-generated original/English text. Audio without generated text cannot match spoken words. |
| Cancel means losing a large transfer | Pause retains verified transfer identity and partial bytes. Resume validates the range and final hash; changed or corrupt content is not accepted. |
| Notifications are immediate | Scheduled checks remain subject to Android timing. Independent carrier push has not been verified and is not advertised as working. |

## 3. What are three edge cases the design might fail in?

1. **Carrier work is stalled while a local action is requested.** Playback, Share, Export, and transcription share cache-first audio resolution. Export also has its own work queue. Native tests hold the carrier monitor; the export test additionally blocks the inbox queue, and the speech test performs actual Tiny inference. Missing audio still needs recovery/network access.
2. **A transfer or destination fails partway through.** Real partial Tiny progress survived process termination and resumed to all four verified hashes. Deterministic checks cover range/fallback/corruption cases. A native document provider injects mid-write no-space and revoked-access failures; the source and earlier export survive. A provider error is not equivalent to filling all filesystem volumes or testing every third-party cloud provider.
3. **Selection, audio, or a guided tip is interrupted.** Selection retains native checked state and transfers focus. Small-window/large-text controls adapt. Audio-focus loss, recreation, and an incoming emulator call pause playback without automatic resumption. Guided tips expose their highlighted action to keyboard/screen-reader users. Recovery from every process-kill point in bulk transcription remains unproven.

## 4. Compared with familiar apps, what is missing?

The task comparisons remain Apple Phone for a paused message view with transcript and playback, Gmail for scoped selection and bulk actions, and Samsung Voice Recorder for recording/transcript context and a quieter scrolled header. The recording now exposes Search, quality, handset, and Share without adding another persistent helper panel. Updating saved text is a compact secondary action rather than a large filled button. Comparing two native headers favored the inline quality/search row at normal size; larger text uses the adaptive stacked layout.

The independent app still asks users to install a speech model and connect to a carrier service. It cannot assume an integrated platform service's provisioning, delivery privileges, or server transcription. Carrier deletion retains a local Saved copy rather than promising server-side Trash/Undo. This comparison evaluates task structure and expectations; it is not a new live competitor audit.

## 5. What breaks with low context, little technical experience, or bad connectivity?

Saved playback, generated text, search, local read changes, and cached sharing/export remain useful while the carrier is slow. Model setup has retained progress and actionable recovery. Export errors now explain whether to recover audio or revisit storage/folder access. Optional downloads and contacts/notifications access do not block first playback.

An installed-TalkBack walkthrough passed 419 assertions and 135 focus operations across six pages; each card avoids duplicate text stops and selection exposes a native checkbox. The final 320 x 480 dp sweep at 160%/200% text passed 471 assertions, including the compact Update state. Contrast, three themes, and reduced-motion behavior have separate coverage. These results do not establish audible wording/order comprehension, switch access, all languages/RTL, or accessibility on every OEM.

## Decision

The approved implementation pass is complete, with [phase evidence](ux-improvements.md) and repeatable [emulator checks](emulator-testing.md). The main follow-up findings—storage wording, local operations blocked by carrier work, misleading download continuation, first-use/search discovery, and accessibility/recovery gaps—have concrete fixes and scoped validation.

Release confidence still needs human first-use and spoken-assistive-technology sessions, broader devices/localization, and real carrier arrival/idle/reboot evidence. Greeting upload is implemented and simulated; the active real greeting has not been replaced. No additional personal voicemail was deleted for this pass. The promo video remains paused. None of those unperformed checks is counted as passed.
