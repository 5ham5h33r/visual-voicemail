# Visual Voice Mail

An investigation and prototype for a free, open-source Android visual voicemail client for Mint Mobile, starting with the Samsung Galaxy S24 Ultra.

The intended app uses the existing carrier mailbox, works alongside the user's chosen dialer, and adds a clear inbox and on-device transcription.

An independently signed Android probe has reproduced cellular-IP subscriber identification on Mint without carrier privileges or a default-dialer role. A working third-party mailbox client has not yet been demonstrated.

- [Carrier protocol and device findings](docs/carrier-research.md)
- [Independent probe results and limits](docs/independent-probe.md)
- [Build the diagnostic Android app](probe/README.md)

## Development

Use `feature/` feature branches, commit each coherent change, and merge changes to `main` through a reviewed pull request with relevant validation.

Downloaded APKs, decompiled proprietary application code, local tools, signing keys, and private device captures are excluded from version control. Only original implementation, research tooling, and findings belong in this repository.
