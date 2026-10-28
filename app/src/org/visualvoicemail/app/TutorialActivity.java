package org.visualvoicemail.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;

/** A short introduction and optional permission setup, followed by tips on the real interface. */
public final class TutorialActivity extends MotionActivity {
    private Ui ui;
    private int page;
    private boolean replay;
    private LinearLayout root;
    private ScrollView scroll;
    private static final String SEEN = "tutorial_seen", PAGE = "tutorial_page";
    private static final String[] TITLES = {
        "Your voicemail.\nA little clearer.",
        "Catch up at\nyour own pace.",
        "Read it.\nHear what matters.",
        "A few things\nto make it yours."
    };
    private static final String[] COPY = {
        "A calm place for the messages you missed. Keep your existing mailbox and your favorite"
                + " phone app.",
        "Find a caller, pick up where you left off, and keep your inbox in order.",
        "Turn recordings into text on this phone. Tap a phrase to hear it, or find the detail"
                + " you're looking for.",
        "Choose what helps you. Everything here is optional, and you can change it later in"
                + " Settings."
    };

    static boolean needed(Context c) {
        return !c.getSharedPreferences("settings", 0).getBoolean(SEEN, false);
    }

    static Intent replay(Context c) {
        return new Intent(c, TutorialActivity.class).putExtra("replay", true);
    }

    static void acknowledge(Context c) {
        c.getSharedPreferences("settings", 0).edit().putBoolean(SEEN, true).putInt(PAGE, 0).apply();
    }

    @Override
    protected void attachBaseContext(Context c) {
        super.attachBaseContext(AppAppearance.context(c));
    }

    @Override
    public void onCreate(Bundle state) {
        AppAppearance.apply(this);
        super.onCreate(state);
        ui = new Ui(this);
        replay = getIntent().getBooleanExtra("replay", false);
        page =
                state != null
                        ? state.getInt(PAGE)
                        : replay ? 0 : getSharedPreferences("settings", 0).getInt(PAGE, 0);
        page = Math.max(0, Math.min(3, page));
        if (Build.VERSION.SDK_INT >= 33)
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        build(false);
    }

