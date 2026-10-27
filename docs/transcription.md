# On-device transcription in version 0.16

Opening a recording shows its player and transcript together. A regular row tap opens it paused; the inbox play button opens it playing. English transcription offers three optional model downloads: Standard, 104,271,045 bytes (about 104 MB), Enhanced, 161,269,920 bytes (about 161 MB), and Highest quality, 1,776,397,772 bytes (about 1.78 GB). Audio playback remains available during setup. The model and runtime are open source; no API key or paid transcription service is required.

## Transcription quality

A control directly below the Transcript heading shows **Standard (Whisper Tiny)** **Enhanced (Whisper Base)**, or **Highest (Large v3)**. Settings → Transcription → Transcription quality opens the same page. Each choice explains the speed/storage tradeoff, model name, size, and whether it is already downloaded.

| Choice | Model | Download | Intended tradeoff |
|---|---|---|---|
| Standard | Whisper tiny.en | About 104 MB | Faster results and less storage; the default |
| Enhanced | Whisper base.en | About 161 MB | Focus on accuracy; a larger model that may recognize difficult speech better |
| Highest quality | Full Whisper large-v3, int8 | About 1.78 GB | Much slower; requires more memory and may improve difficult speech |

All run locally. Tiny.en and Base.en are English-only; Large v3 is multilingual upstream, but this app currently selects English transcription. Model size involves speed/accuracy tradeoffs; results vary by recording and hardware, as explained in [Whisper's model documentation](https://github.com/openai/whisper#available-models-and-languages). A larger model is not guaranteed to be more accurate on every message.

Changing quality preserves existing transcripts and tags future results with the model actually used. The recording page identifies the saved quality and offers **Update transcript** when a different choice is selected. Regeneration keeps the previous transcript until a new result is successfully saved. Earlier transcripts are treated as Standard without rewriting them.

The selected quality is captured when a job starts. Changing quality is blocked until an active download or recognition finishes or is cancelled. Each model has its own checksum-pinned files and completion marker; the existing Standard installation continues to work. Cancelling setup does not start recognition with a different choice.

## Recognition and timing

Android MediaExtractor/MediaCodec decodes the carrier recording locally. The app converts PCM to 16 kHz mono. Silero VAD locates speech, and the selected Whisper model, configured for English, transcribes each speech interval through sherpa-onnx 2.0.1. Speech intervals are limited to about 12 seconds, with short audio padding. Their offsets come from the original audio samples, rather than text length or evenly spaced guesses.

Timing is approximate at the phrase level. A wrapped phrase shares its phrase anchor; there is no word-level alignment claim. Recognition can get names, accents, numbers, and noisy speech wrong. The player remains available to verify the audio. English is the initial supported transcription language.

Tapping a phrase seeks and plays. Repeated taps retain the latest requested offset, and the current audio phrase has a separate selected state. Playback uses one player, audio focus, speed/output preferences, and pauses when the detail screen loses focus. Bad timestamps remain readable without invented playback positions.

## Search and storage

Find in transcript matches literal words or phrases without case sensitivity, underlines every match, displays a count, and provides previous/next controls. Matches can span two phrase sections. Navigating matches does not play audio. Inbox search also includes transcripts already saved on the phone and shows an excerpt. It does not trigger downloads or recognition simply because someone searches.

Saved transcripts use the existing AES-GCM Android Keystore storage, one hashed recording ID per file. Query text, recognized text, and audio are not sent to a transcription server or written to diagnostics. Downloads only fetch public model files; the carrier still serves the original voicemail. The process-scoped worker survives screen recreation, serializes recognition, exposes progress/cancellation, and keeps Activity instances out of background work. Process death may interrupt work; reopening can retry.

Settings provides Transcription quality, Transcribe on open, Clear saved transcripts, and third-party notices. The quality page downloads or removes only the selected model, retaining other downloaded choices. Clearing transcripts/model does not delete voicemail or exported audio. Clearing playback cache retains transcripts. Model installation uses exact size and SHA-256 verification, temporary files, and a completion marker. Completed files can be reused after an interrupted download. The runtime AAR and Kotlin JAR are also checksum-pinned at build time.

## Upstream sources and licenses

- [sherpa-onnx 2.0.1](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1), Apache 2.0; its official Android AAR includes ONNX Runtime (MIT).
- [Whisper](https://github.com/openai/whisper), MIT; [converted tiny.en models](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/tree/d026532c022fa99fd789d6b32446a1df7b6bfc43) from the sherpa-onnx maintainer.
- [Silero VAD](https://github.com/snakers4/silero-vad), MIT.
- [Kotlin](https://github.com/JetBrains/kotlin/tree/v2.2.0), Apache 2.0.

Full notices accompany the APK and are also stored in [licenses](licenses/).

## Device results and limits

On the S24 Ultra, a cached real 18-second carrier AMR voicemail produced two encrypted phrase sections in 2,266 ms after model setup. Both anchors were ordered and within the recording duration. This is one recording, not an accuracy or performance benchmark.

A separate public English speech fixture with two seconds of prepended silence transcribed in 1,508 ms. Expected words were present, the speech anchor retained the leading silence (between 1.5 and 3 seconds), and all phrase offsets were within the audio. The temporary fixture and its transcript were removed afterward.

Fourteen device checks cover opening paused, actual playback from a phrase, seek position, search and next-match without playback, rapid phrase selection, pausing on navigation, and preserving carrier read status. Host checks cover literal/Unicode/repeated/cross-section phrase matching. Native libraries have 16 KB ELF load alignment for arm64-v8a and x86_64. Model download, recognition, and playback were independently reproduced in this app with the vendor VVM app disabled.

Further coverage is needed for long/noisy recordings, diverse accents, real timing accuracy across more speech, low memory/process termination, first-install denied permissions, non-English recordings, and other phones. No instant carrier push or permanent voicemail deletion is introduced by this change.

## Version 0.14 device checks

The Enhanced model downloaded and passed SHA-256 verification on the S24 Ultra. A public speech fixture with two seconds of added leading silence produced the expected words, valid phrase anchors, an Enhanced quality tag, and a base.en engine tag in 2,020 ms. Selection was locked during the download. This is a functional check, not an accuracy comparison or a performance benchmark. The fixture and its temporary transcript were removed afterward.

Twenty-four quality/motion device checks cover the control on the transcript page, the same choices reached from Settings, persistent selection, keeping an existing transcript, immediate tap actions, release/cancel feedback, and reduced-motion behavior. See [device checks](../app/device-tests/README.md). The user's original quality and motion preferences are restored after testing. The 14 recording-flow and 32 onboarding-flow checks also passed on version 0.14.

Version 0.15 compacts the transcript header to Standard (Tiny) / Enhanced (Base), retaining full Whisper names in the quality picker and accessibility label. The same model selection and saved-transcript policy apply.


## Full Large v3 support in version 0.16

Highest quality uses the full Large v3 model, not Turbo or a distilled variant. Its encoder and decoder are self-contained int8 ONNX files; no external float32 weights are downloaded. The catalog pins [upstream revision 2a650709](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3/tree/2a6507094dd6020d939d78e3f1834a1d06267fca), exact byte sizes, and SHA-256 hashes, including Large v3's distinct token table. sherpa-onnx reads the model's mel dimension from its metadata when creating Whisper streams (128 for Large v3).

Setup reserves about 3.6 GB of free storage. Before recognition, the app checks for at least 8 GB total RAM, 2 GiB currently available RAM, and no Android low-memory condition. These checks reduce avoidable failures; they cannot guarantee that Android will keep a large native process alive. Keep the app open while transcribing. Native recognition is not interruptible in the middle of a phrase; cancellation takes effect at the next boundary. Background process death requires retrying. Completed model files can be reused, but an interrupted partial file downloads again.

No existing transcript is replaced by selecting or downloading Large. Standard remains the default. Removal affects only the selected model.

The full Large v3 download passed size/hash verification on the S24 Ultra. The same public speech fixture produced the expected words, a Large v3 engine tag, preserved leading silence, and valid phrase anchors in 18,554 ms. The selected quality was locked during download and restored after the check; the temporary audio/transcript were removed. This one short fixture is a functional check, not an accuracy benchmark or a promise of per-message speed. Both Standard and Enhanced remain downloaded, and Standard remains selected.

Twenty-eight quality/motion device checks passed, including selection of the third model, exclusive selected state, and retaining an existing transcript.

Version 0.17 adds a process-scoped batch queue with Generate missing, explicit Update all, per-recording saves, cancellation, and counts for partial failures. See [bulk actions](bulk-actions.md).
