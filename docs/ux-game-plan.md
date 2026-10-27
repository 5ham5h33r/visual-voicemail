# UX and reliability game plan

Status: approved by the user. Implementation has resumed, including swipe performance, card separation, matching menu styling, rounded selection controls, and actual carrier deletion with retained local copies. The inbox UI pass and notification eligibility investigation are merged. Carrier deletion with protected Saved copies is merged and the user-authorized Mint removal test passed. Multilingual output, calling, sharing, smaller recording icons, balanced player spacing, and model-description paragraphs passed validation. Greeting setup and broader layout/accessibility checks remain next. Direct push delivery is still unverified; the app remains independent of the T-Mobile companion app. Tests do not change existing personal voicemail, carrier configuration, greetings, read status, or device preferences.

## Assessment

The [five-question review](ux-review.md) covers onboarding, connection, permissions, inbox navigation and search, selection, playback, transcripts/model setup, notifications, Settings, export, and failure recovery. It is a heuristic review supported by implementation inspection and native device checks, not a measured user study. The foundation is usable, but setup, vocabulary, notification delivery, and broader accessibility/recovery testing remain concerns.

## 1. Finish and verify the current UI pass

- Resolve the latest rounded-checkbox alignment regression. Retain the native CheckBox widget, checked/accessibility semantics, a 48 dp target, interruptible check animation, and Reduce motion. Inspect its rounded appearance in all three themes.
- Finish the coordinated caller/preview/status indentation, swipe/tap navigation, animated tab indicator, scoped empty states and recovery actions, quieter Inbox actions/Refresh controls, and 12 dp Settings header alignment.
- Preserve search text between filters, vertical scroll behavior, selection scope, first/last boundaries, and Back navigation. Test actual touches, cancellations, rapid reversals, keyboard visibility, and recreation.
- Clarify Marked versus actual deletion. Recommend changing the bulk action label to Mark for deletion; keep Restore obvious. Define when a message becomes read before adding any automatic read update.
- Run large-font/narrow-window, focus/TalkBack, contrast, and touch-target checks in suitable test configurations without changing the user's phone-wide preferences.

Exit criteria: relevant regressions pass, visual review in Light/Dark/AMOLED, original data/preferences preserved, reviewed screenshots, clean CI, and one reviewed PR merged with its branch removed.

## 2. Investigate event-driven voicemail delivery before changing polling

- Trace Android's OMTP/VVM SMS dispatch, carrier-app configuration, and eligibility on this S24 Ultra. The public VisualVoicemailService contract routes events to the default dialer, so merely implementing that service does not establish a supported standalone path.
- Examine the original T-Mobile client's Firebase registration and carrier API. Registration acceptance is not proof that the carrier can deliver to an independent Firebase project: sender/project compatibility must be established.
- Evaluate message-waiting callbacks as a possible supplemental signal, distinguishing a mailbox-waiting boolean from a reliable new-message event. Do not present it as a complete push replacement.
- Do not re-provision the mailbox, register a replacement token, send activation SMS, or request broad SMS access merely to obtain a positive-looking test result. First establish the supported mechanism and its effects.
- If a compatible mechanism exists under the no-default-dialer constraint, use its event to request an authenticated mailbox sync; keep fallback scheduled checks, ID-based deduplication, and generic private notification content.

Exit criteria: a documented supported path and an agreed end-to-end new-voicemail test with the app offscreen/process absent, repeated arrivals while another message remains unread, duplicate events, offline recovery, reboot, and Android battery restrictions. If the carrier/OS gate prevents support, report the exact constraint and options rather than promise instant delivery or poll more frequently.

Primary starting points: [Android VVM service](https://developer.android.com/reference/android/telephony/VisualVoicemailService), [AOSP VVM](https://source.android.com/docs/core/permissions/voicemail), and [FCM sender mismatch](https://firebase.google.com/docs/cloud-messaging/error-codes).

## 3. Free carrier mailbox space while retaining local copies

The user explicitly requested that deleting a message remove it from the carrier mailbox, with the recording retained in the app. This supersedes the earlier local-marks-only behavior for the next implementation phase.

- Save and verify an app-local recording copy and encrypted metadata before requesting carrier deletion. Stop safely if download, verification, or local persistence fails.
- Confirm absence in a subsequent carrier sync. Report pending/partial outcomes instead of claiming removal after a request alone.
- Expose retained copies under Saved. Playback-cache clearing must preserve these copies; removing a saved copy is a separate explicit action.
- Do not automatically delete older local marks created under the previous behavior. Migrate them without erasing carrier data and require the new explicit action.
- Test with disposable audio and a simulated carrier first. Do not delete the user's existing voicemail for validation.

Exit criteria: archive-before-delete ordering, durable-copy verification, timeout/retry and partial-batch behavior, sync reconciliation, retained transcripts/search/offline playback, protected cache clearing, and a user-facing confirmation that explains carrier removal versus app-local retention.

## 4. Add multilingual transcription and optional English translation

- Verify the current engine and model manifests support the intended language-detection and translation tasks; distinguish English-only model choices from multilingual ones.
- Offer automatic language detection with manual override, plus Original language or English output in plain language. Keep model details and download/time costs available.
- Store the original transcript and English translation separately, identify their language/model/task, and preserve existing saved text until the user explicitly generates or replaces it.
- Make both outputs searchable, with clear match context. Preserve phrase-to-audio anchors where they are valid; never imply that translated words provide exact word-level timing.
- Keep recognition and translation on the phone. Show progress, cancellation, and retry without blocking playback.

Exit criteria: public multi-language fixtures, original/English output checks, silence/no-speech, mixed-language and noisy recordings, unsupported language behavior, cancellation, offline operation after setup, cache migration, search, and preservation of saved transcripts. Do not infer translation support merely from the presence of a large model file.

## 5. Add voicemail greeting setup in Settings

- Start with read-only discovery of current greeting metadata and supported Mint operations. The original client has greeting-management methods, but our session has not verified greeting changes.
- Design Current greeting -> Record or choose audio -> Preview -> Save -> carrier confirmation. Request microphone access only when recording is chosen.
- Keep the existing active greeting until the replacement is successfully uploaded/activated and confirmed. Handle unsupported formats, interruption, failed upload/activation, and unavailable carrier capability explicitly.
- If direct editing is not supported, provide an honestly labeled call-to-voicemail setup route; do not portray that fallback as native greeting upload.

Exit criteria: recording/preview and failure paths tested without changing the real greeting, followed by a user-chosen save test and carrier read-back verification. No forwarding or default-dialer change.

## Delivery practice

Each approved phase gets a descriptive branch, coherent commits, a PR with relevant validation, CI, merge, and branch cleanup. Document what is implemented, what is actually reproduced on the phone, and what remains a hypothesis. No proprietary sources, keys, personal audio/transcripts, raw phone captures, or device identifiers go into commits.
