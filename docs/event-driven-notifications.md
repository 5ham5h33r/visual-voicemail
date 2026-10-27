# Event-driven voicemail notification investigation

## Result

Carrier-triggered VVM notifications are real, but the standard Android delivery path is not currently available to this independently signed standalone app under the requirement to keep Samsung Phone as the default dialer. T-Mobile's own client also has a Firebase push path; compatible registration and delivery to an independent client remain unverified. No polling frequency was changed.

This is a capability investigation, not proof of instant notification delivery. No SMS was sent, push token registered, carrier provisioning changed, or additional phone/SMS permission requested.

## Candidate mechanisms

| Mechanism | Assessment under the current constraints |
| --- | --- |
| Android VisualVoicemailService / VVM SMS filtering | The public service contract targets the default dialer. SMS-filter settings additionally recognize the system dialer or configured carrier VVM app. Merely declaring a service or adding BIND_VISUAL_VOICEMAIL_SERVICE does not grant eligibility. |
| An ordinary SMS receiver | Broad SMS access does not guarantee receipt of VVM SMS already consumed by telephony filtering, and this client's current carrier registration is not evidence that SMS events will be sent to it. Not a verified replacement; no SMS permissions or activation commands were added. |
| Carrier Firebase push registration | Static analysis of the original app shows token registration and a Firebase messaging service. FCM tokens are tied to authorized senders; a token from an arbitrary project does not establish that T-Mobile can deliver to it. Carrier-compatible sender/registration support and an actual delivery test are required. |
| Message-waiting indication | A possible supplementary hint while an app callback is registered, requiring READ_PHONE_STATE or carrier privileges. A mailbox-waiting boolean is not a new-voicemail identifier and does not establish cold-start delivery after process death. |

Android's [VVM service contract](https://developer.android.com/reference/android/telephony/VisualVoicemailService) and [TelephonyManager SMS-filter contract](https://developer.android.com/reference/android/telephony/TelephonyManager#setVisualVoicemailSmsFilterSettings(android.telephony.VisualVoicemailSmsFilterSettings)) define the native eligibility rules. [AOSP's VVM description](https://source.android.com/docs/core/permissions/voicemail) explains mailbox-update SMS and carrier-app configuration. [Firebase documents sender/token mismatches](https://firebase.google.com/docs/cloud-messaging/error-codes), and [Android documents message-waiting callbacks](https://developer.android.com/reference/android/telephony/TelephonyCallback.MessageWaitingIndicatorListener).

## Device observations

A temporary, independently signed instrumentation check ran in the app UID on the S24 Ultra, API 37. Six read-only queries reported:

- The app is not the default dialer or a system app.
- It has no carrier privileges or MODIFY_PHONE_STATE permission.
- READ_PHONE_STATE and RECEIVE_SMS are not granted.

The shell carrier-configuration dump contained a CVVM profile, client prefix `//VVM`, port 1808, and carrier VVM package `com.tmobile.vvm.application`. That package is different from this app. These are generic configuration observations, not subscriber identifiers or proof that an incoming VVM SMS reaches our package. The read-only check is reproducible with `research/check_device_ui.py --test notification-eligibility` and reports actual capabilities rather than asserting they must always be absent.

The original client's static Firebase registration flow is separate evidence. It is not a reproduced event delivery test and proprietary source/configuration is not included here.

## Consequence and next validation

Retain scheduled checks as an honestly labeled fallback, with existing cached data and notification deduplication. Do not increase their frequency as a substitute for push reliability.

A supported standalone push path would need carrier-compatible registration and sender authorization, followed by an agreed new-message test with the app offscreen and its process absent. Test repeated arrivals while the mailbox already has unread voicemail, duplicates, offline recovery, reboot, and battery restrictions. Registering a token or posting a local test alert alone is insufficient. No verified compatible push path is shipped by this investigation.

## Independent-client requirement

The user chose to keep this app independent. A companion-app notification listener was considered, but is not part of the selected design. Samsung Phone and Google Messages can remain the defaults: those roles do not transfer T-Mobile Visual Voicemail's configured carrier-app eligibility to this package. Direct push investigation must establish compatible carrier registration and actual delivery to this client, without depending on the T-Mobile app or copying its signing identity.
