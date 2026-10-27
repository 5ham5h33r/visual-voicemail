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
