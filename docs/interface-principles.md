# Interface direction

The app uses native Android interaction with grouped settings and restrained emphasis. The design references are [Apple's button guidance](https://developer.apple.com/design/human-interface-guidelines/buttons), [Apple's motion guidance](https://developer.apple.com/design/human-interface-guidelines/motion), and [Samsung's One UI guide](https://design.samsung.com/global/contents/one-ui/download/oneui_design_guide_eng.pdf). Their principles guide hierarchy and feedback; this project does not reproduce either platform's proprietary appearance.

## Define the control before styling it

| Purpose | Treatment |
| --- | --- |
| Open or change a setting | Grouped row with a clear label, selected value, and disclosure chevron. |
| Perform the main task | One strong filled action in its context, such as initial transcription/model setup. |
| Perform a secondary task | Compact soft filled button, readable action label, 48 dp touch target. No decorative accent outline. |
| Select one option | Native radio state in a compact list, with quiet selection emphasis. |
| Select multiple messages | Native rounded checkbox state; stable card height and aligned text. |
| Explain state | Plain secondary text; no button-like chrome or repeated instruction. |
| Offer a rare/destructive operation | Contextual menu or explicit confirmation, with its consequences clear. |

## Transcription page hierarchy

Use one compact page heading. Group recording language and transcript output together. Present quality options as one coherent list; keep technical model names, language capability, and download sizes available without repeating paragraphs. Detailed model explanations belong in About. Model state drives the primary action: Download, Pause, Resume/Retry, or Ready. Existing text stays intact until an explicit update.

The recording page keeps the transcript and playback primary. Its quality selector is a compact, recognizable control. Update transcript uses the same soft secondary-button language as other body actions. Normal body text is never the sole visual indication of a standalone action. Toolbar icons retain familiar native placement and feedback.

## Spacing and motion

Use consistent type levels and gaps instead of adding a container around each item. Keep the main content close to its controls. Preserve readable scaling and 48 dp hit areas even when the visible control is smaller. Light, Dark, and AMOLED use the same hierarchy and semantic color roles.

Animate a state change as one coordinated movement. Page-level layout owns vertical flow; row animations stay inside their own cards. Selection must not change card height or freeze the inbox's layout. Reversals start from the current pose, state changes take effect immediately, and Reduce motion settles the same final state. Swiping, typing, and ordinary playback updates keep their own responsive behavior.

Review missing-model, downloading, ready, changed-quality/language, empty, error, large-text, and all-theme states before final capture. The product video uses the settled app interface and clearly labelled synthetic data.
