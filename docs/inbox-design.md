# Native inbox and player milestone

The playback milestone gives the user a simple native Android inbox for their existing Mint voicemail. It adds system-following light/dark themes, saved Light and Dark overrides, formatted caller numbers and local dates, and an expandable player. The top-right appearance control opens the theme choices. The current theme affects cards, text, controls, dialogs, and system bars.

## Design brief

The primary task is to find a voicemail and listen to it with one tap. The screen should feel calm and familiar on a Samsung phone. Caller identity comes first, followed by date and duration. Listening controls appear beside the recording and expand when selected. Use a neutral background, grouped rounded surfaces, one restrained green accent, a consistent spacing scale, native typography, and touch targets of at least 48 dp. Avoid decorative charts, fake waveforms, promotional banners, or unnecessary navigation.

Make loading, saved, playing, paused, complete, empty, and retry states understandable. Preserve the saved inbox on refresh failure. Use scalable text and wrapping layouts. Keep important controls labeled for accessibility; the seek control exposes elapsed and total time. Colors come from paired light/night resources, rather than automatic inversion. Source palettes were checked numerically for text contrast; a full TalkBack audit and device testing with increased font sizes remain outstanding.

Sources consulted:

- [Samsung One UI principles](https://developer.samsung.com/one-ui/index.html) and [basic layout](https://developer.samsung.com/one-ui/layout/basic.html): content focus, comfortable grouping, and reachable interactions.
- [Android dark theme guidance](https://developer.android.com/develop/ui/views/theming/darktheme): follow the device by default and offer Light/Dark choices.
- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/): familiar hierarchy and platform conventions, used as visual inspiration for an Android interface.
- [Mobile App UI/UX Design skill](https://github.com/ceorkm/mobile-app-ui-design/blob/main/SKILL.md): a structure, hierarchy, spacing, and state review checklist. Its web-specific examples and decorative suggestions are not requirements for this native voicemail app.

## Playback behavior

Only one MediaPlayer is active. The player supports pause/resume, a seek bar, elapsed/total time, and ten-second skip controls. Cached recordings are looked up independently of the network worker, so refreshing does not delay local playback. Download writes are atomic to avoid treating a partial recording as a saved file.

The app requests transient audio focus for speech and pauses when focus is lost. Leaving the screen also pauses. It never automatically resumes on return. Activity/request generation guards discard stale download or preparation callbacks after destruction or another selection. A refresh rebinds the active controls without stopping a recording that is still in the inbox.

## Live validation on the user's S24 Ultra

- Installed version 0.3 with the existing development signing key, preserving the inbox and downloaded recording.
- Inspected both light and dark appearances on the phone, including the appearance dialog. Tested saved Light after force-stop/relaunch and switching back to Dark/device default.
- Played the real cached recording, paused and resumed it, and observed completion at 18,500 ms.
- Ten-second forward skip moved the paused position from 1,505 to 11,505 ms. Backward skip and seek-bar dragging also changed the position; a later drag reported 13,651 ms.
- Refreshed during playback: mailbox HTTP 200, one message, encrypted inbox saved, and player still playing.
- Sent the app to the background while playing: playback paused at about 1.3 seconds and did not automatically resume on return.
- Samsung Phone remained the default dialer. T-Mobile Visual Voicemail remained disabled with its app data preserved; My VVM was absent. No forwarding, greetings, PIN, or server message flags were changed.

The first live Light-mode switch revealed a configuration timing crash. The final implementation applies the override when attaching the base context, before activity resources are loaded. Subsequent switching and relaunch checks passed.

This is an installed development milestone for user feedback. Background delivery/notifications, transcription, deletion, and read-status synchronization are not implemented. Audio-focus interruption is implemented but has not been exercised with a live incoming call. Large fonts, rotation, multiple simultaneous message selections, and other carriers still need broader testing. Existing protocol/session caveats remain in the other research documents.
