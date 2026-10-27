# Independent Mint mailbox access

Validated October 26, 2026 (America/Los_Angeles), on the user's Samsung Galaxy S24 Ultra.

The original `org.visualvoicemail.app` application completed cellular identification, established a voicemail service instance, obtained its own service-instance token, and loaded the user's real mailbox. The list request returned HTTP 200 with one message. The app has no carrier privileges, requests no phone-state permission or dialer role, and Samsung Phone remains the default dialer.

T-Mobile VVM was temporarily disabled for the final test, preserving its installed app data. My VVM remained uninstalled, as the user reported that it conflicted with T-Mobile VVM. No recordings, greetings, PINs, forwarding settings, or default-dialer settings were changed. The new client did not register a push token or mark any message as read.

## Protocol findings

- Identification uses an explicitly acquired cellular Internet connection and a device identifier scoped to our independently signed app.
- `manageService` operation 0 establishes the service instance. Operation 5 requests its mailbox token. The instance request uses only the returned instance identifier and subscriber fields, plus the service name.
- The service-instance token is UTF-8/base64 encoded before use in the mailbox bearer header.
- The subscriber mailbox header is a `tel:+` URI.
- The current mailbox API requires the public client profile `VVMOTT` and profile version `10`. Sending this project's package/version as those protocol fields produced HTTP 401; the carrier profile fields produced HTTP 200. The HTTP User-Agent continues to identify this project honestly.
- The official app's signing key and SIM carrier privileges were not reused. All credentials were issued directly to the original client on the user's authorized line.

Only success codes, HTTP status, field-presence flags, and the message count were retained as development diagnostics. Subscriber numbers, device identifiers, instance identifiers, fingerprints, tokens, signed URLs, and message metadata are excluded from this report and Git.

## Limits of this milestone

The current token is held in memory and renewed on a mailbox HTTP 401. Persisted credential protection and expiry-based renewal are still to be implemented. The initial client lists the first 100 messages; pagination and offline metadata storage are not yet complete. Audio download code exists, but playback has not yet been validated. No background notification delivery is claimed. Transcription is deliberately deferred until the user reviews a working playback build.
