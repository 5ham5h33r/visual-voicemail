# Local investigation tooling

Only original tooling is tracked here. Downloaded applications, tools, decompiler output, and private phone captures are ignored by Git.

- `fetch.py`: download a public input and save URL/size/hash provenance.
- `download_apks.py`: download exact base APKs from locally saved Aptoide catalog responses and validate catalog checksums.
- `analyze_apks.py`: verify signatures with Google's `apksigner`, then run JADX without launching the apps.
- `excerpt.py`: inspect numbered local decompiler output while suppressing large Kotlin metadata lines.
- `device_inspect.py`: capture the authorized device screen, summarize the two relevant packages, pull installed package APKs, and query the default-dialer holder.
- `github.py`: run the local GitHub CLI with the bundled Git executable and the CLI's standard credential storage. No access tokens are printed or saved by this script.

The scripts currently expect the locally downloaded tool layout under `research/tools/`; this is an investigation harness rather than a portable app build system. Use `--help` on the argument-taking scripts. Never commit `downloads/`, `tools/`, `analysis/`, or `device-private/`.

The findings and input hashes are in [the research report](../docs/carrier-research.md).
