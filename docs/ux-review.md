# Five-question UX review

This is a heuristic review of the working version 0.23, not a usability study or measured conversion report. Evidence includes the native S24 Ultra screens, sanitized documentation captures, implementation inspection, and device regression checks. The scope covers introduction and permissions, mailbox connection, inbox/search/selection, recording playback, transcripts and model setup, notifications, Settings, downloads, and recovery states.

The UI implementation now passes 293 device checks, including physical gestures, direct drag tracking, rounded glyph pixels, preview alignment, and native menu selection. The user approved the companion [game plan](ux-game-plan.md), including notification research, multilingual/English translation, greeting setup, and a subsequent carrier-deletion flow that retains verified local copies.

The core listen/read/search flow is coherent. The app still needs work before calling its first-use, accessibility, and failure-recovery experience complete. Functional test counts do not establish that new users understand the interface.

## 1. Where are users most likely to drop off?

**Partial pass. The largest risks are before the first successful playback and the first transcript.**

The four-screen introduction can be skipped and the subsequent guide highlights actual controls. Optional contacts and notifications have their own explanations. Those are useful safeguards, but an introduction plus a tour can still delay the user's goal. Validate a shorter route to a real inbox with first-time users; the tutorials should help when needed rather than become a prerequisite.

Mint connection depends on mobile data. A person on good Wi-Fi can still fail the first connection and assume the app is broken. The source shows actionable network errors, but we have not tested comprehension with new users. Version 0.23 adds a prominent **Connect mailbox** action to the disconnected state instead of expecting a small Refresh control to communicate initial setup. A future connection flow should identify the unmet prerequisite before asking the person to retry.

Transcription adds another setup step: a model download. The app explains sizes, recommends Wi-Fi, and keeps playback available. However, choosing among three models and waiting for setup is still substantial work. Standard should remain the first choice, with larger options discoverable later. Full Large v3 is a 1.78 GB download and may take minutes per recording; “Highest quality” must never be interpreted as an accuracy guarantee.

## 2. What assumptions might not be true?

**Needs work. Several hidden assumptions affect meaning, not just appearance.**

| Assumption | Evidence and response |
| --- | --- |
| Everyone knows long-press selects messages | It is useful as a shortcut but insufficient as the only entry. Version 0.23 retains an explicit **Inbox actions → Select messages** route and updates the guide. |
| More visible text buttons make the inbox easier | Select and Refresh competed beside a small section label. Version 0.23 removes the standalone Select label and uses a labeled, 48 dp Refresh icon. Initial connection still has a visible text action. |
| “Marked” means the same thing to everyone | It can suggest a favorite, flag, or deletion mark. The empty state and populated explanation now identify deletion marks, but the short tab label still deserves a wording pass. The bulk label now says “Mark for deletion.” The user has since requested actual carrier deletion with local retention; that is a separate follow-up. Carrier recordings are not erased. |
| Opening or listening means a message becomes read | Current read status is explicitly changed through carrier actions; simply opening the player does not automatically mark it read. Decide this behavior deliberately and explain it before changing carrier flags automatically. |
| Transcript search searches every recording | Only already-saved transcripts are searchable. The guide and empty-search copy explain the saved-text scope. A recording without a transcript cannot match words that have not been recognized. |
| A quality change replaces old transcripts | It does not. Existing transcripts remain, the saved quality is shown, and replacement requires an explicit update. Keep this behavior. |
| New-message alerts arrive instantly | They use scheduled mailbox checks, subject to Android delays. Setup and Settings explain this; a local test notification proves display permission, not new carrier delivery. |

## 3. What are three edge cases the design might fail in?

**Partial pass. These are the highest-impact cases for this app.**

