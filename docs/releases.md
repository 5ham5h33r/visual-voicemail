# Signed APK releases

[Download version 0.32](https://github.com/5ham5h33r/visual-voicemail/releases/tag/v0.32) from GitHub Releases. This is a preview release with the version 0.31 feature set and release packaging. The repository is currently private, so access to its release assets requires repository access. Publishing an APK does not change repository visibility or establish Play Store/F-Droid availability.

The universal APK supports Android 8.0+ (API 26), arm64-v8a phones, and x86_64 emulators. It is signed and non-debuggable. Speech models download separately after explicit selection. `SHA256SUMS` verifies the downloaded APK bytes; `SIGNING-CERTIFICATE.txt` records public signing certificate information. Neither contains private keys.

## Build types

The default development build remains available for local instrumentation:

```sh
python probe/bootstrap.py
python probe/build.py --module app
```

To create a release signing identity for your own distribution, run once:

```sh
python research/prepare_release_signing.py
python probe/build.py --module app --release
```

The signed output is `app/build/release/visual-voice-mail-<version>.apk`. Release packaging sets `android:debuggable=false` in a generated manifest, compiles DEX in release mode, and has apksigner reject debuggable packages. It does not change the development manifest or use obfuscation. Package/version, resources, and native libraries remain shared between variants.

The helper creates a dedicated RSA 3072-bit key with 10,000-day certificate validity and a random password file. References are stored in `app/local/release-signing.json`. Future releases reuse that configuration. It refuses to overwrite existing signing files or silently generate a replacement identity. The builder also accepts `--signing-config <local-json>` for an existing signing setup.

Keep an encrypted backup of **all of `app/local`**, including the keystores, password file, configuration, and rotation lineage when present. Losing the signing identity can prevent updates to installed copies. These files are ignored by Git and are never release assets or CI inputs. A fresh clone's independently generated key cannot update APKs signed by this project's release identity.

## Upgrading the existing development installation

This checkout's first release was prepared with `--migrate-debug`. It creates a proof-of-rotation from the original local development key to the dedicated release key. APK signing v3.1 uses the release key on Android 13+ and the original signer on older supported Android versions. The existing local development key must remain available for those older-platform signatures. Rollback to an APK signed only by the old key is not enabled on devices that have adopted the new signer.

This lineage applies to APKs signed by this checkout's original key. It does not authorize updates from unrelated CI/debug signing keys. Install the release as an update; uninstalling removes app-private recordings and transcripts. Future development builds signed only with the old key may be rejected after installing the rotated release. Use the published release or retain a separate development emulator rather than uninstalling a personal mailbox to switch builds.

The S24-sized API 36 emulator passed 15 pre-upgrade and 20 post-upgrade assertions. An in-place update from development 0.31 to release 0.32 preserved the hashed private files/preferences and decrypted the existing sample inbox with Android Keystore. The installed app reported non-debuggable state and the expected signing history. The installed release then passed 47 recording/playback assertions and 11 actual Tiny/VAD inference assertions using public audio. These checks use only an unauthenticated sample emulator; the personal phone was not migrated by the release task.

## CI and publishing

CI builds the normal app/probe, checks host behavior/contrast, compiles instrumentation, and builds `--release --unsigned` to validate release packaging without any private signing material. Unsigned output is not installable or a published release asset. CI's default development APKs use ephemeral signing identities and are separate from this signed release.

For an owner release, build from the merged release commit, verify the signed APK, exercise an in-place emulator upgrade and playback, then publish the APK plus its SHA-256 checksum and public certificate report against the matching Git tag. Keep preview status until the remaining [UX/reliability validation](ux-final-review.md) is complete. Instant independent carrier push, real arrival/idle/reboot timing, and live greeting replacement remain unverified.

Signing follows the official [apksigner tool](https://developer.android.com/tools/apksigner) and [v3.1 rotation guidance](https://source.android.com/docs/security/features/apksigning/v3-1).
