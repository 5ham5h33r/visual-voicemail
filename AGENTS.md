# Project workflow

- Keep `main` as the integration branch.
- Build each feature or investigation change on a descriptive `feature/` branch.
- Commit coherent changes with clear messages. Push the branch, open a pull request, run relevant validation, and merge through the pull request.
- Follow the user's authorization to create the GitHub repository and merge routine completed pull requests; do not ask again for each routine commit or merge.
- Never commit downloaded carrier APKs, decompiled proprietary sources, extracted credentials, signing keys, personal voicemail, phone screenshots, or device identifiers.
- Keep original implementation separate from vendor decompilation. Document protocol observations and uncertainty in plain language.
- Tests against the user's phone must preserve voicemail, greetings, forwarding settings, and the user's default dialer unless the user specifically authorizes a change.
- Distinguish static code findings, live app observations, and independently reproduced behavior.
