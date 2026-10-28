package org.visualvoicemail.app;

import android.app.AlertDialog;
import android.content.Context;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Quality choices explain what changes before any download or transcript replacement. */
public final class TranscriptionSettingsActivity extends MotionActivity {
    private Ui ui;
    private LinearLayout content;
    private TextView state;
    private Button download, cancel, remove;
    private TextView recommendation;
    private Button language, output;
    private ProgressBar progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TranscriptionQuality[] qualities = TranscriptionQuality.values();
    private final View[] choices = new View[qualities.length];
    private boolean visible;
    private String previous = "";
    private final Runnable ticker =
            new Runnable() {
                public void run() {
                    if (!visible) return;
                    update();
                    handler.postDelayed(this, 300);
                }
            };

    @Override
    protected void attachBaseContext(Context c) {
        super.attachBaseContext(AppAppearance.context(c));
    }

    @Override
    public void onCreate(Bundle saved) {
        AppAppearance.apply(this);
        super.onCreate(saved);
        ui = new Ui(this);
        build();
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
        nav.setPadding(ui.dp(12), ui.dp(4), ui.dp(16), 0);
        nav.addView(ui.icon("back", "Back to Settings", false, this::navigateUp));
        nav.addView(ui.label("Settings", 17, ui.text, true));
        root.addView(nav);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24));
        content.addView(ui.label("Transcription quality", Ui.DETAIL_TITLE, ui.text, true));
        TextView explanation =
                ui.label(
                        "Transcription on your phone. Tiny and Base are English-only. Large v3"
                            + " supports other languages and English translation.",
                        14,
                        ui.secondary,
                        false);
        recommendation = explanation;
        explanation.setLineSpacing(ui.dp(4), 1);
        ui.add(content, explanation, 14);
        language =
                ui.button(
                        "",
                        false,
                        () ->
                                SpeechOptions.chooseLanguage(
                                        this,
                                        () -> {
                                            previous = "";
                                            update();
                                        }));
        output =
                ui.button(
                        "",
                        false,
                        () ->
                                SpeechOptions.chooseOutput(
                                        this,
                                        () -> {
                                            previous = "";
                                            update();
                                        }));
        language.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        output.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        ui.add(content, language, 8);
        ui.add(content, output, 0);
        for (int i = 0; i < qualities.length; i++) {
            TranscriptionQuality q = qualities[i];
            LinearLayout card = ui.column();
            card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            card.setBackground(ui.shape(ui.surface, 20));
            LinearLayout titleRow = ui.row();
            titleRow.addView(
                    ui.label(q.title, 17, ui.text, true), new LinearLayout.LayoutParams(0, -2, 1));
            TextView selected =
                    ui.label(
                            q.key.equals(Transcription.selected(this).key) ? "Selected" : "",
                            13,
                            ui.accent,
                            true);
            titleRow.addView(selected);
            card.addView(titleRow);
            AppMotion.press(card);
            ui.add(
                    card,
                    ui.label(
                            "Whisper " + q.modelName() + " · " + q.sizeLabel(),
                            14,
                            ui.accent,
                            false),
                    10);
            TextView detail = ui.label(q.description, 14, ui.secondary, false);
            detail.setLineSpacing(ui.dp(3), 1);
            ui.add(card, detail, 10);
            card.setAccessibilityDelegate(
                    new View.AccessibilityDelegate() {
                        @Override
                        public void onInitializeAccessibilityNodeInfo(
                                View host, android.view.accessibility.AccessibilityNodeInfo info) {
                            super.onInitializeAccessibilityNodeInfo(host, info);
                            info.setClassName(RadioButton.class.getName());
                            info.setCheckable(true);
                            info.setChecked(host.isSelected());
                        }
                    });
            card.setFocusable(true);
            card.setClickable(true);
            card.setContentDescription(
                    q.label() + ". " + q.summary + ". " + q.sizeLabel() + ". " + q.description);
            card.setOnClickListener(
                    v -> {
                        if (!Transcription.select(this, q.key)) {
                            Toast.makeText(
                                            this,
                                            "Finish or cancel the current operation before changing"
                                                    + " quality.",
                                            Toast.LENGTH_LONG)
                                    .show();
                            return;
                        }
                        for (int j = 0; j < choices.length; j++) {
                            LinearLayout item = (LinearLayout) choices[j];
                            ((TextView) ((LinearLayout) item.getChildAt(0)).getChildAt(1))
                                    .setText(qualities[j] == q ? "Selected" : "");
                        }
                        previous = "";
                        update();
                        AppMotion.reveal(selected, 140, 0);
                    });
            choices[i] = card;
            ui.add(content, card, 12);
        }
        state = ui.label("", 13, ui.secondary, false);
        state.setLineSpacing(ui.dp(4), 1);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(content, state, 16);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.accent));
        progress.setMax(100);
        ui.add(content, progress, 12);
        download = ui.button("", true, this::download);
        ui.add(content, download, 18);
        cancel = ui.button("Cancel current operation", false, () -> Transcription.cancel());
        ui.add(content, cancel, 10);
        remove =
                ui.button(
                        "Remove downloaded model",
                        false,
                        () ->
                                ui.show(
                                        new AlertDialog.Builder(this)
                                                .setTitle(
                                                        "Remove "
                                                                + Transcription.selected(this).title
                                                                + "?")
                                                .setMessage(
                                                        "Complete and partial files for this model will be removed. Saved transcripts and recordings are kept."
                                                            + " This option will need downloading"
                                                            + " again before you can use it.")
                                                .setPositiveButton(
                                                        "Remove",
                                                        (d, w) -> {
                                                            if (!Transcription.clear(this, true))
                                                                Toast.makeText(
                                                                                this,
                                                                                "Wait for the"
                                                                                    + " current"
                                                                                    + " operation"
                                                                                    + " to finish,"
                                                                                    + " then"
                                                                                    + " retry.",
                                                                                Toast.LENGTH_LONG)
                                                                        .show();
                                                            previous = "";
                                                            update();
                                                        })
                                                .setNegativeButton("Cancel", null)
                                                .create()));
        remove.setBackgroundColor(0);
        remove.setTextSize(13);
        ui.add(content, remove, 10);
        TextView note =
                ui.label(
                        "New quality applies to new transcripts. Use Update transcript to redo an"
                                + " existing one.",
                        12,
                        ui.secondary,
                        false);
        note.setLineSpacing(ui.dp(4), 1);
        ui.add(content, note, 16);
        Button about =
                ui.button(
                        "About these options",
                        false,
                        () ->
                                ui.show(
                                        new AlertDialog.Builder(this)
                                                .setTitle("About transcription quality")
                                                .setMessage(
                                                        "Standard (Whisper Tiny): English-only."
                                                            + " Fast results with a smaller"
                                                            + " download.\n\n"
                                                            + "Enhanced (Whisper Base):"
                                                            + " English-only. A larger model that"
                                                            + " may recognize difficult speech"
                                                            + " better.\n\n"
                                                            + "Highest quality (Whisper Large v3):"
                                                            + " Multilingual recognition and"
                                                            + " English translation. Full int8"
                                                            + " model. Much slower; needs an 8 GB"
                                                            + " or larger phone with enough"
                                                            + " available memory.\n\n"
                                                            + "Larger models can improve"
                                                            + " recognition, but no model is always"
                                                            + " correct. Check important names,"
                                                            + " dates, and numbers against the"
                                                            + " audio.\n\n"
                                                            + "All three models are open source and"
                                                            + " run on this phone. Public model"
                                                            + " downloads are verified before use.")
                                                .setPositiveButton("Done", null)
                                                .create()));
        about.setBackgroundColor(0);
        about.setTextSize(13);
        ui.add(content, about, 16);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        update();
    }

    private void download() {
        TranscriptionQuality q = Transcription.selected(this);
        ui.show(
                new AlertDialog.Builder(this)
                        .setTitle(Transcription.downloadAction(this) + "?")
                        .setMessage(
                                "About "
                                        + q.sizeLabel()
                                        + " from the public model host. Wi-Fi is recommended. Your"
                                        + " recordings and transcripts stay on this phone."
                                        + (q == TranscriptionQuality.HIGHEST
                                                ? "\n\n"
                                                        + "Allow about 2 GB of free storage for"
                                                        + " setup. Large v3 can take minutes per"
                                                        + " recording and use several GB of memory."
                                                        + " Keep the app open; choose Standard or"
                                                        + " Enhanced for faster results."
                                                : ""))
                        .setPositiveButton(
                                Transcription.downloadAction(this),
                                (d, w) -> {
                                    Transcription.download(this);
                                    previous = "";
                                    update();
                                })
                        .setNegativeButton("Not now", null)
                        .create());
    }

    private void update() {
        TranscriptionQuality q = Transcription.selected(this);
        boolean ready = Transcription.ready(this);
        String current =
                q.key
                        + ready
                        + Transcription.busy
                        + Transcription.status
                        + Transcription.failure
                        + Transcription.cancelled
                        + SpeechOptions.language(this, q)
                        + SpeechOptions.english(this);
        if (current.equals(previous)) return;
        previous = current;
        boolean multilingual = !"en".equals(SpeechOptions.language(this, q)) || SpeechOptions.english(this);
        recommendation.setText(multilingual
            ? "For this language or English translation, use Highest quality (Large v3). Check its download and memory needs below."
            : "Start with Standard (Tiny) for English. Larger models are optional; your saved text is kept when quality changes.");
        language.setText(
                "Language · " + SpeechOptions.label(SpeechOptions.language(this, q)) + "  ›");
        output.setText(
                SpeechOptions.english(this)
                        ? "Output · Original + English  ›"
                        : "Output · Original language  ›");
        boolean active = Transcription.busy;
        for (View v : choices) v.setEnabled(!active);
        if (active)
            state.setText(
                    Transcription.cancelled
                            ? Transcription.downloading ? "Pausing download..." : "Stopping after the current phrase..."
                            : Transcription.status);
        else if (!Transcription.failure.isEmpty() && Transcription.job.equals("model"))
            state.setText(Transcription.failure);
        else
            state.setText(
                    ready
                            ? "Ready for new transcripts"
                            : Transcription.downloadedBytes(this, q) > 0
                                    ? "Saved download progress is available. Resume to continue, or remove it to free space. Your recordings and saved text are kept."
                                    : q.title
                                    + " needs a one-time "
                                    + q.sizeLabel()
                                    + " download. Listening and saved transcripts remain"
                                    + " available.");
        long retained = Transcription.downloadedBytes(this, q);
        progress.setVisibility((active && Transcription.downloading || !ready && retained > 0) ? View.VISIBLE : View.GONE);
        progress.setProgress(active ? Transcription.progress : (int)(retained * 100 / q.bytes()));
        download.setText(Transcription.downloadAction(this) + " · " + q.sizeLabel());
        download.setVisibility(!ready && !active ? View.VISIBLE : View.GONE);
        cancel.setText(Transcription.downloading ? "Pause download" : "Stop transcription");
        cancel.setVisibility(active ? View.VISIBLE : View.GONE);
        cancel.setEnabled(!Transcription.cancelled);
        remove.setText(ready ? "Remove downloaded model" : "Remove download");
        remove.setVisibility((ready || Transcription.downloadedBytes(this, q)>0) && !active ? View.VISIBLE : View.GONE);
        for (int i = 0; i < choices.length; i++) {
            View v = choices[i];
            TranscriptionQuality option = qualities[i];
            TextView detail = (TextView) ((LinearLayout) v).getChildAt(1);
            String caption =
                    "Whisper "
                            + option.modelName()
                            + " · "
                            + option.sizeLabel()
                            + (Transcription.ready(this, option) ? " · Downloaded" : " download");
            if (!caption.contentEquals(detail.getText())) detail.setText(caption);
            boolean selected = option == q;
            if (v.isSelected() != selected)
                v.setBackground(ui.shape(selected ? ui.tint : ui.surface, 20));
            v.setSelected(selected);
            if (Build.VERSION.SDK_INT >= 30) v.setStateDescription(selected ? "Selected" : "");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
