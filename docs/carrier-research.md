# Mint visual voicemail investigation

Investigation date: October 26, 2026 (America/Los_Angeles).

## Current conclusion

A standalone carrier-mailbox app now authenticates and loads the user's real Mint inbox. Cellular identification, service-instance token issuance, and a mailbox HTTP 200 were reproduced without carrier privileges or a default-dialer role. Audio playback, persisted credentials, and background notification delivery are the remaining milestones. See [the live mailbox validation](mailbox-access.md).

The official app having carrier privileges does **not** establish that every authentication path needs them. The cellular-IP path accepted the independent probe's identification request, but that alone does **not** prove that the server will issue the mailbox token or accept its push registration. See [the independent probe results](independent-probe.md).

## Inputs and verification

| Application | Package | Version analyzed | APK SHA-256 |
| --- | --- | --- | --- |
| T-Mobile Visual Voicemail | `com.tmobile.vvm.application` | `10.8.0.1536924` | `0079b07c45740c8736898738039b72426690986f0477ce066241e6a3a1bb4789` |
| My Visual Voicemail | `com.vna.service.vvm` | `12.5.1.484513` | `3ca587131b2e13284f53ce6ba48298abfb4c039f3aaa3416dc1d91b367acb7af` |

The APKs came from Aptoide's public catalog/download service. Metadata and download provenance are saved locally. Google's Android build-tools 36.1 `apksigner` verified both APKs, including v2 and v3 signatures and their source stamps. T-Mobile also verifies under v3.1 and has an Android-version-dependent signing certificate rotation.

- T-Mobile current signer SHA-256: `92b5f8117fbd9bd5738ff168a4fa12cbe284be834ede1a7bb44dd8455ba15920`.
- My VVM signer SHA-256: `3be6d6e16f92ed517f8fa5321be0b7e01cb33a66c5dd362a626292e818953171`.

JADX 1.5.6 recovered resources and Java representations for both apps. It reported 119 method-level errors for T-Mobile and 133 for My VVM. These outputs are incomplete decompiler representations, especially around Kotlin coroutine control flow; they are not buildable original source. Findings below are restricted to identifiable APIs, data models, manifests, and clear calls.

An older T-Mobile `5.35.0.82646` APK was also obtained as a historical reference. It is not used as evidence for the current app's authentication requirements.

## Confirmed on the user's phone

- Device model reported by ADB: Samsung `SM-S928B` (Galaxy S24 Ultra).
- Installed T-Mobile version: `10.8.0.1536924`, target SDK 35.
- Pulled installed base APK is byte-for-byte identical to the downloaded APK above.
- Default dialer remains `com.samsung.android.dialer`.
- `cmd phone has-carrier-privileges com.tmobile.vvm.application` returned `true`.
- The app has ordinary `READ_PHONE_STATE`, `INTERNET`, and `CHANGE_NETWORK_STATE` grants.
- The app opened to the populated inbox. A pull-to-refresh gesture returned to the inbox without a visible error. This is a UI observation; it does not independently prove fresh server retrieval or which authentication route was used.
- My VVM was not installed during testing. The user reports that uninstalling it was necessary for T-Mobile VVM to work. The cause is unconfirmed; shared provisioning or notification registration are hypotheses to investigate.

ADB screenshots and taps provided direct screen inspection. Phone captures and package summaries stay in an ignored local directory. No messages, greetings, PINs, forwarding settings, or default-dialer settings were changed during this investigation.

## Current protocol observations

### Authentication

Both apps contain cellular-IP and EAP-AKA implementations.

The current T-Mobile app exposes `AuthOptions.AuthType.IpAuth` and `EapAka`, with separate `SecurityBinding.authByIp` and `authByEapAka` implementations. Its SDK sends JSON requests to an entitlement endpoint's `/generic_devices` path. Identifiable method names include `3gppAuthentication`, `getMSISDN`, `manageServiceProvision`, `manageServiceInstance`, and `managePushToken`.

The SDK's IP implementation requests an Internet-capable cellular network through `ConnectivityManager`. My VVM's independent `IPAuthManagerImpl` similarly binds its requests to a mobile network. A Wi-Fi-only desktop request is therefore not an equivalent authentication test.

