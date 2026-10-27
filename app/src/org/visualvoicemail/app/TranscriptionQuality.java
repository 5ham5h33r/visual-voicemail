package org.visualvoicemail.app;

/** User-facing choices and immutable, verified upstream model manifests. */
public final class TranscriptionQuality {
    public static final TranscriptionQuality STANDARD =
            new TranscriptionQuality(
                    "standard",
                    "Standard",
                    "Fast results, less storage",
                    "Fast results and a smaller download. Best for clear recordings.",
                    "tiny.en",
                    "d026532c022fa99fd789d6b32446a1df7b6bfc43",
                    "",
                    12937772,
                    89853865,
                    "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6",
                    "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527");
    public static final TranscriptionQuality ENHANCED =
            new TranscriptionQuality(
                    "enhanced",
                    "Enhanced",
                    "Accuracy over speed",
                    "May recognize difficult speech better. Uses more storage and takes longer.",
                    "base.en",
                    "59eea950fc76df2453efb57e6c0fd334548e8ffe",
                    "enhanced",
                    29120534,
                    130669978,
                    "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823",
                    "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41");
    public final String key, title, summary, description, prefix, revision, directory;
    private final long encoderSize, decoderSize;
    private final String encoderHash, decoderHash;

    private TranscriptionQuality(
            String key,
            String title,
            String summary,
            String description,
            String prefix,
            String revision,
            String directory,
            long encoderSize,
            long decoderSize,
            String encoderHash,
            String decoderHash) {
        this.key = key;
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.prefix = prefix;
        this.revision = revision;
        this.directory = directory;
        this.encoderSize = encoderSize;
        this.decoderSize = decoderSize;
        this.encoderHash = encoderHash;
        this.decoderHash = decoderHash;
    }

    public static TranscriptionQuality forKey(String key) {
        return ENHANCED.key.equals(key) ? ENHANCED : STANDARD;
    }

    public String[] names() {
        return new String[] {
            prefix + "-encoder.int8.onnx",
            prefix + "-decoder.int8.onnx",
            prefix + "-tokens.txt",
            "silero_vad.onnx"
        };
    }

    public long[] sizes() {
        return new long[] {encoderSize, decoderSize, 835554, 643854};
    }

    public String[] hashes() {
        return new String[] {
            encoderHash,
            decoderHash,
            "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930",
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6"
        };
    }

    public long bytes() {
        return encoderSize + decoderSize + 835554 + 643854;
    }

    public String sizeLabel() {
        return Math.round(bytes() / 1000000d) + " MB";
    }

    public String compactLabel() {
        return title + " (" + (this == STANDARD ? "Tiny" : "Base") + ")";
    }

    public String label() {
        return title + " (Whisper " + (this == STANDARD ? "Tiny" : "Base") + ")";
    }

    public String url(int index) {
        return index == 3
                ? "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
                : "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-"
                        + prefix
                        + "/resolve/"
                        + revision
                        + "/"
                        + names()[index];
    }
}
