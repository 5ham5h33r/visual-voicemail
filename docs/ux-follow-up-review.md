# Five-question follow-up review

Reviewed version 0.27 on 27 October 2026 after the approved implementation pass. The emulator remains at the measured S24 Ultra viewport: 1440 x 3120, 600 dpi. Earlier 411 dp and 320 x 480 dp large-text checks remain relevant evidence. This is a heuristic review, not a new-user study or a claim that every failure path has been reproduced.

The core recording layout is substantially clearer. Play has balanced space, every speed retains its center, and smaller visible icons retain comfortable targets. This pass found three concrete follow-ups in source inspection: inconsistent storage wording, local actions that can wait for carrier synchronization, and misleading download-retry wording.

## 1. Where are users most likely to drop off?

**Connection and the first transcript.** Four introduction pages, an optional control tour, mailbox connection, and model setup can still feel like several introductions before the first useful result. Skipping and contextual permissions help, but functional checks cannot establish whether that sequence is understandable.

Keep first playback as the immediate goal. Validate the disconnected state with a new user, including someone on Wi-Fi without the required Mint mobile-data route. For transcription, foreground the recommended choice for the desired language and keep model details available without demanding that everyone understand three models. Preserve explicit consent for the 1.78 GB Large download.

## 2. What assumptions might not be true?

- **Saved means exported or backed up.** Storage guidance correctly distinguishes app-private Saved recordings from exports. The Privacy dialog instead says saved audio copies go to the chosen folder. Those statements conflict. Use Saved consistently for retained app-private recordings and Export for ordinary files in the chosen folder. Explain that uninstalling removes app-local copies; a retained carrier-deleted voicemail is not automatically an external backup.
- **Listening means read.** The implementation keeps read/unread explicit. That is a valid choice, but it needs user validation rather than an automatic carrier-flag change based on this review.
- **Search covers audio without text.** Inbox text search can only match transcripts already generated. The existing guide explains this; test comprehension with someone who did not read the guide.
- **A smaller menu is always easier.** Find is the first item in Transcript options, which keeps the player quiet but makes discovery depend on opening a menu. On the emulator, opening that item exposed the field and previous/next controls, and the empty field had no clear X. Whether a new user finds it remains unmeasured.

## 3. What are three edge cases this design might fail in?

1. **A carrier refresh is slow while someone uses a saved recording.** Playback now resolves cached audio first. Source inspection shows Share, Export, and transcription still call the synchronized carrier audio method directly. They can wait for its monitor even when the recording is local. Extend the cache-first resolution to those consumers, preserving missing-file recovery and Saved-copy verification. This finding is based on code, not a new measured sharing-delay test.
2. **A large model download is cancelled near completion.** Completed verified files are reused, but the current partial file is deleted. The cancellation message says the user can resume, which can imply byte-level continuation. Correct the wording to explain retry behavior; separately consider verified partial-download recovery. Do not advertise resumability before it is implemented and tested.
3. **A call, process termination, revoked folder grant, or full storage interrupts work.** Existing simulated and device checks cover important parts of recovery, but not this whole matrix. Test retained drafts/transcripts, partial batches, audio focus, and accurate failure messages before release. Long-idle/reboot notification timing and actual new-message arrivals still require carrier/device validation.

## 4. Compared with three familiar apps, what is missing?

| Reference | Current assessment and next improvement |
| --- | --- |
| [Apple Phone](https://support.apple.com/guide/iphone/check-voicemail-iph003dae603/ios) | Its documented message view combines playback, transcript, and sharing. Our core message view now follows that understandable task structure. Improve setup and recovery before adding more player controls. |
| [Gmail](https://support.google.com/mail/answer/7401?co=GENIE.Platform%3DAndroid&hl=en) | Selection and bulk actions are familiar. Gmail normally moves deleted mail into recoverable Trash; our confirmed carrier removal retains a local Saved copy instead. Keep that difference explicit and do not imply carrier Undo is available. |
| [Samsung Voice Recorder](https://www.samsung.com/us/support/answer/ANS10000942/) | Its documented workflow connects transcription, translation, playback, and sharing. Those tasks are present here, but choosing and downloading an independent speech model adds setup effort. Keep language/output choices understandable and saved results immediately usable. |

These comparisons assess task structure, not visual identity or equivalent carrier/platform capabilities. They do not justify copying every feature from the reference apps.

## 5. What breaks with low context, little technical experience, or bad Wi-Fi?

The most useful improvements are clear storage language, predictable retry behavior, and local operations that remain responsive while the network is slow. Direct push remains unverified; scheduled notifications must continue to state that Android can delay checks. A successful test alert is not evidence of new carrier voicemail delivery.

Large-text bounds, target sizes, contrast, and exposed accessibility labels are useful checks. They do not replace a full TalkBack session or a first-time-user session. Still validate focus order, spoken state, model/setup comprehension, and interrupted work. A matching emulator screen does not reproduce One UI, real radio behavior, or S24 performance.

## Recommended next order

| Priority | Action | Completion evidence |
| --- | --- | --- |
| First | Unify Saved/export/privacy wording | All storage and deletion explanations describe the same destinations and retention behavior. |
| First | Keep cached Share, Export, and transcription independent of carrier synchronization | A held carrier monitor does not block preparation from public local audio; missing recordings still recover correctly. |
| First | Correct download retry/resume wording | Cancellation accurately explains what is retained and what must download again. |
| Next | Validate the shortest route to playback and transcription | A new user completes connection, playback, transcript search, and a safe sample deletion without coaching. |
| Before release | Finish accessibility and interruption coverage | TalkBack, incoming-call/audio-focus, process termination, folder access, and low-storage results are recorded. |
| Carrier-dependent | Validate notification delivery and an explicitly chosen greeting replacement | Real arrival/idle/reboot evidence and user-chosen greeting read-back; no emulator substitute or unsupported push claim. |

This review records recommendations. It does not change read status, delete voicemail, replace greetings, download models, or implement the newly identified follow-ups. The earlier implementation plan and its completed validations remain in the [baseline review](ux-review.md) and [game plan](ux-game-plan.md).
