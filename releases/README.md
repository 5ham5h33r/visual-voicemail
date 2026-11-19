# Install the Android preview

[Download Voice Mail 0.34](https://github.com/5ham5h33r/visual-voicemail/releases/download/v0.34/visual-voice-mail-0.34.apk), or use the identical [APK stored in this repository](v0.34/visual-voice-mail-0.34.apk).

1. Open the downloaded APK on your Android phone. If Android asks, allow installation from the app you used to open it.
2. Open Voice Mail and connect using Mint cellular data for initial setup. The app uses the existing carrier mailbox and keeps your chosen dialer; call forwarding is not required.
3. Choose whether to enable contacts, notifications, and an optional on-device transcription model. Models download separately from the APK.

Requires Android 8.0+ and an arm64-v8a phone. The APK also includes x86_64 support for emulators. Carrier testing has focused on Mint and the S24 Ultra; this remains a development preview. Background checks can be delayed by Android, independent carrier push is unverified, and live greeting replacement has not been validated.

The APK is signed and non-debuggable. It is the same v0.34 binary published in GitHub Releases, with SHA-256:

```text
b5d6861e47ca379f47a553875fa55faf7159ea3fa0f6b2a06405c5825ac6a9d7
```

The version folder includes `SHA256SUMS` and `SIGNING-CERTIFICATE.txt`. To verify on Windows, run `Get-FileHash .\visual-voice-mail-0.34.apk -Algorithm SHA256`; on Linux, run `sha256sum --check SHA256SUMS` from `releases/v0.34/`.

Install updates over the existing app to keep app-private recordings and transcripts. Uninstalling removes those files. See [signing and upgrade details](../docs/releases.md), [source build instructions](../app/README.md), and the [dependency licenses](../docs/licenses/). Private signing keys and personal mailbox data are not distributed.
