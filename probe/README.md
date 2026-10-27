# Read-only cellular-IP probe

This is an original diagnostic app, not a finished voicemail client. It uses its own package (`org.visualvoicemail.probe`) and local debug signing key. It requests only Internet/network permissions and never asks for a default-dialer role or phone-state permission.

The two buttons compare the active default network with an explicitly requested cellular network. Each sends a subscriber-identification request to T-Mobile's entitlement endpoint. It does not provision service, request a mailbox token, register push notifications, access recordings, or change mailbox settings.

The request identifies this research app honestly. No official signing key, proprietary SDK, extracted access token, or T-Mobile app identity is reused. The request's device identifier is derived from this app's Android ID and the default data subscription ID. The request goes directly from the phone to T-Mobile over verified HTTPS; it is not sent to a project server.

The response is processed in memory. Only transport information, response codes, and flags indicating nonempty fields are displayed and saved in the app's private `files/result.json`. Phone numbers, service fingerprints, challenges, and tokens are omitted from that file. Values must not be printed or added to telemetry in future changes.

## Build

Prerequisites: Python 3.9+ and Java 17+ on Windows x64 or Linux x64.

```sh
python probe/bootstrap.py
python probe/build.py
```

The bootstrap script verifies fixed SHA-256 hashes for the Android SDK platform, build-tools, and Eclipse Java compiler. The build compiles Java, converts it to DEX, packages a manifest, signs with a local debug key, and verifies the APK signature. Output: `probe/build/vvm-research-probe.apk`.

The debug key is generated once in ignored `probe/local/`. It is disposable, uses the conventional Android debug password, and is not a production signing identity. CI generates its own disposable key; a CI-built APK therefore cannot update a locally signed installation without uninstalling the diagnostic app first.

## Phone validation

Use ADB only with a device the owner has authorized. Install the debug APK and open `org.visualvoicemail.probe/.MainActivity`. Keep the working carrier voicemail app installed. Run the default-network control and then the cellular test. The data SIM must be the Mint subscription for a Mint cellular test.

To collect the sanitized result from this debuggable app:

```sh
adb -s DEVICE shell run-as org.visualvoicemail.probe cat files/result.json
```

A successful identification response does not establish mailbox-token issuance, token renewal, message download, or push delivery. See the [research report](../docs/carrier-research.md).
