# Voice Mail / Hyperframes preview

A separate 22-second, 1080x1350, 60 fps product film made with the brag skill and Hyperframes 0.9.26. The previous 32-second film in media/promo/exports is preserved.

- `brag.mp4`: finished video with its selected poster baked into frame 0.
- `brag.jpg`: thumbnail selected from the settled native phrase-playback scene.
- `share-copy.txt`: a short caption; no public posting has occurred.
- `brag-plan.md` and `composition-brief.md`: product claims, storyboard, and visual/audio direction.
- `composition/`: editable HTML scenes, pinned Node dependencies, and reviewed public-sample native UI assets.
- `verification.json`: compact validation and encoded-file evidence.

The footage contains synthetic sample transcripts and no personal caller, number, mailbox data, or physical-phone capture. Native playback runs at its original speed. Hyperframes seeks each animation frame independently; offline rendering does not depend on realtime emulator speed. The existing footage came from the Generic rectangular emulator with the S24 logical viewport and RTX 5070 Ti host renderer. This run does not connect to any Android device.

## Preview and render

From `composition/`, run `npm ci`, then `npx hyperframes preview --background` to edit locally. Run `npx hyperframes check --json` before rendering. The final command is `npx hyperframes render --quality delivery --fps 60 --workers 2 --no-best-effort --output ../brag.mp4`.

Roboto uses Hyperframes' font resolution/cache. GSAP 3.15.0 is copied locally from the pinned package. Scene files use paused timelines and composition-local native-media timing. The nonzero nested-video timing warning refers to the deliberately local 4-second start in the 0-second flow scene; no legacy global timestamp is involved.

The original native crops are committed for local preview and render. `prepare.py` regenerates them from the ignored original public-sample capture files when those are available; it also builds the composition files. The script needs FFmpeg, NumPy's Python environment, installed Node dependencies, the locally downloaded version-matched Hyperframes audio-data helper under research/tools, and the brag bundle for its CC0 accent. After rendering, run `finalize.py` from the project root to select the 5.5-second poster, bake frame 0, and write verification. Its full check report must be available as the local check.json file.

## Soundtrack rights

Reuse the verified source and rights record in [MUSIC-LICENSE.md](../media/promo/MUSIC-LICENSE.md). The synchronized excerpt is seconds 12-34, normalized near -19 LUFS, with a 0.35-second entry fade and 1.2-second exit fade in the composition. No loop or public credit is required. The standalone track/excerpt and audio analysis remain ignored, so the composition needs that local excerpt to render with music. The incorporated MP4 soundtrack retains the provider's terms; the app's MIT license does not replace them.

The two quiet warm accents come from Kenney's CC0 impact collection bundled with brag. The title reveal adapts the editable Hyperframes `soft-blur-in` registry component. GSAP retains its vendor copyright header and standard license; the repository's MIT license does not supersede third-party terms. Original Android app code and UI behavior are unchanged.

## Timing decisions

Hyperframes detected a 105.1 BPM grid with 36 beats in the excerpt. The UI demonstration and text-reading holds retain their natural timing rather than forcing every action to a beat. The only audio-reactive visual is a small 0.96-1.00 opacity modulation of the ending brand icon from extracted bass data; readable text stays still. The initial hook deliberately holds after its short reveal. No artificial motion interpolation or generated text frames are used.
