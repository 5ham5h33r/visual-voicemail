# Independent cellular-IP probe results

Test date: October 26, 2026 (America/Los_Angeles).

The original `org.visualvoicemail.probe` Android app successfully completed the subscriber-identification part of T-Mobile's cellular-IP authentication on the user's Mint line. It used its own package name and independently generated debug signing key. It did not request phone-state permission, a default-dialer role, or carrier privileges.

Android independently reported that the probe had no carrier privileges. Samsung Phone remained the default dialer. The app bound the test connection to a cellular network while Wi-Fi remained available for wireless ADB.

| Observation | Wi-Fi control | Explicit cellular connection |
| --- | --- | --- |
| HTTPS request completed | Yes, HTTP 200 | Yes, HTTP 200 |
| `3gppAuthentication` response | `1005` (`INVALID_IPAUTH`) | `1000` (success) |
| `getMSISDN` response | `1005` | `1000` |
| Nonempty subscriber number returned | No | Yes |
| Nonempty service fingerprint returned | No | Yes |
| Probe had carrier privileges | No | No |
| Default dialer | Samsung Phone | Samsung Phone |

The sanitized machine-readable results are in [probe-results.json](probe-results.json). They contain only transport flags, application/dialer package names, status codes, response size, and presence flags. Subscriber numbers, device IDs, fingerprints, challenges, and tokens are excluded.

## What this establishes

The carrier accepts an ordinary, independently signed client's cellular-IP identification request on this device and line. The initial identification step does not inherently require the official app's signing key, carrier privileges, forwarding to another provider, or the default-dialer role. The test used an honest research app User-Agent rather than claiming to be the official application.

## What remains unproven

The probe does not obtain a mailbox token, list or download messages, renew credentials, or register notifications. A service fingerprint is an intermediate authentication result, not proof of complete mailbox access. The server could impose additional restrictions later in the exchange. Background reliability, dual-SIM behavior across devices, and long-term support also remain untested.

The user reported a conflict between My VVM and T-Mobile VVM. To preserve the working setup, these tests did not provision another voicemail client or replace the official app's push registration. The probe only sent `3gppAuthentication` and `getMSISDN` requests and did not play, mark, delete, or modify voicemail messages.

The next feature should investigate service-instance token retrieval and its effect on existing registration before adding mailbox retrieval or transcription.
