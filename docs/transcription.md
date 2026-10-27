# On-device transcription in version 0.13

Opening a recording shows its player and transcript together. A regular row tap opens it paused; the inbox play button opens it playing. The English model is an optional, one-time download of 104,271,045 bytes (about 104 MB). Audio playback remains available during setup. The model and runtime are open source; no API key or paid transcription service is required.

## Recognition and timing

Android MediaExtractor/MediaCodec decodes the carrier recording locally. The app converts PCM to 16 kHz mono. Silero VAD locates speech, and Whisper tiny.en transcribes each speech interval through sherpa-onnx 2.0.1. Speech intervals are limited to about 12 seconds, with short audio padding. Their offsets come from the original audio samples, rather than text length or evenly spaced guesses.

Timing is approximate at the phrase level. A wrapped phrase shares its phrase anchor; there is no word-level alignment claim. Recognition can get names, accents, numbers, and noisy speech wrong. The player remains available to verify the audio. English is the initial supported transcription language.

Tapping a phrase seeks and plays. Repeated taps retain the latest requested offset, and the current audio phrase has a separate selected state. Playback uses one player, audio focus, speed/output preferences, and pauses when the detail screen loses focus. Bad timestamps remain readable without invented playback positions.

## Search and storage

Find in transcript matches literal words or phrases without case sensitivity, underlines every match, displays a count, and provides previous/next controls. Matches can span two phrase sections. Navigating matches does not play audio. Inbox search also includes transcripts already saved on the phone and shows an excerpt. It does not trigger downloads or recognition simply because someone searches.

Saved transcripts use the existing AES-GCM Android Keystore storage, one hashed recording ID per file. Query text, recognized text, and audio are not sent to a transcription server or written to diagnostics. Downloads only fetch public model files; the carrier still serves the original voicemail. The process-scoped worker survives screen recreation, serializes recognition, exposes progress/cancellation, and keeps Activity instances out of background work. Process death may interrupt work; reopening can retry.

Settings provides Transcribe on open, Clear saved transcripts, Remove speech model, and third-party notices. Clearing transcripts/model does not delete voicemail or exported audio. Clearing playback cache retains transcripts. Model installation uses exact size and SHA-256 verification, temporary files, and a completion marker. Completed files can be reused after an interrupted download. The runtime AAR and Kotlin JAR are also checksum-pinned at build time.

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
