package org.visualvoicemail.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.util.concurrent.*;

/** Personal greeting: read current, record or import locally, preview, and explicitly save. */
public final class GreetingActivity extends MotionActivity {
    private static final int MICROPHONE = 51, PICK_AUDIO = 52;
    private Ui ui;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private JSONObject current;
    private File draft, recordingFile;
    private String draftHash = "", previewedHash = "";
    private int draftDuration;
    private boolean working, checking, destroyed, visible, previewingDraft;
    private long started, jobVersion = GreetingJobs.version;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private TextView currentState, draftState, status;
    private Button record, choose, preview, save, discard;
    private Ui.Icon currentPlay, refresh;
    private AlertDialog confirmation;
    private final Runnable tick =
            new Runnable() {
                public void run() {
                    if (destroyed) return;
                    update();
                    if (visible) handler.postDelayed(this, 250);
                }
            };

    @Override
    protected void attachBaseContext(Context c) {
        super.attachBaseContext(AppAppearance.context(c));
    }

    @Override
    public void onCreate(Bundle state) {
        AppAppearance.apply(this);
        super.onCreate(state);
        ui = new Ui(this);
        previewedHash = state == null ? "" : state.getString("previewed_hash", "");
        audio = getSystemService(AudioManager.class);
        focus =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build())
                        .setOnAudioFocusChangeListener(
                                change -> {
                                    if (change < 0) stopPreview();
                                })
                        .build();
        loadDraft();
        pickCurrent(CarrierClient.get(this).cachedGreetings());
        build();
        if (CarrierClient.get(this).hasSession()) refresh();
        else status.setText("Connect your mailbox from the inbox to manage your carrier greeting.");
    }

    private void build() {
        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.bg);
        root.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets b =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        v.setPadding(b.left, b.top, b.right, b.bottom);
                    } else
                        v.setPadding(
                                0,
                                insets.getSystemWindowInsetTop(),
                                0,
                                insets.getSystemWindowInsetBottom());
                    return insets;
                });
        LinearLayout nav = ui.row();
        nav.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), 0);
        nav.addView(icon("back", "Back to Settings", this::navigateUp));
        nav.addView(
                ui.label("Greeting", 17, ui.text, true), new LinearLayout.LayoutParams(0, -2, 1));
        refresh = icon("refresh", "Check carrier greeting", this::refresh);
        nav.addView(refresh);
        root.addView(nav);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24));
        body.addView(ui.label("Voicemail greeting", 24, ui.text, true));
        ui.add(
                body,
                ui.label("What callers hear when you can't answer.", 14, ui.secondary, false),
                8);
        LinearLayout existing = ui.row();
        existing.setPadding(ui.dp(16), ui.dp(14), ui.dp(12), ui.dp(14));
        existing.setBackground(ui.shape(ui.surface, 20));
        LinearLayout details = ui.column();
        details.addView(ui.label("Current greeting", 16, ui.text, true));
        currentState = ui.label("", 13, ui.secondary, false);
        ui.add(details, currentState, 6);
        existing.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        currentPlay = icon("play", "Preview current greeting", this::previewCurrent);
        existing.addView(currentPlay);
        ui.add(body, existing, 20);
        ui.add(body, ui.label("New greeting", 18, ui.text, true), 24);
        draftState = ui.label("", 14, ui.secondary, false);
        ui.add(body, draftState, 8);
        LinearLayout sources = ui.row();
        record = ui.button("Record", false, this::recordAction);
        choose = ui.button("Choose audio", false, this::chooseAudio);
        sources.addView(record, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
        size.setMarginStart(ui.dp(8));
        sources.addView(choose, size);
        ui.add(body, sources, 14);
        preview = ui.button("Preview draft", false, this::previewDraft);
        ui.add(body, preview, 10);
        save = ui.button("Save greeting", true, this::confirmSave);
        ui.add(body, save, 18);
        discard =
                ui.button(
                        "Discard draft",
                        false,
                        () -> {
                            if (working || GreetingJobs.busy || recorder != null) return;
                            ui.show(
                                    new AlertDialog.Builder(this)
                                            .setTitle("Discard this draft?")
                                            .setMessage(
                                                    "Remove the local draft. Your carrier greeting"
                                                            + " is kept.")
                                            .setPositiveButton(
                                                    "Discard",
                                                    (d, w) -> {
                                                        stopPreview();
                                                        try {
                                                            GreetingAudio.discard(this);
                                                            previewedHash = "";
                                                            loadDraft();
                                                            update();
                                                        } catch (Exception error) {
                                                            status.setText(
                                                                    "Couldn't discard the draft."
                                                                        + " Check free storage and"
                                                                        + " retry.");
                                                        }
                                                    })
                                            .setNegativeButton("Keep draft", null)
                                            .create());
                        });
        discard.setTextSize(13);
        ui.add(body, discard, 0);
        status = ui.label("", 13, ui.secondary, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(body, status, 12);
        Button carrierSetup =
                ui.button("Call voicemail for greeting help", false, this::callVoicemail);
        carrierSetup.setTextSize(13);
        ui.add(body, carrierSetup, 12);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        update();
        ui.enter(body);
    }

    private Ui.Icon icon(String name, String label, Runnable action) {
        Ui.Icon v = ui.icon(name, label, false, action);
        v.glyphSize(20);
        return v;
    }

    private void pickCurrent(JSONArray entries) {
        current = null;
        if (entries == null) return;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item != null
                    && "personal".equalsIgnoreCase(item.optString("type"))
                    && "CNS-Greeting-On".equalsIgnoreCase(item.optString("flag"))) {
                current = item;
                return;
            }
        }
    }

    private void loadDraft() {
        draft = GreetingAudio.selected(this);
        JSONObject meta = GreetingAudio.metadata(this);
        draftHash = meta.optString("hash");
        draftDuration = meta.optInt("duration");
    }

    private void update() {
        if (status == null) return;
        if (jobVersion != GreetingJobs.version) {
            jobVersion = GreetingJobs.version;
            pickCurrent(CarrierClient.get(this).cachedGreetings());
            loadDraft();
            status.setText(GreetingJobs.status);
        }
        boolean recording = recorder != null, blocked = working || GreetingJobs.busy;
        currentState.setText(
                current == null
                        ? "Carrier default or not checked yet"
                        : Math.max(0, current.optInt("duration")) + " seconds · Personal greeting");
        currentPlay.setEnabled(current != null && !blocked && !recording);
        refresh.setEnabled(!blocked && !recording && !checking);
        record.setText(recording ? "Stop recording" : "Record");
        record.setEnabled(!blocked);
        choose.setEnabled(!blocked && !recording);
        preview.setVisibility(draft == null ? View.GONE : View.VISIBLE);
        preview.setEnabled(draft != null && !blocked && !recording);
        discard.setVisibility(draft == null ? View.GONE : View.VISIBLE);
        discard.setEnabled(!blocked && !recording);
        save.setEnabled(
                draft != null
                        && !blocked
                        && !recording
                        && draftHash.equals(previewedHash)
                        && !draftHash.isEmpty());
        if (recording)
            draftState.setText(
                    "Recording · " + time((int) (SystemClock.elapsedRealtime() - started)));
        else
            draftState.setText(
                    draft == null
                            ? "Record or choose 1–60 seconds of audio."
                            : time(draftDuration)
                                    + (draftHash.equals(previewedHash)
                                            ? " · Ready to save"
                                            : " · Preview before saving"));
        preview.setText(previewingDraft && isPreviewPlaying() ? "Stop preview" : "Preview draft");
        currentPlay.name = !previewingDraft && isPreviewPlaying() ? "pause" : "play";
        currentPlay.invalidate();
        if (GreetingJobs.busy) status.setText(GreetingJobs.status);
        for (Button control : new Button[] {record, choose, preview, save, discard})
            control.setAlpha(control.isEnabled() ? 1f : .4f);
    }

    private boolean isPreviewPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (IllegalStateException preparing) {
            return false;
        }
    }

    private void refresh() {
        if (working || checking || GreetingJobs.busy || recorder != null) return;
        checking = true;
        status.setText("Checking your carrier greeting…");
        update();
        network.execute(
                () -> {
                    try {
                        JSONArray items = CarrierClient.get(this).greetings();
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    checking = false;
                                    pickCurrent(items);
                                    if (!working && recorder == null && !GreetingJobs.busy)
                                        status.setText(
                                                "Current greeting checked · No changes made");
                                    update();
                                });
                    } catch (Exception error) {
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    checking = false;
                                    if (!working && recorder == null && !GreetingJobs.busy)
                                        status.setText(
                                                "Couldn't check the carrier. Your local draft is"
                                                    + " kept. Retry with your carrier connection or"
                                                    + " call voicemail.");
                                    update();
                                });
                    }
                });
    }

    private void recordAction() {
        if (recorder != null) {
            stopRecording();
            return;
        }
        if (working || GreetingJobs.busy) return;
        stopPreview();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, MICROPHONE);
            return;
        }
        MediaRecorder next = null;
        try {
            recordingFile = GreetingAudio.create(this);
            next = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            next.setAudioSource(MediaRecorder.AudioSource.MIC);
            next.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            next.setAudioSamplingRate(8000);
            next.setAudioChannels(1);
            next.setAudioEncodingBitRate(12200);
            next.setMaxDuration(60000);
            next.setOutputFile(recordingFile.getAbsolutePath());
            next.setOnInfoListener(
                    (r, what, extra) -> {
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)
                            stopRecording();
                    });
            next.setOnErrorListener(
                    (r, what, extra) -> {
                        stopRecording();
                        status.setText("Recording stopped. Preview your draft before saving.");
                    });
            next.prepare();
            next.start();
            recorder = next;
            started = SystemClock.elapsedRealtime();
            previewedHash = "";
            status.setText("Recording on this phone. Tap Stop when you're ready.");
            update();
        } catch (Exception failure) {
            if (next != null) next.release();
            if (recordingFile != null) recordingFile.delete();
            recordingFile = null;
            status.setText(
                    "Recording couldn't start. Check microphone access, or choose an audio file"
                            + " instead.");
        }
    }

    private void stopRecording() {
        MediaRecorder previous = recorder;
        if (previous == null) return;
        recorder = null;
        File completed = recordingFile;
        recordingFile = null;
        boolean stopped = true;
        try {
            previous.stop();
        } catch (RuntimeException shortClip) {
            stopped = false;
        } finally {
            previous.release();
        }
        if (!stopped) {
            if (completed != null) completed.delete();
            status.setText("Record at least one second. Your previous draft is kept.");
            update();
            return;
        }
        working = true;
        status.setText("Saving your local draft…");
        update();
        worker.execute(
                () -> {
                    try {
                        GreetingAudio.publish(this, completed);
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    loadDraft();
                                    status.setText(
                                            "Draft saved on this phone. Preview it before saving to"
                                                    + " the carrier.");
                                    update();
                                });
                    } catch (Exception error) {
                        completed.delete();
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    status.setText(
                                            "Use 1–60 seconds of audio. Your previous draft is"
                                                    + " kept.");
                                    update();
                                });
                    }
                });
    }

    private void chooseAudio() {
        if (working || GreetingJobs.busy || recorder != null) return;
        stopPreview();
        try {
            startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("audio/*")
                            .addCategory(Intent.CATEGORY_OPENABLE),
                    PICK_AUDIO);
        } catch (ActivityNotFoundException unavailable) {
            status.setText("No file picker is available. Record a greeting instead.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == MICROPHONE && !destroyed && !isFinishing()) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) recordAction();
            else
                status.setText(
                        "Microphone access is optional. Choose an audio file, or allow Microphone"
                                + " in App permissions.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_AUDIO || result != RESULT_OK || data == null || data.getData() == null)
            return;
        if (GreetingJobs.busy) return;
        Uri source = data.getData();
        working = true;
        previewedHash = "";
        status.setText("Preparing audio on this phone…");
        update();
        worker.execute(
                () -> {
                    try {
                        GreetingAudio.importAudio(
                                this, getContentResolver().openInputStream(source));
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    loadDraft();
                                    status.setText(
                                            "Audio ready. Preview before saving to the carrier.");
                                    update();
                                });
                    } catch (Exception failure) {
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    status.setText(
                                            "Couldn't use that file. Choose 1–60 seconds of"
                                                    + " supported audio under 25 MB. Your previous"
                                                    + " draft is kept.");
                                    update();
                                });
                    }
                });
    }

    private void previewCurrent() {
        if (current == null || working || GreetingJobs.busy) return;
        if (player != null && !previewingDraft) {
            stopPreview();
            return;
        }
        stopPreview();
        JSONObject selected = current;
        working = true;
        status.setText("Opening current greeting…");
        update();
        worker.execute(
                () -> {
                    try {
                        File file = CarrierClient.get(this).greetingAudio(selected);
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    play(file, false, "");
                                });
                    } catch (Exception failure) {
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    status.setText(
                                            "Couldn't open the current greeting. Check your"
                                                    + " connection or call voicemail.");
                                    update();
                                });
                    }
                });
    }

    private void previewDraft() {
        if (draft == null || working || GreetingJobs.busy || recorder != null) return;
        if (player != null && previewingDraft) {
            stopPreview();
            return;
        }
        stopPreview();
        File source = draft;
        String expected = draftHash;
        working = true;
        update();
        worker.execute(
                () -> {
                    try {
                        if (!expected.equals(GreetingAudio.hash(source)))
                            throw new IOException("Changed draft");
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    play(source, true, expected);
                                });
                    } catch (Exception failure) {
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    working = false;
                                    previewedHash = "";
                                    status.setText(
                                            "The draft couldn't be verified. Choose or record it"
                                                    + " again.");
                                    update();
                                });
                    }
                });
    }

    private void play(File file, boolean isDraft, String expected) {
        stopPreview();
        previewingDraft = isDraft;
        try {
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                status.setText("Audio is in use. Retry preview when it is available.");
                update();
                return;
            }
            MediaPlayer next = new MediaPlayer();
            player = next;
            next.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build());
            next.setDataSource(file.getAbsolutePath());
            next.setOnPreparedListener(
                    p -> {
                        if (destroyed || player != p) return;
                        try {
                            p.start();
                            if (isDraft) previewedHash = expected;
                            status.setText(
                                    isDraft
                                            ? "Previewing draft · Carrier greeting unchanged"
                                            : "Previewing current carrier greeting");
                            update();
                        } catch (RuntimeException error) {
                            stopPreview();
                            status.setText("Couldn't play this audio. Try another recording.");
                        }
                    });
            next.setOnCompletionListener(
                    p -> {
                        stopPreview();
                        status.setText(
                                isDraft
                                        ? "Preview complete. Save when you're ready."
                                        : "Current greeting preview complete.");
                    });
            next.setOnErrorListener(
                    (p, a, b) -> {
                        stopPreview();
                        status.setText("Couldn't play this audio. Try another recording.");
                        return true;
                    });
            next.prepareAsync();
        } catch (Exception failure) {
            stopPreview();
            status.setText("Couldn't open this audio for preview.");
            update();
        }
    }

    private void stopPreview() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (audio != null) audio.abandonAudioFocusRequest(focus);
        if (preview != null) update();
    }

    private void confirmSave() {
        if (draft == null
                || working
                || GreetingJobs.busy
                || recorder != null
                || !draftHash.equals(previewedHash)) return;
        stopPreview();
        File source = draft;
        String expected = draftHash;
        confirmation =
                new AlertDialog.Builder(this)
                        .setTitle("Replace your personal greeting?")
                        .setMessage(
                                "Upload this "
                                        + ((draftDuration + 999) / 1000)
                                        + "-second recording and use it for callers. Your local"
                                        + " draft is kept. The app checks the carrier before"
                                        + " confirming the change; older greetings aren't deleted.")
                        .setPositiveButton(
                                "Save greeting",
                                (d, w) -> {
                                    if (GreetingJobs.save(this, source, expected)) update();
                                })
                        .setNegativeButton("Cancel", null)
                        .create();
        ui.show(confirmation);
    }

    private void callVoicemail() {
        stopPreview();
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("voicemail:")));
        } catch (ActivityNotFoundException missing) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL));
                Toast.makeText(
                                this,
                                "Press and hold 1 in Phone, then follow the greeting menu.",
                                Toast.LENGTH_LONG)
                        .show();
            } catch (ActivityNotFoundException unavailable) {
                status.setText(
                        "Open your phone app, press and hold 1, and follow the greeting menu.");
            }
        }
    }

    private static String time(int ms) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", ms / 60000, ms / 1000 % 60);
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(tick);
        stopPreview();
        if (recorder != null) stopRecording();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("previewed_hash", previewedHash);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        visible = false;
        handler.removeCallbacksAndMessages(null);
        stopPreview();
        if (confirmation != null) confirmation.dismiss();
        worker.shutdown();
        network.shutdown();
        super.onDestroy();
    }
}
