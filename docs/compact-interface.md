# Compact interface in version 0.15

The visual pass reduces the oversized headings, padding, and repeated helper text reported on the S24 Ultra. Main titles are 28 sp, detail titles 24 sp, section titles 20 sp, and primary controls/body copy 15–16 sp. Secondary information is generally 12–13 sp. Text still follows Android font scaling; device display/font preferences are not changed.

Inbox cards and Settings rows use tighter padding and section gaps. Inbox play/menu actions share the caller row, avoiding a separate tall action footer; transcript previews use a single line. The recording player uses a 56 dp play target, smaller margins, and a shorter card. Standard controls retain 48 dp touch targets. Transcript text uses a continuous paragraph layout, with a soft highlight for the current phrase instead of a separate filled card around every phrase.

The transcript header contains a compact Standard (Tiny) / Enhanced (Base) text control. Full Whisper model names remain on the choice page and in accessibility labels. With larger fonts or a narrower window, that control moves below the heading instead of competing for one row. Model descriptions and setup/helper text are shorter; detailed explanations remain in About/Settings and the contextual tour.

The first-use introduction and anchored tips use the same smaller hierarchy. Theme colors, app-wide press feedback, transcript search, and audio behavior remain available.

## Back navigation

The screenshot helper sometimes opened a detail/settings page as its own task, leaving no previous app page behind it. The old top-left action simply finished that page. Settings, recording detail, and transcription quality now declare their parent pages and use a common Up action. Normal navigation finishes the current page; an isolated page reconstructs its parent stack. System Back gets the same fallback only for an isolated child page, preserving native behavior for normal activity stacks.

Thirty-three S24 Ultra navigation checks cover normal Settings/recording/quality paths, isolated Settings and recording pages returning to the inbox, and isolated quality pages returning to Settings and then the inbox. Both top-left and system Back were checked. Screenshot/test activity launches remain temporary; the normal app is reopened after testing.

The compact build also passed the 24 quality/tap-feedback, 14 playback, and 32 anchored-onboarding device checks. Theme contrast and 74 host behavior checks passed. Full large-font/TalkBack/landscape coverage remains open; the phone font setting stayed at 1.0 and was not changed.

Version 0.18 removes the persistent offline footer and lets the expanded inbox header scroll into a compact toolbar. Bulk selection also removes the expanded search/filter area while keeping selection actions pinned. See [inbox header](inbox-header.md).

Version 0.23 moves the Settings heading 12 dp toward the leading edge, with the existing 48 dp back-control touch target retained. This follows direct comparison with Samsung's native settings on the same phone; it is an optical alignment choice, not a universal Apple spacing requirement. Expanded inbox secondary controls become menu/refresh icons, with initial mailbox connection still presented as a text action. See the [five-question review](ux-review.md) for the app-wide assessment and remaining gaps.

Version 0.27 also prevents the Settings root's padding from clipping its optically offset back control. Per-activity 320 � 480 dp bounds at 160%/200% font scaling passed 455 layout/accessibility assertions across Main, Settings, recording, transcription quality, greeting, and tutorial pages. Phone-wide font, display, and security preferences were not changed. Full TalkBack validation remains separate.