1. **A horizontal swipe becomes a scroll, a recording tap, or a selection gesture.** Version 0.23 arbitrates direction after touch slop, cancels the old child gesture when horizontal navigation wins, preserves ordinary vertical scrolling, disables filter swipes during selection, and resists the first/last boundaries. Tab taps remain available. Interrupted navigation and cancelled gestures need explicit device checks, not a screenshot review alone.
2. **An empty screen has multiple possible causes.** No mailbox, no unread messages, no deletion marks, no search matches, contact lookup in progress, and a failed refresh are different states. Version 0.23 gives each filter/search scope its own explanation and a recovery action. A failed refresh must keep the saved inbox and report failure instead of claiming the mailbox is empty. Search text stays in place when switching tabs.
3. **Work is interrupted.** Bulk carrier updates can partially succeed; a local transcription queue or large model can be cancelled or interrupted; folder access can be revoked. The app preserves old transcripts, verifies read updates, reports partial outcomes, and separates private playback from exported copies. Broad process-death recovery, full storage, revoked provider access, audio route changes, and incoming-call interruption still need device coverage. Do not imply that current tests cover all of these.

## 4. Compared with three familiar apps, what's missing?

**The core patterns are recognizable; the vocabulary and polish are less consistent.**

| Familiar reference | Pattern worth retaining | Our assessment |
| --- | --- | --- |
| Gmail | Long-press, a selection count, Select all, and actions on the selected set | Present. Select all correctly uses the current filter/search results. An explicit menu route supplements the gesture. Deletion marks must stay visibly distinct from actual mail-style deletion. |
| Apple Phone | A message leads naturally to playback and its transcript | Present. Opening a row shows the recording paused; Play is a separate immediate action. Tappable transcript phrases and transcript search support catching up. Audit all child-page back-control spacing together, rather than fixing Settings alone and assuming consistency. |
| Samsung Voice Recorder | A recording and its transcript form one understandable task | Present. Quality is reachable from the transcript itself, with plain-language choices plus model names. Model setup remains text-heavy and requires more effort than the familiar “Transcribe” mental model. |

Version 0.23 adds directional list motion, an animated tab indicator, swipe navigation, quieter secondary controls, and a Settings header moved 12 dp toward the leading edge. A 48 dp control remains tappable even when the visible icon is small. These changes improve hierarchy; they are not a substitute for testing whether people find the actions.

## 5. What breaks with low context, no technical experience, or bad connectivity?

**Good foundation, incomplete validation.**

Saved recordings and saved transcripts work offline; a failed mailbox refresh preserves cached data. This is a real strength. The UI must distinguish saved content from content that still needs downloading, and playback from a transcript model that has not been installed.

Permissions are optional and denied access has a Settings route. Contact lookup is local. Folder export uses Android's picker, but “private playback cache” versus “saved audio copy” is still a distinction that some people will miss. Keep the export action and its destination clear; avoid leading with storage implementation details.

The app supports Light, Dark, AMOLED, font scaling, native focusable controls, and Reduce motion. Contrast and relevant touch targets are checked. Full TalkBack traversal, large-font/narrow-window behavior across every flow, localization/RTL, and a first-time user session are still open. Those are release-readiness gaps, not reasons to shrink tap areas or remove the explicit selection route.

## Priorities after this pass

1. Validate the mobile-data connection/retry flow and time to first successful playback with a new user.
2. Resolve the meaning of Marked/Delete and explicitly decide when listening should mark a message read.
3. Complete TalkBack, large-font, narrow-window, audio-interruption, and interrupted-work coverage.
4. Reduce model-setup decision load while keeping technical details and large-download costs available.
5. Investigate a Settings → Voicemail greeting flow. The original carrier API exposes greeting management, but changing greetings through our Mint session is not yet verified and is not implemented here.

## References

- [Samsung One UI design guide](https://design.samsung.com/global/contents/one-ui/download/oneui_design_guide_eng.pdf): app bars and selection entry through an explicit option or long-press.
- [Apple toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars): familiar navigation and action organization.
- [Android Views accessibility](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views): at least 48 dp interactive targets.
- [Gmail multi-message actions](https://support.google.com/mail/answer/7401?co=GENIE.Platform%3DAndroid&hl=en): touch-and-hold selection.
- [Apple voicemail](https://support.apple.com/guide/iphone/iph003dae603/ios): voicemail playback and transcript context.
- [Samsung Voice Recorder](https://www.samsung.com/us/support/answer/ANS10000942/): recording-to-transcript workflow.

The comparisons are design judgments based on those patterns, not a claim that the three products use identical layouts, networks, or transcription systems.
