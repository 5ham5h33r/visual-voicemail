# Approved UX improvement implementation

The user approved the full follow-up plan on 27 October 2026. Development uses an emulator at 1440 x 3120 / 600 dpi, with smaller-window and large-text validation retained. Destructive/failure fixtures use public sample data. Subsequent non-destructive S24 Ultra checks are recorded separately below.

## 1. Storage, read behavior, and local actions

Version 0.28 distinguishes Saved recordings inside the app from files exported to the chosen folder. Privacy and cache-clear explanations use the same terms. Uninstalling removes app-local recordings and transcripts; exported files follow the selected folder's retention rules.

Playback Settings adds **Mark as read when playback starts**, off by default. Only a successful playback start requests the change, once per recording activity session. Opening a recording alone does not mark it read. Carrier confirmation is required before the cached inbox changes; a failed or unavailable update reports that it could not be confirmed. The user can retry through existing inbox read actions. Saved-only recordings update locally without a carrier connection. Playback never waits for a read update.

Playback, Share, Export, and transcription use the same cache-first audio resolution. Missing audio falls back to the existing verified download/error path. Saved-only read updates bypass the carrier monitor. Export has its own file-work queue, so a network operation already queued in the inbox cannot delay local export preparation. Cache clearing waits for active audio/export/mailbox work and continues to protect Saved recordings.

## 2. Model download recovery

Version 0.29 retains partial files with their pinned source URL, expected size, hash, and server validator. Resume checks continuation offsets and the complete model hash before promotion. If a host ignores a range request, the current file restarts safely. Changed, corrupt, or unidentified partial content is never accepted as a ready model. Completed verified files are reused. Pausing interrupts the active connection off the UI thread, and saved progress survives process restart without automatically restarting network work.

The quality page and recording use Pause download, Resume/Retry/Verify download, and Remove download actions. Removal affects only the selected model's complete/partial files and transfer metadata. Existing transcripts and recordings remain. Paused progress remains visible. Storage checks account for retained data rather than always requiring twice the model size.

Sixty-two deterministic host checks cover continuation/fallback, invalid ranges, changed identity, corruption, truncated transfer, unavailable host, cancellation, orphaned progress, strong validators, and complete partial-file verification. A separate emulator test downloaded the public English Tiny model: nine checks confirmed retained partial progress and the Resume/Remove UI; after the app process ended, fourteen checks confirmed the same prefix survived, download resumed, and all four pinned model hashes passed. This is not evidence of Large inference performance on the 2 GB emulator.

## 3. First use and transcript discovery

Version 0.30 offers Get Started directly from the first Welcome screen, with Show me around retaining the four-screen interactive introduction and anchored control tour. The quick route opens the inbox without requiring Contacts, alerts, model setup, or a tour. Those choices remain available contextually and in Settings.

Connection preflight explains known Airplane-mode or all-SIM-slots-absent cases before a network attempt. It offers network settings, cancellation, and an explicit Try anyway action. Unknown SIM/capability states and dormant cellular networks are not rejected. No new phone-state permission is requested or device setting changed automatically.

The transcript has a direct Search icon, keeps quality selection on the page, and retains other actions in Transcript options. Search does not start playback. Closing search clears highlights and stays on the recording; an inbox search result does not automatically open the keyboard. Search is disabled with an explanatory accessibility label until text exists.

Two native S24-sized header alternatives were compared with sample data. Keeping quality inline retained a single 48 dp control row and fit Standard, Enhanced, and Highest labels without wrapping the title. Moving quality underneath added another row. The inline form was selected at normal text size; larger fonts/narrow windows keep the adaptive stacked form. This is a measured layout comparison and design judgment, not a human preference study.

The quality page recommends Standard for English and explains when the language/output needs Large. Existing quality options, technical model names, download costs, and explicit download confirmation remain available. Forty-one first-use/discovery assertions, 33 guided-onboarding assertions, and 47 recording assertions passed on the S24-sized emulator. Earlier fixed sleeps in tour checks now use bounded overlay readiness.

