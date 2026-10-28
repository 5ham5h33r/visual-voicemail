# Calling and sharing from a recording

Version 0.25 places a small handset beside caller details and a Share icon in the toolbar. The handset opens the user's phone app with the callback number ready to dial using ACTION_DIAL. It does not place a call automatically and needs no CALL_PHONE permission. Missing/anonymous numbers and service-code strings do not produce a callable action.

Share opens two matching menu choices: Transcript and Voice recording. If both original text and an English translation exist, Transcript offers a second choice between them. Text is shared with a plain-text heading and phrase timestamps. Voice recording prepares a temporary AMR/AMR-WB copy, then opens Android's standard share sheet. The user chooses the destination. Preparing or cancelling a share never removes saved originals or carrier messages.

Audio uses an unexported native ContentProvider, random temporary file identifiers, exact read-only URI grants, and ClipData. Only prepared files in the private sharing cache are exposed. The provider rejects other paths, traversal, write modes, and expired copies. Shared copies expire after 24 hours and are cleaned during subsequent preparation; Android may clear cache sooner. Once a recipient saves or forwards content, that recipient controls its copy. The provider exposes no inbox, credentials, transcripts, or permanent recording directories.

The recording screen uses 20 dp glyphs inside 48 dp touch areas, a 48 dp central Play control, equal top/bottom padding around the player control row, and equal card padding. Standard-size layouts combine speed, skip, Play, and output into one row. Larger fonts/narrower configurations keep extra controls on a separate row. Transcript options gather Find, language, translation, and updates in one menu. Routine Paused/output labels and repeated transcript instructions are hidden; loading and errors remain visible. Model explanations use separate paragraphs per model.

## Validation

Twenty sharing checks exercised synthetic public data: metadata/MIME, content bytes, read-only mode, traversal rejection, a separate shell UID denied before permission, successful reading with an explicit read grant, denial after revocation, expiry, text headings/timestamps, and share-intent flags. Recording-flow checks intercept the dialer and share-sheet launches, verify menu choices, output switching and search, and assert vertical padding and horizontal Play centering. Tests do not send content to a recipient or place phone calls. Reviewed screenshots show Light, Dark, and AMOLED with caller details removed and sample text substituted before drawing.

Android documents [temporary URI access](https://developer.android.com/training/secure-file-sharing/share-file) and the [standard share-sheet flow](https://developer.android.com/develop/ui/compose/sharing/send).

Version 0.27 gives the compact speed control the same 48 dp width as the output control, preserving the Play center at 0.75×, 1×, 1.25×, 1.5×, and 2× without truncating labels. The player card has 14 dp top/bottom padding; its control row adds 8 dp above and below the 48 dp Play target. Cached audio is resolved before entering the carrier network lock. Forty-seven recording checks include every speed label and preparation while another thread holds that lock, alongside calling, sharing, phrase seeking, and transcript search.
