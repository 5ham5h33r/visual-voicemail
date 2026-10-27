# UX pressure test: version 0.4

Reviewed 2026-10-26 using the user's five questions. This is a heuristic assessment of the current code, the device checks already performed, and official reference flows. It is not a usability study: no abandonment rates or task-success rates have been measured. One real mailbox message on one phone is insufficient evidence for a general usability pass.

Version 0.6 follow-up: optional local contact-name display/search now addresses the contact-lookup gap below. Name access is explained and requested only when enabled; declining permission retains number/date search. The rest of this review records the v0.4 assessment and its still-open acceptance criteria. Transcription remains deferred.

Version 0.7 follow-up: Settings adds visible last-sync information, refresh-on-open, connection help/reconnect, actual audio-output selection, speed/skip preferences, and storage/privacy controls. Speaker and earpiece routes were verified on the S24 Ultra. The incomplete permanent-deletion lifecycle, notifications, broad accessibility/performance validation, and uncertain read-update recovery remain open.

Verdict: useful playback prototype; the complete everyday voicemail experience is not release-ready. System/light/dark themes, saved playback, explicit read-status verification, and reversible marks are strengths. The unresolved flows below are product gaps, not visual polish requests. Transcription remains outside the current milestone.

## 1. Where would users abandon onboarding?

**Partial.** The current path is open app → connect using Mint data → view inbox. There is no forced account signup or default-dialer change. The welcome copy mentions Mint data, but the app does not provide a clear preflight check for the correct data SIM or distinguish unsupported service, temporary network failure, and carrier activation failure in plain language. Your phone has multiple SIMs, making this a concrete setup concern. An empty mailbox and a failed/missing saved inbox also need clearer distinctions.

Acceptance: a first-time user can explain which SIM/data connection is needed before starting. Setup shows a named progress state, a specific recovery action on failure, and a distinct confirmed-empty result. Avoid adding a mandatory three-screen introduction merely because the review prompt mentioned three steps.

## 2. What assumptions might be wrong?

**Fail as a complete product experience.** Current assumptions include:

- People remember a caller's number or date. They may remember a contact name or the subject of the voicemail instead. Search is accurately labeled for numbers/dates, but contact lookup is missing; spoken-content search is deliberately deferred.
- A deletion mark is useful on its own. The app saves and restores marks correctly, but users seeking to clear a full mailbox cannot finish that task. The label implies a later deletion step that does not exist yet. Visible explanatory copy reduces confusion but does not complete the workflow.
- Playing a message while retaining its unread status matches expectations. The app currently requires an explicit menu action. This policy needs a deliberate decision and user feedback.
- Manual Refresh is enough for routine use. The app has no background delivery or notifications, and the inbox does not automatically refresh on opening. A saved inbox can therefore appear complete while new messages remain unseen.

Acceptance: define what listened/read means; make saved-versus-current status and last successful sync visible; decide whether marks are an organizational feature or a genuine deletion queue. If implementing deletion later, specify carrier behavior, confirmation, undo/recovery limits, and real mailbox-capacity effects before labeling the flow complete. Do not claim transcript search before transcription exists.

## 3. Which three edge cases challenge the design?

**Partial; broader tests are outstanding.**

1. **Carrier accepts a read change, then connectivity fails before verification.** The app correctly avoids claiming confirmed success, but the old UI state and generic retry message leave the outcome uncertain. Acceptance: show an explicit unconfirmed state, refresh to resolve it, and let the user distinguish a retry from a new conflicting action. Exercise failure before submission and after acceptance separately.
2. **An old recording is not downloaded and its signed audio URL has expired.** Saved audio works locally, but an uncached message still needs the network, and the app has no automatic fresh-URL recovery. Acceptance: distinguish saved audio from metadata-only messages, refresh an expired payload link with a bounded retry, and give a useful offline message rather than a generic playback failure.
3. **Hundreds of messages, large fonts, and typing with the keyboard open.** The keyboard layout was checked on the S24 Ultra, but the app rebuilds all matching cards and performs file/diagnostic work on each edit on the main thread. Jank and focus/scroll instability are plausible, not measured failures. Acceptance: test a synthetic large inbox, increased font/display sizes, TalkBack, and both themes; move to recycled rows/background search if measurements show missed frames or typing delays.

## 4. How does it compare with familiar apps?

**Partial.** The relevant comparison set is Apple Phone, Samsung Phone/Visual Voicemail, and Phone by Google. These are category references, not a claim that these are the market's top three apps.

- [Apple's voicemail guide](https://support.apple.com/en-gb/guide/iphone/iph003dae603/ios) describes message handling and deleted-message recovery, subject to carrier behavior. Our local mark/restore flow should not be mistaken for that complete delete/recover lifecycle.
- [Samsung's voicemail setup guidance](https://www.samsung.com/us/support/answer/ANS10007266/) provides a familiar Galaxy entry point and setup flow. Our independent client intentionally stays separate from the default dialer; it still needs clearer connection guidance and account/SIM context.
- [Google's voicemail guidance](https://support.google.com/phoneapp/answer/2811844?hl=en) documents list access, notification entry, SIM-specific behavior, and audio sharing. Our list/player is familiar, while notifications, SIM context, and export/share remain gaps.

Also missing from our own interface: a visible audio-output choice and a callback action. These are review findings from the implementation, not claims that every comparator offers identical controls. For private recordings, clear playback routing deserves priority over decorative changes. No competitor UI was live-tested during this audit.

Acceptance: a user can recognize the primary listening action, predict where audio will play, understand message state, and complete the supported management tasks without learning hidden gestures. Keep long-press as a shortcut rather than the only route; the existing visible message menu is useful.

## 5. What breaks with low context, accessibility needs, or bad Wi-Fi?

**Partial.** Verified strengths: saved inbox reopens, saved recordings play, read/unread round trips are verified, deletion marks survive reopening and can be removed, and both themes meet the checked text-contrast threshold. The large heading collapses while typing so the result card remains visible.

Remaining gaps: setup errors are often generic, an initial/renewed carrier session needs the correct cellular connection, read changes have no offline queue, cached inbox freshness is vague, and audio routing is not exposed. Contrast and 48 dp targets do not establish full accessibility: TalkBack, larger text, switch access, and live-call interruptions still need testing. Cached-path tests do not prove all airplane-mode or degraded-network cases.

Acceptance: run explicit offline/slow-network scenarios, offer a specific recovery action for each supported failure, keep saved playback available, expose fresh/saved/unconfirmed state, and complete an assistive-technology review. Simulate network failures without deleting the user's recordings or changing their forwarding settings.

## Priorities resulting from this review

1. Explain setup, data-SIM requirements, freshness, and uncertain read-update outcomes.
2. Resolve the deletion-mark lifecycle and playback/read semantics; expose audio-output choice.
3. Validate large-inbox performance, larger fonts, assistive technology, and degraded networks.
4. Add expected daily-use capabilities such as notification delivery and contact-name lookup, with explicit permission and privacy behavior.

These findings are open; their acceptance criteria have not passed. Transcription remains deferred.
