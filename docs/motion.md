# Motion and tap feedback in version 0.15

The app follows Apple's [motion guidance](https://developer.apple.com/design/human-interface-guidelines/motion): feedback should be brief, purposeful, interruptible, and optional. These are design principles applied to native Android controls; the app retains Android navigation and accessibility behavior.

Buttons press to 98% scale, icons to 96%, and cards/long rows to 99%, then return on release or cancellation. The press/release durations are 70/110 ms before Android's animation scaling. Touch listeners return control to Android immediately, so click actions do not wait for feedback to finish. Ripple, selection, checked state, text, and accessible labels also communicate the result.

The shared policy covers inbox play/actions/filter buttons, settings rows and back controls, recording transport/output/speed controls, transcript phrases and quality selection, onboarding navigation/examples, tour buttons, and app dialog actions. Switches keep native thumb feedback. Seek bars retain their direct native gesture response.

Filter results use a 140 ms reveal; transcript arrival and instructional cards use up to 180 ms and 8 dp of movement. Transcript search expands/collapses in 160 ms. Typing, routine playback ticks, and repeated search-match navigation do not get extra content animations. Native screen transitions preserve familiar forward/back behavior.

Settings → Appearance → Reduce motion disables custom scaling, reveals, and layout transitions, settles switch feedback, and sets app window animations to zero. Android's disabled-animation state is respected independently. No global device settings are changed. All five app screens share the same navigation policy.

The 24 quality/motion device checks verify actual touch press/release/cancel, click delivery before the release animation has to finish, normal scale after cancellation, reduced-motion scale/reveal states, and zero app window animations when enabled. Full TalkBack traversal and broader device/refresh-rate coverage remain open.
