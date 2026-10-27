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
    private ProgressBar progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View[] choices = new View[2];
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
        nav.addView(ui.icon("back", "Back to Settings", false, () -> finish()));
        nav.addView(ui.label("Settings", 17, ui.text, true));
        root.addView(nav);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = ui.column();
        content.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(32));
        content.addView(ui.label("Transcription quality", 30, ui.text, true));
        TextView explanation =
                ui.label(
                        "Choose the balance that suits you. Both options work in English on this"
                                + " phone, without uploading your recordings.",
                        16,
                        ui.secondary,
                        false);
        explanation.setLineSpacing(ui.dp(4), 1);
        ui.add(content, explanation, 14);
        TranscriptionQuality[] qualities = {
            TranscriptionQuality.STANDARD, TranscriptionQuality.ENHANCED
        };
        for (int i = 0; i < qualities.length; i++) {
            TranscriptionQuality q = qualities[i];
            LinearLayout card = ui.column();
            card.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18));
            card.setBackground(ui.shape(ui.surface, 20));
            LinearLayout titleRow = ui.row();
            titleRow.addView(
                    ui.label(q.title, 20, ui.text, true), new LinearLayout.LayoutParams(0, -2, 1));
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
                            "Whisper "
                                    + (q == TranscriptionQuality.STANDARD ? "Tiny" : "Base")
                                    + " · "
                                    + q.sizeLabel(),
                            14,
                            ui.accent,
                            false),
                    10);
            TextView detail = ui.label(q.description, 15, ui.secondary, false);
            detail.setLineSpacing(ui.dp(4), 1);
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
                                    .setText(
                                            j == (q == TranscriptionQuality.STANDARD ? 0 : 1)
                                                    ? "Selected"
                                                    : "");
                        }
                        previous = "";
                        update();
                        AppMotion.reveal(selected, 140, 0);
                    });
            choices[i] = card;
            ui.add(content, card, 20);
        }
        state = ui.label("", 15, ui.secondary, false);
        state.setLineSpacing(ui.dp(4), 1);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.add(content, state, 24);
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
                                                        "Saved transcripts and recordings are kept."
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
        ui.add(content, remove, 10);
        TextView note =
                ui.label(
                        "Changing quality keeps your existing transcripts. Open a recording and"
                            + " choose Update transcript to apply the new quality to it. Downloaded"
                            + " options stay on this phone until you remove them.",
                        14,
                        ui.secondary,
                        false);
        note.setLineSpacing(ui.dp(4), 1);
        ui.add(content, note, 24);
        Button about =
                ui.button(
                        "About these options",
                        false,
                        () ->
                                ui.show(
                                        new AlertDialog.Builder(this)
                                                .setTitle("About transcription quality")
                                                .setMessage(
                                                        "Standard uses Whisper tiny.en. Enhanced"
                                                            + " uses Whisper base.en, a larger"
                                                            + " speech-recognition model. Larger"
                                                            + " models can improve recognition, but"
                                                            + " no model is always correct. Check"
                                                            + " important names, dates, and numbers"
                                                            + " against the audio.\n\n"
                                                            + "Both models are open source and run"
                                                            + " on this phone. Public model"
                                                            + " downloads are verified before use.")
                                                .setPositiveButton("Done", null)
                                                .create()));
        about.setBackgroundColor(0);
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
                        .setTitle("Download " + q.title + "?")
                        .setMessage(
                                "About "
                                        + q.sizeLabel()
                                        + " from the public model host. Wi-Fi is recommended. Your"
                                        + " recordings and transcripts stay on this phone.")
                        .setPositiveButton(
                                "Download",
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
                        + Transcription.cancelled;
        if (current.equals(previous)) return;
        previous = current;
        boolean active = Transcription.busy;
        for (View v : choices) v.setEnabled(!active);
        if (active)
            state.setText(
                    Transcription.cancelled
                            ? "Cancelling after the current operation…"
                            : Transcription.status);
        else if (!Transcription.failure.isEmpty() && Transcription.job.equals("model"))
            state.setText(Transcription.failure);
        else
            state.setText(
                    ready
                            ? q.title + " is ready. New transcripts use this quality."
                            : q.title
                                    + " needs a one-time "
                                    + q.sizeLabel()
                                    + " download. Listening and saved transcripts remain"
                                    + " available.");
        progress.setVisibility(active && Transcription.downloading ? View.VISIBLE : View.GONE);
        progress.setProgress(Transcription.progress);
        download.setText("Download " + q.title + " · " + q.sizeLabel());
        download.setVisibility(!ready && !active ? View.VISIBLE : View.GONE);
        cancel.setVisibility(active ? View.VISIBLE : View.GONE);
        cancel.setEnabled(!Transcription.cancelled);
        remove.setVisibility(ready && !active ? View.VISIBLE : View.GONE);
        for (int i = 0; i < choices.length; i++) {
            View v = choices[i];
            TranscriptionQuality option =
                    i == 0 ? TranscriptionQuality.STANDARD : TranscriptionQuality.ENHANCED;
            TextView detail = (TextView) ((LinearLayout) v).getChildAt(1);
            String caption =
                    "Whisper "
                            + (i == 0 ? "Tiny" : "Base")
                            + " · "
                            + option.sizeLabel()
                            + (Transcription.ready(this, option) ? " · Downloaded" : " download");
            if (!caption.contentEquals(detail.getText())) detail.setText(caption);
            boolean selected = (i == 0) == (q == TranscriptionQuality.STANDARD);
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
