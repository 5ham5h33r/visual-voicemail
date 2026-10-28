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
    private final RadioButton[] indicators = new RadioButton[qualities.length];
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
        nav.addView(ui.label("Transcription", Ui.SECTION_TITLE, ui.text, true));
        root.addView(nav);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(24));
        TextView explanation =
                ui.label(
                        "Transcription on your phone. Tiny and Base are English-only. Large v3"
                                + " supports other languages and English translation.",
                        14,
                        ui.secondary,
                        false);
        recommendation = explanation;
        explanation.setLineSpacing(ui.dp(4), 1);
        explanation.setTextSize(13);
        ui.add(content, ui.label("Language & output", 13, ui.secondary, true), 8);
        LinearLayout languageGroup = ui.column();
        languageGroup.setBackground(ui.shape(ui.surface, 20));
        ui.add(content, languageGroup, 8);
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
        ui.add(languageGroup, language, 0);
        View divider = new View(this);
        divider.setBackgroundColor(ui.divider);
        LinearLayout.LayoutParams dividerSize =
                new LinearLayout.LayoutParams(-1, Math.max(1, ui.dp(.5f)));
        dividerSize.setMargins(ui.dp(16), 0, ui.dp(16), 0);
        languageGroup.addView(divider, dividerSize);
        ui.add(languageGroup, output, 0);
        ui.add(content, ui.label("Quality", 13, ui.secondary, true), 20);
        LinearLayout qualityGroup = ui.column();
        qualityGroup.setBackground(ui.shape(ui.surface, 20));
        qualityGroup.setClipToOutline(true);
        ui.add(content, qualityGroup, 8);
        for (int i = 0; i < qualities.length; i++) {
            TranscriptionQuality q = qualities[i];
            LinearLayout card = ui.column();
            card.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
            card.setBackground(ui.shape(ui.surface, 20));
            LinearLayout titleRow = ui.row();
            titleRow.addView(
                    ui.label(q.title, 15, ui.text, true), new LinearLayout.LayoutParams(0, -2, 1));
            RadioButton selected = new RadioButton(this);
            selected.setButtonTintList(
                    new android.content.res.ColorStateList(
                            new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
                            new int[] {ui.accent, ui.secondary}));
            selected.setClickable(false);
            selected.setFocusable(false);
            selected.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            selected.setChecked(q == Transcription.selected(this));
            titleRow.addView(selected, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
            indicators[i] = selected;
            card.addView(titleRow);
            AppMotion.press(card);
            ui.add(
                    card,
                    ui.label(
                            "Whisper " + q.modelName() + " · " + q.sizeLabel(),
                            12,
                            ui.secondary,
                            false),
                    2);
            titleRow.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            card.getChildAt(1).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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
                        previous = "";
                        update();
                        AppMotion.reveal(selected, 140, 0);
                    });
            choices[i] = card;
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(ui.divider);
                LinearLayout.LayoutParams size =
                        new LinearLayout.LayoutParams(-1, Math.max(1, ui.dp(.5f)));
                size.setMargins(ui.dp(16), 0, ui.dp(16), 0);
                qualityGroup.addView(line, size);
            }
            ui.add(qualityGroup, card, 0);
        }
        ui.add(content, recommendation, 8);
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
                                                        "Complete and partial files for this model"
                                                            + " will be removed. Saved transcripts"
                                                            + " and recordings are kept. This"
                                                            + " option will need downloading again"
                                                            + " before you can use it.")
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
        remove.setTextSize(13);
        ui.add(content, remove, 10);
        LinearLayout.LayoutParams removeSize = (LinearLayout.LayoutParams) remove.getLayoutParams();
        removeSize.width = -2;
        removeSize.gravity = Gravity.END;
        remove.setLayoutParams(removeSize);
        TextView note =
                ui.label(
                        "Changing quality keeps your existing transcripts.",
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
        about.setTextSize(13);
        ui.add(content, about, 16);
        LinearLayout.LayoutParams aboutSize = (LinearLayout.LayoutParams) about.getLayoutParams();
        aboutSize.width = -2;
        about.setLayoutParams(aboutSize);
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
        boolean multilingual =
                !"en".equals(SpeechOptions.language(this, q)) || SpeechOptions.english(this);
        recommendation.setText(
                multilingual
                        ? "Large v3 supports other languages and English translation."
                        : "Standard is a good starting point for English.");
        ui.settingValue(
                language,
                "Recording language",
                SpeechOptions.label(SpeechOptions.language(this, q)));
        ui.settingValue(
                output,
                "Transcript output",
                SpeechOptions.english(this)
                        ? "Original + English translation"
                        : "Original language");
        boolean active = Transcription.busy;
        language.setEnabled(!active);
        output.setEnabled(!active);
        language.setAlpha(active ? .5f : 1f);
        output.setAlpha(active ? .5f : 1f);
        for (View v : choices) {
            v.setEnabled(!active);
            v.setAlpha(active ? .5f : 1f);
        }
        if (active)
            state.setText(
                    Transcription.cancelled
                            ? Transcription.downloading
                                    ? "Pausing download..."
                                    : "Stopping after the current phrase..."
                            : Transcription.status);
        else if (!Transcription.failure.isEmpty() && Transcription.job.equals("model"))
            state.setText(Transcription.failure);
        else
            state.setText(
                    ready
                            ? "Ready for new transcripts"
                            : Transcription.downloadedBytes(this, q) > 0
                                    ? "Saved download progress is available. Resume to continue, or"
                                            + " remove it to free space. Your recordings and saved"
                                            + " text are kept."
                                    : q.title
                                            + " needs a one-time "
                                            + q.sizeLabel()
                                            + " download. Listening and saved transcripts remain"
                                            + " available.");
        long retained = Transcription.downloadedBytes(this, q);
        progress.setVisibility(
                (active && Transcription.downloading || !ready && retained > 0)
                        ? View.VISIBLE
                        : View.GONE);
        progress.setProgress(active ? Transcription.progress : (int) (retained * 100 / q.bytes()));
        download.setText(Transcription.downloadAction(this) + " · " + q.sizeLabel());
        download.setVisibility(!ready && !active ? View.VISIBLE : View.GONE);
        cancel.setText(Transcription.downloading ? "Pause download" : "Stop transcription");
        cancel.setVisibility(active ? View.VISIBLE : View.GONE);
        cancel.setEnabled(!Transcription.cancelled);
        remove.setText(ready ? "Remove downloaded model" : "Remove download");
        remove.setVisibility(
                (ready || Transcription.downloadedBytes(this, q) > 0) && !active
                        ? View.VISIBLE
                        : View.GONE);
        for (int i = 0; i < choices.length; i++) {
            View v = choices[i];
            TranscriptionQuality option = qualities[i];
            TextView detail = (TextView) ((LinearLayout) v).getChildAt(1);
            String caption =
                    "Whisper "
                            + option.modelName()
                            + " · "
                            + option.sizeLabel()
                            + (option == TranscriptionQuality.HIGHEST
                                    ? " · Multilingual"
                                    : " · English only")
                            + (Transcription.ready(this, option) ? " · Downloaded" : " download");
            if (!caption.contentEquals(detail.getText())) detail.setText(caption);
            boolean selected = option == q;
            android.graphics.drawable.GradientDrawable fill =
                    ui.shape(selected ? ui.tint : android.graphics.Color.TRANSPARENT, 0);
            v.setBackground(
                    new android.graphics.drawable.RippleDrawable(
                            android.content.res.ColorStateList.valueOf(ui.divider), fill, null));
            indicators[i].setChecked(selected);
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
