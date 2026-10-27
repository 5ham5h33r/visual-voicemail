# Motion and tap feedback in version 0.15

The app follows Apple's [motion guidance](https://developer.apple.com/design/human-interface-guidelines/motion): feedback should be brief, purposeful, interruptible, and optional. These are design principles applied to native Android controls; the app retains Android navigation and accessibility behavior.

Buttons press to 98% scale, icons to 96%, and cards/long rows to 99%, then return on release or cancellation. The press/release durations are 70/110 ms before Android's animation scaling. Touch listeners return control to Android immediately, so click actions do not wait for feedback to finish. Ripple, selection, checked state, text, and accessible labels also communicate the result.

The shared policy covers inbox play/actions/filter buttons, settings rows and back controls, recording transport/output/speed controls, transcript phrases and quality selection, onboarding navigation/examples, tour buttons, and app dialog actions. Switches keep native thumb feedback. Seek bars retain their direct native gesture response.

Filter results use a 140 ms reveal; transcript arrival and instructional cards use up to 180 ms and 8 dp of movement. Transcript search expands/collapses in 160 ms. Typing, routine playback ticks, and repeated search-match navigation do not get extra content animations. Native screen transitions preserve familiar forward/back behavior.

Settings → Appearance → Reduce motion disables custom scaling, reveals, and layout transitions, settles switch feedback, and sets app window animations to zero. Android's disabled-animation state is respected independently. No global device settings are changed. All five app screens share the same navigation policy.

The 24 quality/motion device checks verify actual touch press/release/cancel, click delivery before the release animation has to finish, normal scale after cancellation, reduced-motion scale/reveal states, and zero app window animations when enabled. Full TalkBack traversal and broader device/refresh-rate coverage remain open.

Version 0.18 adds a scroll-driven title crossfade, without a separate scroll/layout animation. The expanded header and messages share one native ScrollView. Reduce motion switches title states without a fade while keeping direct native scrolling. See [inbox header](inbox-header.md).


## Inbox selection in version 0.21

Selecting/deselecting and Select all update the existing row and checkbox instances instead of rebuilding the inbox. Native checkmark/ripple transitions can therefore finish normally, and card fills ease to the selected/unselected color in 160 ms. Checkbox visibility and caller-column bounds transition together in 160 ms; selection toolbar/actions reveal in 160 ms and changed counts in 120 ms. Selection state, action availability, and accessibility states update immediately.

Rapid changes cancel and retarget the current card color, while entering/leaving selection cancels old layout transitions before starting another. Logical mode is tracked separately from a fading view's visibility, so a rapid exit/re-entry cannot hide an active checkbox. Leaving selection disables outgoing checkbox input immediately. Ordinary content refreshes and destruction dispose of old row animations. Offscreen rows settle directly instead of animating unseen work.

Reduce motion and Android's disabled-animation state settle checkmark drawables, card colors, and layout changes immediately. Native checkbox press/ripple behavior is retained; no tap waits for an animation. The S24 Ultra passed 30 selection checks, with an active native checkbox animation observed, plus retained-view identity, immediate model changes, color completion, rapid reversals, Select all, rapid mode exit/re-entry, accessibility states, and reduced motion. Synthetic inbox entries were view-only; no carrier flags or personal audio were changed.

The build also passed 43 bulk-action checks (including waiting for inbox window focus after dismissing a dialog), 38 header, 32 onboarding, 28 quality/motion, and 33 navigation regressions: 204 phone checks in total. All 108 host checks and native theme contrast checks passed.

## Coordinated selection chrome in version 0.22

Long-press selection no longer removes the expanded header in one layout step. One 220 ms animator coordinates header height/opacity, bulk-action height, toolbar crossfade, and the native list offset. Both toolbar states share one frame, so swapping titles does not change their footprint. The expanded header and the list move together; an already-scrolled inbox keeps its list-relative position.

A reversal starts from the current rendered height and opacity. Only the logically active toolbar receives touches or accessibility focus during the fade; the outgoing search/header controls cannot take input. Selection state and row/checkmark feedback still update immediately. Reduced motion settles the complete layout without a transition. Saving state during a transition uses the target coordinate system rather than a partially collapsed header.

The native gesture check samples real pre-draw frames from a physical long press on a synthetic inbox. It checks a partial visible header, gradual list movement, the final state, physical toolbar taps, reversal, scrolled-list preservation, reduced motion, and recreation mid-transition. No carrier writes or personal audio are involved.

The S24 Ultra passed 19 transition checks, sampling 40 rendered frames with a largest list-position step of 58 physical pixels across a 927-pixel header. The existing 30 selection, 43 bulk-action, 38 header, 32 onboarding, 33 navigation, and 28 quality/motion checks also passed: 223 phone checks total. All 108 host behavior checks and native theme contrast checks passed. App preferences were restored and version 0.22 was reopened with the normal inbox.