    private void build(boolean animate) {
        root = ui.column();
        root.setBackgroundColor(ui.bg);
        root.setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(16));
        root.setOnApplyWindowInsetsListener(
                (v, insets) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.graphics.Insets b =
                                insets.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        v.setPadding(
                                ui.dp(20) + b.left,
                                b.top,
                                ui.dp(20) + b.right,
                                ui.dp(16) + b.bottom);
                    } else
                        v.setPadding(
                                ui.dp(20),
                                insets.getSystemWindowInsetTop(),
                                ui.dp(20),
                                ui.dp(16) + insets.getSystemWindowInsetBottom());
                    return insets;
                });
        LinearLayout nav = ui.row();
        if (page > 0)
            nav.addView(ui.icon("back", "Previous introduction screen", false, this::back));
        TextView brand = ui.label("VOICE MAIL", 12, ui.secondary, true);
        brand.setLetterSpacing(.12f);
        nav.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button skip = ui.button(replay ? "Close" : "Skip", false, () -> complete(false));
        skip.setBackgroundColor(0);
        nav.addView(skip);
        root.addView(nav);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = ui.column();
        body.setPadding(0, ui.dp(28), 0, ui.dp(20));
        if (page == 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(R.mipmap.ic_launcher);
            body.addView(icon, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)));
        }
        TextView title = ui.label(TITLES[page], 30, ui.text, true);
        title.setLineSpacing(ui.dp(2), 1);
        ui.add(body, title, page == 0 ? 28 : 4);
        TextView subtitle = ui.label(COPY[page], 15, ui.secondary, false);
        subtitle.setLineSpacing(ui.dp(5), 1);
        ui.add(body, subtitle, 16);
        if (page == 0) {
            feature(
                    body,
                    "Your existing mailbox",
                    "Connect with Mint mobile data. No forwarding setup.",
                    "sound");
            feature(
                    body,
                    "Your phone, your choice",
                    "Keep Samsung Phone as your default dialer.",
                    "transcript");
        } else if (page == 1) {
            TextView demo = ui.label("TRY A SAMPLE", 12, ui.secondary, true);
            ui.add(body, demo, 28);
            final TextView result =
                    ui.label("Tap a sample message to see its actions.", 13, ui.secondary, false);
            LinearLayout sample = ui.column();
            sample.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18));
            sample.setBackground(ui.shape(ui.surface, 22));
            sample.addView(ui.label("Alex Morgan", 16, ui.text, true));
            ui.add(sample, ui.label("Today · 0:24 · Sample voicemail", 13, ui.secondary, false), 8);
            LinearLayout actions = ui.column();
            Button read =
                    ui.button(
                            "Try marking as read",
                            false,
                            () -> {
                                result.setText(
                                        "Sample marked read. Your real messages are unchanged.");
                            });
            Button mark =
                    ui.button(
                            "See how Delete works",
                            false,
                            () ->
                                    result.setText(
                                            "Delete verifies a saved recording first, then removes the carrier copy. The recording stays in Saved. This sample changes nothing."));
            actions.addView(read, new LinearLayout.LayoutParams(-1, -2));
            ui.add(actions, mark, 10);
            ui.add(sample, actions, 18);
            ui.add(body, sample, 14);
            ui.add(body, result, 14);
            feature(
                    body,
                    "Find it quickly",
                    "Search contact names, dates, and saved transcripts.",
                    "search");
        } else if (page == 2) {
            TextView demo = ui.label("TRY TAPPING A PHRASE · SAMPLE", 12, ui.secondary, true);
            ui.add(body, demo, 28);
            TextView position = ui.label("0:00  ━━━━━━━━━━━  0:24", 13, ui.accent, true);
            String[] phrases = {
                "Hey, it's Alex. Just calling to catch up.",
                "Give me a call when you have a moment."
            };
            LinearLayout samples = ui.column();
            for (int i = 0; i < phrases.length; i++) {
                final int index = i;
                Button phrase =
                        ui.button(
                                (i == 0 ? "0:02  " : "0:12  ") + phrases[i],
                                false,
                                () -> {
                                    position.setText(
                                            (index == 0 ? "0:02" : "0:12")
                                                    + "  ━━━━━━━━━━━  0:24 · Sample position");
                                });
                phrase.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                ui.add(samples, phrase, 10);
            }
            ui.add(body, samples, 14);
            ui.add(body, position, 20);
            TextView note =
                    ui.label(
                            "Standard and Enhanced use English models (104–161 MB). Large v3 adds other languages and English translation (1.78 GB). Choose in the transcript or Settings. Audio stays on this phone. Words and phrase timing can be imperfect.",
                            14,
                            ui.secondary,
                            false);
            note.setLineSpacing(ui.dp(4), 1);
            ui.add(body, note, 24);
        } else {
            boolean contacts =
                    checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                                    == PackageManager.PERMISSION_GRANTED
                            && getSharedPreferences("settings", 0)
                                    .getBoolean("contact_names", false);
            permission(
                    body,
                    "Recognize your callers",
                    "Optional Contacts access. Names are looked up on this phone.",
                    contacts ? "Contacts enabled" : "Enable contact names",
                    contacts,
                    () -> {
                        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                                == PackageManager.PERMISSION_GRANTED) {
                            getSharedPreferences("settings", 0)
                                    .edit()
                                    .putBoolean("contact_names", true)
                                    .apply();
                            build(false);
                        } else
                            requestPermissions(
                                    new String[] {android.Manifest.permission.READ_CONTACTS}, 41);
                    });
            permission(
                    body,
                    "Know when there's a message",
                    "Alerts follow mailbox checks, about every 15 minutes. Android may delay"
                            + " checks.",
                    VoicemailAlerts.enabled(this) ? "Manage alerts" : "Enable alerts",
                    false,
                    () -> {
                        getSharedPreferences("settings", 0)
                                .edit()
                                .putBoolean("alerts_prompted", true)
                                .apply();
                        if (VoicemailAlerts.enabled(this))
                            startActivity(VoicemailAlerts.systemSettings(this));
                        else VoicemailAlerts.enable(this, 45);
                        build(false);
                    });
            TextView note =
                    ui.label(
                            "Light, Dark, and true-black AMOLED are in Settings. We'll point out"
                                    + " the main controls next.",
                            14,
                            ui.secondary,
                            false);
            note.setLineSpacing(ui.dp(4), 1);
            ui.add(body, note, 24);
        }
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout progress = ui.row();
        progress.setGravity(Gravity.CENTER);
        progress.setPadding(0, ui.dp(8), 0, ui.dp(16));
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            dot.setBackground(ui.shape(i == page ? ui.accent : ui.divider, 4));
            LinearLayout.LayoutParams p =
                    new LinearLayout.LayoutParams(ui.dp(i == page ? 24 : 6), ui.dp(6));
            p.setMargins(ui.dp(4), 0, ui.dp(4), 0);
            progress.addView(dot, p);
        }
        progress.setContentDescription("Introduction " + (page + 1) + " of 4");
        boolean quickStart = page == 0 && !replay;
        if (quickStart) {
            root.addView(ui.button("Show me around", false, () -> {
                page = 1;
                getSharedPreferences("settings", 0).edit().putInt(PAGE, page).apply();
                build(true);
            }), new LinearLayout.LayoutParams(-1, -2));
        } else root.addView(progress);
        root.addView(
                ui.button(
                        quickStart || page == 3 ? "Get Started" : "Continue",
                        true,
                        () -> {
                            if (quickStart) complete(false);
                            else if (page == 3) complete(true);
                            else {
                                page++;
                                getSharedPreferences("settings", 0)
                                        .edit()
                                        .putInt(PAGE, page)
                                        .apply();
                                build(true);
                            }
                        }),
                new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        if (animate) ui.enter(body);
    }

    private void feature(LinearLayout body, String title, String detail, String icon) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(14), 0, ui.dp(14));
        Ui.Icon symbol = ui.icon(icon, title, false, () -> {});
        symbol.setClickable(false);
        symbol.setFocusable(false);
        symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(symbol);
        LinearLayout labels = ui.column();
        labels.setPadding(ui.dp(12), 0, 0, 0);
        labels.addView(ui.label(title, 16, ui.text, true));
        TextView note = ui.label(detail, 13, ui.secondary, false);
        note.setLineSpacing(ui.dp(3), 1);
        ui.add(labels, note, 7);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        ui.add(body, row, 18);
    }

    private void permission(
            LinearLayout parent,
            String title,
            String body,
            String action,
            boolean enabled,
            Runnable click) {
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20));
        card.setBackground(ui.shape(ui.surface, 22));
        card.addView(ui.label(title, 16, ui.text, true));
        TextView note = ui.label(body, 14, ui.secondary, false);
        note.setLineSpacing(ui.dp(4), 1);
        ui.add(card, note, 10);
        Button button = ui.button(action, false, click);
        button.setEnabled(!enabled);
        ui.add(card, button, 18);
        ui.add(parent, card, 20);
    }

    private void complete(boolean guide) {
        acknowledge(this);
        if (page == 3)
            getSharedPreferences("settings", 0).edit().putBoolean("alerts_prompted", true).apply();
        getSharedPreferences("settings", 0).edit().putBoolean("guide_recording", guide).apply();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("guide", guide));
        setResult(RESULT_OK);
        finish();
    }

    private void back() {
        if (page > 0) {
            page--;
            build(true);
        } else complete(false);
    }

    @Override
    public void onBackPressed() {
        back();
    }

    @Override
    protected void onSaveInstanceState(Bundle s) {
        super.onSaveInstanceState(s);
        s.putInt(PAGE, page);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 41) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            getSharedPreferences("settings", 0).edit().putBoolean("contact_names", ok).apply();
        }
        if (request == 45) {
            VoicemailAlerts.ensureScheduled(this);
            VoicemailAlerts.checkSoon(this);
        }
        build(false);
    }
}
