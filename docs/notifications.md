# New voicemail notifications in version 0.9

After connecting a mailbox, the app offers to enable new-voicemail alerts. Android 13 and newer also require the system notification permission. Settings → Notifications contains enable/disable, a 15/30/60-minute check interval, Android sound/vibration/lock-screen controls, a labeled test notification, and the last completed background-check result. Denying notification access leaves inbox search and playback available; Settings identifies the block and links to Android's notification controls.

Background checks use Android JobScheduler with a network requirement and a persisted periodic job. They retrieve mailbox metadata, renew the existing carrier session when needed, and do not download audio. Foreground operations and background checks use one synchronized carrier client. Cancellation stops the background request and prevents a stopped job from reporting completion. Atomic encrypted storage uses a shared process-wide lock to protect concurrent readers and writers.

These are scheduled checks, not instant carrier push notifications. The default interval is about 15 minutes. Android controls execution and may delay it because of idle state, battery restrictions, network availability, or job quotas. Force-stop prevents background work until the app is reopened. Persisted scheduling is configured for reboot, but reboot and long idle delivery still need device testing. Carrier push uses Firebase in the inspected vendor app; registering an independently owned Firebase project with the carrier remains unverified and is not implemented here. The app needs no default-dialer role, call forwarding, SMS access, or continuously running foreground service for scheduled checks.

## Arrival and privacy behavior

- The initial mailbox, or the cached mailbox when enabling alerts, establishes a silent baseline. Existing unread voicemail is not replayed as new.
- A successful sync compares message IDs with an encrypted local history and alerts for newly discovered unread messages. A message already read elsewhere stays quiet. Marking an existing message unread does not make it a new arrival.
- One notification summarizes pending new voicemail. Repeated syncs leave an unchanged notification alone. A newly arrived message may alert again; count reductions update silently. Read or removed messages leave the pending set.
- Tapping opens the unread inbox, clears any search, and acknowledges the notification. Swiping it away also acknowledges it. Neither action changes carrier read flags. Acknowledged IDs do not alert again on subsequent checks.
- Disabling alerts cancels background jobs and active voicemail notifications. A failed mailbox check preserves the saved inbox and retries at the next scheduled opportunity, without repeated error alerts.
- Alerts show generic text and a count, without names, numbers, or transcription. The lock-screen public version is generic. Notification history contains only message identifiers and pending status, encrypted with the existing Android Keystore storage. The history is capped at 20,000 IDs, prioritizing IDs still present in the mailbox; an older evicted ID reintroduced by the carrier could be treated as new.

The test-notification button checks Android display permissions only. It does not prove that a background job or carrier delivery has occurred.

## Validation

The version 0.9 APK built and passed signature verification. The host suite has 60 checks: 27 inbox/search/read/order, 10 cache safety, 8 audio export format, and 15 notification-ledger checks. The notification cases cover baselines, actual ID differences, read transitions, acknowledgment, batches, missing IDs, disabled access, and bounded history.

On the S24 Ultra, the app's enable explanation and Android permission dialog appeared. A labeled test notification was visible in the notification shade, and tapping it opened the app. Twenty instrumented device checks exercised synthetic arrival/deduplication, generic notification content, private/public visibility, dismissal via its real PendingIntent, read/removal cleanup, disabling and job cancellation, persisted network-constrained scheduling, shared-client identity, and cancellation before a network request. Those checks did not alter the real inbox or contact/caller data. Synthetic notification state was removed afterward.

Four additional device checks revoked notification permission temporarily, verified that alerts were blocked and jobs canceled, and confirmed a synthetic arrival produced no notification. Permission was restored afterward. With the app off-screen and its process absent, a forced run of the actual periodic JobScheduler job started the app process, fetched the real Mint mailbox with HTTP 200, saved the encrypted inbox, and recorded a successful background-check result. This verifies the background execution path, rather than proving Android's natural periodic or idle timing.

Actual new-carrier-message arrival, long idle timing, battery/data usage over time, offline recovery, device reboot, and additional Android versions still need coverage. Notification posting and synthetic arrival tests must not be presented as proof of instant carrier push delivery.

## Platform references

- [Android notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [JobInfo.Builder scheduling and persisted-job behavior](https://developer.android.com/reference/android/app/job/JobInfo.Builder)
- [JobService lifecycle and cancellation](https://developer.android.com/reference/android/app/job/JobService)
- [VisualVoicemailService role restrictions](https://developer.android.com/reference/android/telephony/VisualVoicemailService)

## Event-driven delivery research

The [SMS/push investigation](event-driven-notifications.md) documents Android eligibility, read-only S24 Ultra capability results, carrier configuration, and the remaining Firebase registration/delivery uncertainty. It does not change polling frequency or claim instant carrier push support.
