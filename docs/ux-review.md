# Five-question UX review

For the completed version 0.31 pass, see the [final review](ux-final-review.md) and [implementation evidence](ux-improvements.md). This baseline describes version 0.27.

The [follow-up review](ux-follow-up-review.md) repeats these questions after implementation and records the newly identified priorities.

This review covers version 0.27: introduction and permissions, connection, inbox/search/selection, playback, transcripts and model setup, notifications, Settings, exports, and greetings. It is a heuristic assessment supported by native device checks and reviewed screenshots. It is not a measured first-user study.

## 1. Where are users most likely to drop off?

**Partial pass: connection and model setup remain the largest obstacles.** The short introduction is skippable; the follow-up tour highlights actual controls. Settings can replay it. Contacts, notifications, and microphone access have contextual entry points rather than one mandatory permission wall.

A disconnected inbox has a visible Connect mailbox action and mobile-data guidance. Authentication still depends on Mint connectivity; an emulator cannot validate SIM entitlement. Validate comprehension and time to first successful playback with someone unfamiliar with the app.

Transcription introduces a model download. Standard (Tiny, 104 MB) is the default; Enhanced (Base, 161 MB) and Highest quality (full Large v3, 1.78 GB) are available from the transcript and Settings. Large enables other languages and English translation. Costs and limitations are explained before selection. Playback remains available during setup. Highest quality does not guarantee accurate words or translation.

## 2. What assumptions might not be true?

| Assumption | Current response |
| --- | --- |
| Everyone discovers long-press selection | Inbox actions also offers Select messages. Selection includes a count, Select all, and scoped bulk actions. |
| Delete means only a local mark | Delete now saves and verifies a durable recording before carrier removal, then confirms absence by fresh sync. The retained copy appears in Saved. Removing that copy is separate. Old marks never automatically delete carrier messages. |
| Listening automatically marks a message read | Read/unread is explicit. Opening or playing does not silently change carrier flags. |
| Search can find words in every voicemail | It searches contacts, numbers, dates, and already-saved original/English text. Audio without a transcript cannot match unrecognized words. |
| Changing quality rewrites previous transcripts | Saved text is preserved until an explicit update. Original and English output remain separate. |
| Notifications are instant | Scheduled checks remain subject to Android delays. No standalone carrier push delivery has been verified. A test alert proves notification display only. |
| Sharing or calling should happen immediately | The handset opens the phone app with a number ready. Share asks for Transcript or Voice recording, then opens Android's destination chooser. Neither action silently sends content or places a call. |

## 3. What are three edge cases this design might fail in?

1. **A horizontal swipe becomes a recording tap or vertical scroll.** Direction arbitration, cancelled child gestures, rapid reversals, selection mode, boundaries, and preserved search have native regression coverage. Adjacent pages have a visible gap and track the finger. Filter taps remain available.
2. **Connectivity fails while work is underway.** Cached inbox data and saved text remain visible. Cached recording preparation now bypasses the carrier synchronization lock; a blocked refresh cannot delay opening that audio. Carrier deletion verifies the local copy first, reconciles uncertain responses, and reports partial results. Greeting upload/activation has retry reconciliation and read-back confirmation; no live replacement was performed.
3. **Large text or a short window leaves controls clipped.** A 320 × 480 dp per-activity viewport at 160% and 200% font size exercised six pages, selection, and top/bottom scroll positions. The sweep passed 455 assertions across 125 visible-control observations. It caught and fixed clipping of Settings' leading back target. This does not establish every intermediate scroll position, localization, or full screen-reader comprehension.

## 4. Compared with three familiar apps, what is missing?

| Reference | Pattern and current assessment |
| --- | --- |
| Gmail | Long-press selection, Select all scoped to visible results, and bulk actions are present. Carrier removal with an app-local Saved copy is explained before confirmation because it differs from a conventional trash folder. |
| Apple Phone | Opening a voicemail leads to paused audio and its transcript. Small handset/share glyphs sit in 48 dp targets. Play stays centered for every speed, with equal card and control-row vertical padding. Phrase taps seek into the recording. |
| Samsung Voice Recorder | Header condensation, recording/transcript context, and quality selection on the transcript are present. Model download/setup still asks more of the user than an integrated system service. |

Motion is shared across button presses, selection, menus, navigation, and filter transitions. Actions are immediate, interrupted animations retarget, and Reduce motion is supported. These are design judgments; similar controls do not establish identical platform behavior or usability.

## 5. What breaks with low context, no technical experience, or bad connectivity?

**A usable foundation with explicit remaining limits.** Saved recordings and text work locally. The player hides routine helper/status text and retains loading/error feedback. Lower English-only models do not pretend to support multilingual output. English text keeps source-audio phrase anchors without claiming exact translated-word timing. Public Spanish/French fixtures passed, although an idiom produced awkward literal English; users need to verify consequential text against audio.

Settings search filters actual controls. Light, Dark, AMOLED, native focusable controls, scaled text, reduced motion, and contrast checks are present. Full TalkBack listening/traversal, localization/RTL, broader devices, full storage, audio interruption, reboot/long-idle notification delivery, and first-time-user comprehension remain release-validation work.

## Outcome of the approved plan

Inbox polish, saved-copy carrier deletion, multilingual/English output, calling/sharing, model-description paragraphs, greeting setup, and adaptive-layout hardening are implemented. The explicitly authorized single-message Mint deletion was confirmed by fresh listings while its saved audio/transcript remained intact. Greeting behavior is tested with public audio and a simulated carrier, plus read-only Mint discovery; replacing the real greeting requires the user's chosen draft and explicit Save.

Direct carrier push remains unverified under the independent, non-default-dialer requirement. The [notification investigation](event-driven-notifications.md) records the Android eligibility and sender-compatibility limits. Scheduled fallback is honestly labeled; the companion notification-listener approach is not part of this design. The promo was paused during this review; the later [native product demo](../media/promo/README.md) follows the user's renewed video request.

## References

- [Samsung One UI guide](https://design.samsung.com/global/contents/one-ui/download/oneui_design_guide_eng.pdf)
- [Apple toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars)
- [Android Views accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)
- [Gmail multi-message actions](https://support.google.com/mail/answer/7401?co=GENIE.Platform%3DAndroid&hl=en)
- [Apple voicemail](https://support.apple.com/guide/iphone/iph003dae603/ios)
- [Samsung Voice Recorder](https://www.samsung.com/us/support/answer/ANS10000942/)
