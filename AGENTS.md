# Project workflow

- Keep `main` as the integration branch.
- Build each feature or investigation change on a descriptive `feature/` branch.
- Commit coherent changes with clear messages. Push the branch, open a pull request, run relevant validation, and merge through the pull request.
- After a completed pull request is merged, delete its remote branch and remove the fully merged local branch. Preserve branches with unmerged work. Keep GitHub's automatic branch deletion after merge enabled.
- Follow the user's authorization to create the GitHub repository and merge routine completed pull requests; do not ask again for each routine commit or merge.
- Never commit downloaded carrier APKs, decompiled proprietary sources, extracted credentials, signing keys, personal voicemail, raw/private phone screenshots, or device identifiers. User-requested documentation screenshots may be committed under `docs/screenshots/` only after personal details are removed and every image is reviewed.
- Keep original implementation separate from vendor decompilation. Document protocol observations and uncertainty in plain language.
- Tests against the user's phone must preserve voicemail, greetings, forwarding settings, and the user's default dialer unless the user specifically authorizes a change.
- Distinguish static code findings, live app observations, and independently reproduced behavior.
- Apply the shared `AppMotion` policy to new app controls: brief, interruptible press feedback, immediate actions, and support for both app Reduce motion and Android animation settings. Keep native switch/seek gestures and navigation behavior. See `docs/motion.md` for the Apple-guided interaction rules.
- Keep transcription choices in plain language with the model name available: Standard (Whisper Tiny), Enhanced (Whisper Base), and Highest quality (Whisper Large v3). The recording transcript and Settings use the same selection. Changing quality must preserve saved transcripts until the user requests an update.

- Follow the compact type/spacing hierarchy in `Ui` and `docs/compact-interface.md`. Prefer shorter helper text and fewer filled containers; retain readable font scaling and comfortable touch targets. Top-left back actions must use the shared Up behavior so individually opened child pages return to their parent.

- Inbox Select all applies only to current visible search/filter results. Bulk speech must preserve existing transcripts by default, serialize work, and report partial outcomes. Keep carrier recording deletion behind the existing reversible local marks policy.

- Keep the expanded inbox header and messages on one native scroll surface. Preserve scroll position and compact actions; respect Reduce motion. Avoid persistent explanatory footer text and scroll-driven header-height changes. See docs/inbox-header.md.