The AKA implementation checks `hasCarrierPrivileges()` and calls `TelephonyManager.getIccAuthentication(2, 129, challenge)`. In the inspected implementation it returns no challenge response when carrier privileges are absent. Renaming a package or declaring a permission would not grant the carrier authorization needed by this path.

The authentication exchange eventually produces a service-instance token (SIT). Mailbox requests carry an `Authorization: Bearer ...` value, the subscriber number, and client metadata. Server-side client allowlisting and independent-client acceptance have not yet been established. No extracted app keys or subscriber tokens have been included in this repository.

### Mailbox and audio

The default current mailbox endpoint is:

`https://cpaas-geo.t-mobile.com/v1/daasvmsvc`

The current T-Mobile `VMSvcAPI` describes operations for listing voicemails, retrieving an individual message, downloading audio payloads, reading quotas and profiles, updating read flags, and managing greetings. The list API supports offset/count and a delta-sync date. Audio types include AMR. This is an HTTPS client architecture; replacing the default phone app is unnecessary for this transport.

The entitlement endpoint default is `https://eas3.msg.t-mobile.com`. Defaults can be overridden through remote configuration, so they should not be treated as permanent infrastructure guarantees.

### Notifications

T-Mobile uses `VVMFirebaseMessagingService` and registers a push token as part of the authentication flow. Working manual mailbox access alone would not prove timely background notifications. Acceptance of a token from our own Firebase project, or an alternative supported notification mechanism, needs testing.

### Transcription

Mailbox models contain transcription and feature-entitlement fields. Those describe carrier-supplied transcription. An independent client could instead transcribe downloaded recordings locally, without relying on Mint selling or enabling that feature. Accuracy, latency, battery cost, audio decoding, and model licensing need implementation testing.

## Local evidence map

Decompiled inputs remain untracked under `research/analysis/`:

- `tmobile-10.8.0.1536924/resources/AndroidManifest.xml`
- `tmobile-10.8.0.1536924/resources/res/xml/remote_config_defaults.xml`
- `tmobile-10.8.0.1536924/sources/com/tmobile/visualvoicemail/auth/AuthOptions.java`
- `tmobile-10.8.0.1536924/sources/com/tmobile/visualvoicemail/auth/SecurityBindingImpl.java`
- `tmobile-10.8.0.1536924/sources/com/tmobile/android/sdk/security/auth/h.java` (IP flow)
- `tmobile-10.8.0.1536924/sources/com/tmobile/android/sdk/security/auth/f.java` (AKA flow)
- `tmobile-10.8.0.1536924/sources/com/tmobile/android/sdk/security/connectivity/c.java`
- `tmobile-10.8.0.1536924/sources/com/tmobile/visualvoicemail/api/HeaderUtil.java`
- `tmobile-10.8.0.1536924/sources/com/tmobile/visualvoicemail/api/VMSvcAPI.java`
- `tmobile-10.8.0.1536924/sources/com/tmobile/visualvoicemail/fcm/VVMFirebaseMessagingService.java`
- `myvvm-12.5.1.484513/sources/com/tmobile/visualvoicemail/auth/IPAuthManagerImpl.java`
- `myvvm-12.5.1.484513/sources/com/tmobile/visualvoicemail/utils/UtilityImpl.java`
- `tmobile-signature.txt`, `myvvm-signature.txt`, and the decompiler logs.

These paths are evidence locators for this local investigation, not published vendor source. Original code must be implemented separately.

## Next validation

1. Completed: an independently signed, minimal Android probe with Internet/network permissions, no default-dialer role, and no carrier privileges.
2. Completed: subscriber identification over an explicitly requested cellular network. Both response messages reported success (`1000`); the subscriber number and service fingerprint were nonempty. The Wi-Fi control returned `INVALID_IPAUTH` (`1005`). No provisioning or push-registration requests were sent.
3. Next: determine whether service-instance token retrieval can be performed without displacing the working official client's registration or changing service provisioning.
4. Then implement token refresh and read-only mailbox retrieval, followed by notifications and local transcription on separate feature branches. Test each stage before claiming a complete replacement works.
