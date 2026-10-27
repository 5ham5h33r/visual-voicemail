# Multilingual transcripts and English translation

Version 0.25 adds Recording language and Transcript output to Settings and the transcription-quality page. The recording's Transcript options menu also offers language selection, Find, and original/English generation or updates.

The bundled Tiny and Base manifests are English-only (`tiny.en`, `base.en`). Full Whisper Large v3 supports automatic language detection, manual language selection, and speech-to-English translation. Choosing a multilingual task explains the 1.78 GB download, memory requirements, and longer processing time before switching to Large v3. Model downloading remains an explicit action. Automatic detection supports the model's languages; the manual picker offers common languages.

Original transcripts and English translations use separate encrypted files. Translation preserves an existing original, generating one first if missing. The recording shows Original/English controls when a translation is available or requested. Inbox search matches both outputs and labels matching translated previews as English. Opening such a result selects English and retains the search query. Find operates within the currently displayed output.

Phrase anchors come from local voice-activity ranges. Tapping translated text plays the corresponding source-audio phrase; translated words do not have exact word-level timing. Language selection records the requested mode. Detected language is saved only if the recognizer reports it; selecting automatic detection is not presented as a confirmed language label.

Queued work snapshots model, language, and output. Generate missing keeps existing requested outputs. Updating English text preserves its original transcript. Cancellation and failures retain already saved text. Clearing transcripts removes both output types; removing a saved voicemail copy also removes its local original and translation. Version 1 original caches remain readable.

All speech decoding and translation run on the phone. Audio/text are not uploaded to a translation server. Models must be downloaded before offline use. The current decode limit is ten minutes per recording; Large v3 requires an 8 GB or larger phone and sufficient free memory. Keep the app open for long jobs.

## Validation

The S24 Ultra passed 34 engine checks with public Spanish and French WAVs from the [sherpa-onnx language-identification fixtures](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/spoken-language-identification-test-wavs.tar.bz2). Spanish used automatic detection; French used manual selection. Both produced original-language text and separate English output. A concatenated Spanish/French fixture with deterministic low-level noise produced both source-language phrases. Silence produced empty outputs; cancellation preserved old text. The queue generated one missing translation and retained another. Cache migration, anchors, and unsupported stored-language fallback were checked. Public fixtures and recognized output logs are excluded from commits.

These are functional checks, not a language-quality benchmark. One Spanish idiom was translated awkwardly and literally. Recognition, automatic detection, mixed-language handling, and translation can be wrong, particularly for short/noisy speech. Check important content against the audio. Local cached-fixture execution was verified; no phone-wide network settings were changed to simulate airplane mode.

The implementation uses the engine's [language/task configuration](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/offline-whisper-model-config.cc) and its existing [Whisper implementation](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/offline-recognizer-whisper-impl.h). English-only models support transcription; the multilingual model supports transcription and translation.