## 4. Accessibility and recovery

Version 0.31 presents each inbox card as one named action with its date, read/Saved state, and a bounded transcript preview. Selection moves accessibility and keyboard focus to the native rounded checkbox, which exposes its actual checked state. Settings rows identify themselves as buttons; section headings expose heading semantics. Guided tips have an accessible action equivalent to tapping the highlighted control, a named pane, and keyboard Back dismissal.

At large text sizes or below 360 dp width, selection uses a short count and labelled 48 dp action icons instead of squeezed text. The full action and selection scope remain available to assistive technology. Normal-size S24 controls retain their existing layout. The disabled transcript-search icon uses one dimming treatment rather than being dimmed twice.

Physical visual review also caught a language-only setting change being described as a quality change. That message now names the selected language. At the user's request, updating existing text uses a compact secondary text action instead of a full-width filled button, retaining its 48 dp target. First-time transcription/model setup keeps the primary action. Both styles share immediate press feedback and reduced-motion behavior.

Export failures distinguish unavailable source audio from a destination problem. The destination uses a file descriptor that can report a document provider's reliable-stream error. A failed export removes only the document created for that attempt; the source recording and earlier exports remain intact. No test fills the actual device storage.

Validation on the S24-sized API 36 emulator:

| Check | Result and scope |
| --- | --- |
| Installed TalkBack enabled | 419 assertions, 135 successful focus operations across six pages, selection, and guided tips. Exact accessibility settings restored afterward. Spoken audio/comprehension was not assessed. |
| Small/short window and large text | Final sweep: 471 assertions, 125 visible-control observations at 320 x 480 dp and 160%/200% text, including first-use Welcome, bulk selection, and the compact transcript-update state. The earlier matched-quality state passed 480 assertions/128 observations. Counts depend on visible content. These are per-activity overrides. |
| Export and interruption recovery | 42 assertions: cached export while both carrier lock and inbox network queue are blocked; exact exported bytes; mid-write no-space provider failure; revoked folder access; failed atomic encrypted-state write; audio-focus loss; recreation; emulator incoming-call interruption. Source bytes/preferences restored. |
| Real local speech inference | 11 assertions: public audio passes through downloaded Tiny/VAD and encrypted transcript storage while the carrier monitor is held. No recognized text is logged. |
| Affected regressions | 35 selection, 33 guided-onboarding, 41 first-use/discovery, and 38 quality/motion assertions passed on version 0.31. The latter includes language-only change copy, compact Update versus primary setup sizing, retained text, and reduced/immediate press feedback. |
| Host checks | 223 behavior checks and text contrast in 24 theme/color combinations passed. |

The recovery provider and grant receiver exist only in the temporary test APK. The production APK does not register either component. Incoming calls are simulated through the emulator console; no real call is placed. Download/process-restart evidence remains the real partial Tiny transfer documented above. This does not establish durable recovery from every possible OS kill point during a bulk transcription.

The physical S24 Ultra was reconnected and updated in place from 0.27 to 0.31. Preferences were byte-identical immediately after installation. All 47 recording, 33 navigation, and 38 quality/motion assertions passed on that phone, including cached preparation, phrase/search behavior, player spacing, intercepted Call/Share launch, parent/Back navigation, the compact Update action, and language-change copy. Existing read status was unchanged; no new deletion or greeting replacement was performed. These results supplement the emulator evidence rather than establishing notification arrival timing.

## Final review and remaining release evidence

The [final five-question review](ux-final-review.md) records the completed changes and separates reproduced behavior from human/carrier validation. A first-time-user comprehension session, listening to the full spoken TalkBack experience, localization/RTL, real arrival/long-idle/reboot notification timing, and an explicitly chosen live greeting replacement remain unverified. Direct standalone carrier push remains unverified under the independent-app requirement. These are recorded limits, not emulator passes.

Each implementation phase uses a descriptive branch, coherent commits, CI, a pull request, merge, and completed-branch cleanup.
