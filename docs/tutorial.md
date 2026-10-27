# First-use tutorial in version 0.11

An optional seven-topic guide appears the first time this version opens, when no tutorial acknowledgment is saved. It can be replayed from **Settings → Help & tutorial → App tutorial**. The tutorial uses the existing Light, Dark, and AMOLED palettes, native illustrations, a step counter, progress indicators, and scrollable text.

## Topics

1. **Welcome:** the existing mailbox, no forwarding setup, keeping the preferred dialer, and where to replay the guide.
2. **Connect your mailbox:** Mint-only support, choosing Mint for mobile data, the inbox Connect action, connection help, and cached audio versus recordings that still need downloading.
3. **Inbox & search:** All/Unread/Marked filters, contact/number/date search, optional local contact names, refresh, and last-update information.
4. **Playback:** play/pause, seeking, skips, speed, audio output, and pausing when leaving the app.
5. **Save & organize:** Save audio copy and Download location, cache versus exports, carrier read/unread actions, and reversible local deletion marks. Listening alone does not mark a message read; permanent deletion is unavailable.
6. **Notifications:** optional Android permission, opening the unread inbox, the 15/30/60-minute checks, possible Android delays, notification controls/testing, and force-stop behavior.
7. **Make it yours:** appearance including true-black AMOLED, Settings/privacy, replay help, and a clear statement that transcription, transcript search, and permanent message deletion remain unavailable.

## Navigation and first-use behavior

Next/Back buttons and horizontal swipes move between topics. The Topics picker jumps directly to a section. Content scrolls vertically when it does not fit; the navigation controls remain outside the scrolling content. Page titles/counters and button labels expose their purpose to accessibility services. System Back moves to the previous topic, or closes at Welcome; Android 13+ uses the platform back callback.

Skip or Open inbox acknowledges the automatic tour. An unfinished automatic tour remembers its page across process exit. Page and scroll position are also saved for activity recreation. Replay starts at Welcome, and Close/Done returns to Settings. Completing a replay does not reset playback, appearance, contact, or notification preferences.

The tutorial activity is internal and offline. It does not start carrier authentication, play a recording, request permissions, or change voicemail. Automatic foreground refresh and the first notification-enable explanation wait until the automatic tour is acknowledged. Already enabled background checks remain independent. An explicit notification tap bypasses the optional automatic tour so users can reach their messages; the guide remains available in Settings.

## Validation and UX review

Built, signature-verified, and installed version 0.11 on the S24 Ultra. The guide appeared automatically on upgrading an installation with no tutorial flag. Checked Next, horizontal swipe, system Back, topic selection, completion, force-stop/reopen without reappearing, and resuming an interrupted automatic tour at step 3. Settings replay started at Welcome; Close and Done returned to Settings. Vertical scrolling revealed further instructions without changing the topic.

Also installed an isolated temporary application ID using the same compiled code to check empty first-use state without clearing the user's real app. Welcome appeared, Skip opened the unconnected inbox, the Skip acknowledgment survived force-stop, and Settings replay was available before connecting. No session/inbox record or carrier-request stage was created in that test app. The temporary app was uninstalled and the original mailbox installation preserved.

Light and AMOLED screenshots confirmed their expected canvas colors; the Dark guide was also visually inspected. With font scale temporarily increased to 1.5×, the topic was retained and navigation remained available in portrait. Original theme, font scale, and portrait-lock settings were restored and verified. Landscape rotation could not be reliably confirmed in this device test and remains unverified. Full TalkBack traversal, other phones/locales, and extreme text/display sizes still need coverage.

Applying the user's five UX questions: seven topics can feel long, so Skip and a topic picker avoid making every page mandatory; progress and saved position reduce interruption friction. The guide assumes no contact or notification permission and works without Wi-Fi. It explains the important local-mark/cache/scheduled-alert limitations in the relevant topic. This is a heuristic review plus device checks, not a measured usability or abandonment study.
