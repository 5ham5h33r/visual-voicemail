package org.visualvoicemail.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.media.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Recording detail: one player, a persistent local transcript, and audio-backed phrase anchors. */
public final class RecordingActivity extends MotionActivity {
    private Ui ui;
    private JSONObject message;
    private String id, find = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean visible, destroyed, prepared, loading, seeking, autoPlay, seekPending;
    private int pendingPosition, request, selectedMatch, activeSegment = -1;
    private Ui.Icon play;
    private SeekBar seek;
    private TextView time, remaining, playerState, transcriptState, matchCount;
    private LinearLayout transcriptCard, transcriptBody, transcriptTools, transcriptStatus, findBox;
    private ScrollView scroll;
    private Button speechAction, cancelSpeech, speed, qualityControl;
    private boolean compactSpeechAction;
    private boolean transcribeAfterDownload;
    private String setupQuality = "";
    private EditText search;
    private Transcript transcript;
    private Transcript original, english;
    private boolean englishVisible;
    private LinearLayout outputTabs;
    private Button originalTab, englishTab;
    private Ui.Icon callControl, outputControl;
    private String playbackProblem = "";
    private final List<TextView> lines = new ArrayList<>();
    private final List<int[]> matches = new ArrayList<>();
    private String lastSpeechState = "";
    private View transcriptHeading, transcriptSearch;
    private Ui.Icon clearFind, findControl;
    private android.window.OnBackInvokedCallback findBack;
    private AppMenu transcriptMenu;
    private AppMenu shareMenu;
    private Ui.Icon shareControl;
    private boolean preparingShare;
    private boolean wasBusy, autoReadAttempted, autoReadPending;
    private final Runnable tick =
            new Runnable() {
                public void run() {
                    if (destroyed) return;
                    updatePlayer();
                    updateSpeech();
                    updateReadStatus();
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
        id = getIntent().getStringExtra("id");
        for (JSONObject item : CarrierClient.get(this).cachedInbox())
            if (item.optString("objectid").equals(id)) {
                message = item;
                break;
            }
        if (message == null) {
            finish();
            return;
        }
        find = state == null ? getIntent().getStringExtra("find") : state.getString("find", "");
        if (find == null) find = "";
        transcribeAfterDownload =
                state != null && state.getBoolean("transcribe_after_download", false);
        setupQuality = state == null ? "" : state.getString("setup_quality", "");
        autoReadAttempted = state != null && state.getBoolean("auto_read_attempted", false);
        autoReadPending = state != null && state.getBoolean("auto_read_pending", false);
        pendingPosition = state == null ? 0 : state.getInt("position", 0);
        selectedMatch = state == null ? 0 : state.getInt("match", 0);
        autoPlay = state == null && getIntent().getBooleanExtra("play", false);
        audio = getSystemService(AudioManager.class);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        focus =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attributes())
                        .setOnAudioFocusChangeListener(
                                change -> {
                                    if (change < 0) pause();
                                },
                                handler)
                        .build();
        englishVisible =
                state == null
                        ? getIntent().getBooleanExtra("english", SpeechOptions.english(this))
                        : state.getBoolean("english", false);
        loadTranscriptOutput();
        build();
        loadAudio();
        if (getSharedPreferences("settings", 0).getBoolean("guide_recording", false))
            handler.postDelayed(this::showGuide, 500);
        if (getSharedPreferences("settings", 0).getBoolean("auto_transcribe", true)
                && Transcription.ready(this)
                && transcript == null
                && !Transcription.busy) Transcription.generate(this, message, englishVisible);
        if (state != null) {
            int y = state.getInt("scroll", 0);
            scroll.post(() -> scroll.scrollTo(0, y));
        }
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
                        v.setPadding(
                                b.left,
                                b.top,
                                b.right,
                                Math.max(
                                        b.bottom,
                                        insets.getInsets(WindowInsets.Type.ime()).bottom));
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
        nav.addView(icon("back", "Back to voicemail", false, this::navigateUp));
        TextView title = ui.label("Voicemail", 17, ui.text, true);
        nav.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        shareControl = icon("share", "Share voicemail", false, () -> showShareOptions());
        nav.addView(shareControl);
        root.addView(nav);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(24));
        String caller = getIntent().getStringExtra("caller");
        if (caller == null || caller.isEmpty()) caller = "Unknown caller";
        TextView name = ui.label(caller, 22, ui.text, true);
        LinearLayout callerRow = ui.row(), callerDetails = ui.column();
        callerDetails.addView(name);
        TextView date =
                ui.label(
                        displayDate()
                                + " · "
                                + duration((int) (message.optDouble("duration") * 1000)),
                        12,
                        ui.secondary,
                        false);
        ui.add(callerDetails, date, 6);
        callerRow.addView(callerDetails, new LinearLayout.LayoutParams(0, -2, 1));
        android.content.Intent callback = callIntent(message);
        callControl =
                icon(
                        "phone",
                        "Call caller",
                        false,
                        () -> {
                            if (callback == null) return;
                            pause();
                            try {
                                startActivity(callback);
                            } catch (android.content.ActivityNotFoundException unavailable) {
                                Toast.makeText(
                                                this,
                                                "No phone app is available to call this number.",
                                                Toast.LENGTH_LONG)
                                        .show();
                            }
                        });
        callControl.setContentDescription("Call " + caller + " in your phone app");
        callControl.setVisibility(callback == null ? View.GONE : View.VISIBLE);
        callerRow.addView(callControl);
        ui.add(content, callerRow, 0);

        LinearLayout card = ui.column();
        card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        card.setBackground(ui.shape(ui.surface, 20));
        seek = new SeekBar(this);
        seek.setProgressTintList(ColorStateList.valueOf(ui.accent));
        seek.setThumbTintList(ColorStateList.valueOf(ui.accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(ui.divider));
        seek.setContentDescription("Recording playback position");
        seek.setKeyProgressIncrement(1000);
        card.addView(seek, new LinearLayout.LayoutParams(-1, ui.dp(44)));
        LinearLayout times = ui.row();
        time = ui.label("0:00", 12, ui.secondary, false);
        remaining =
                ui.label(
                        duration((int) (message.optDouble("duration") * 1000)),
                        12,
                        ui.secondary,
                        false);
        times.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(remaining);
        card.addView(times);
        LinearLayout controls = ui.row();
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, ui.dp(8), 0, ui.dp(8));
        int skip = getSharedPreferences("settings", 0).getInt("skip_seconds", 10);
        controls.addView(
                icon(
                        "rewind" + skip,
                        "Skip back " + skip + " seconds",
                        false,
                        () -> jump(-skip * 1000)));
        play = icon("play", "Play recording", true, this::toggle);
        LinearLayout.LayoutParams center = new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48));
        center.setMargins(ui.dp(8), 0, ui.dp(8), 0);
        controls.addView(play, center);
        controls.addView(
                icon(
                        "forward" + skip,
                        "Skip forward " + skip + " seconds",
                        false,
                        () -> jump(skip * 1000)));
        card.addView(controls);
        LinearLayout tools = ui.row();
        speed = ui.button(speedLabel(), false, this::chooseSpeed);
        speed.setMinWidth(ui.dp(48));
        speed.setMinimumWidth(ui.dp(48));
        speed.setMinHeight(ui.dp(48));
        speed.setTextSize(13);
        speed.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        speed.setBackgroundColor(0);
        speed.setContentDescription("Playback speed, " + speedLabel());
        tools.addView(speed);
        playerState = ui.label("Opening recording…", 12, ui.secondary, false);
        playerState.setGravity(Gravity.CENTER);
        tools.addView(playerState, new LinearLayout.LayoutParams(0, -2, 1));
        outputControl = icon("sound", "Choose audio output", false, this::chooseOutput);
        tools.addView(outputControl);
        if (getResources().getConfiguration().fontScale <= 1.2f
                && getResources().getConfiguration().screenWidthDp >= 360) {
            tools.removeView(speed);
            tools.removeView(outputControl);
            tools.removeView(playerState);
            center.setMargins(0, 0, 0, 0);
            play.setLayoutParams(center);
            // Match the output target so changing speed never shifts the centered Play control.
            speed.setSingleLine(true);
            speed.setPadding(ui.dp(4), 0, ui.dp(4), 0);
            controls.addView(speed, 0, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            controls.addView(new View(this), 1, new LinearLayout.LayoutParams(0, 1, 1));
            controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            controls.addView(outputControl);
            tools.addView(playerState, new LinearLayout.LayoutParams(-1, -2));
        }
        card.addView(tools);
        ui.add(content, card, 16);
        seek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onStartTrackingTouch(SeekBar s) {
                        seeking = true;
                    }

                    public void onProgressChanged(SeekBar s, int p, boolean user) {
                        if (user) time.setText(duration(p));
                    }

                    public void onStopTrackingTouch(SeekBar s) {
                        seeking = false;
                        seekTo(s.getProgress(), false);
                    }
                });
        transcriptCard = ui.column();
        transcriptCard.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12));
        transcriptCard.setBackground(ui.shape(ui.surface, 20));
        ui.add(content, transcriptCard, 16);
        LinearLayout heading = ui.row();
        heading.setPadding(ui.dp(4), 0, 0, 0);
        TextView transcriptTitle = ui.label("Transcript", 18, ui.text, true);
        transcriptHeading = transcriptTitle;
        heading.addView(transcriptTitle, new LinearLayout.LayoutParams(0, -2, 1));
        transcriptSearch =
                icon(
                        "more",
                        "Transcript options",
                        false,
                        () -> showTranscriptOptions(transcriptSearch));
        findControl = icon("search", "Search transcript", false, this::showFind);
        heading.addView(findControl);
        heading.addView(transcriptSearch);
        ui.add(transcriptCard, heading, 0);
        qualityControl =
                ui.button(
                        "",
                        false,
                        () ->
                                startActivity(
                                        new android.content.Intent(
                                                this, TranscriptionSettingsActivity.class)));
        qualityControl.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        qualityControl.setTextSize(13);
        qualityControl.setMinWidth(0);
        qualityControl.setMinimumWidth(0);
        qualityControl.setBackground(
                new android.graphics.drawable.InsetDrawable(
                        ui.buttonBackground(false), 0, ui.dp(6), 0, ui.dp(6)));
        qualityControl.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        if (getResources().getConfiguration().fontScale <= 1.05f
                && getResources().getConfiguration().screenWidthDp >= 360) {
            heading.addView(qualityControl, 1, new LinearLayout.LayoutParams(-2, ui.dp(48)));
        } else transcriptCard.addView(qualityControl, new LinearLayout.LayoutParams(-2, -2));
        outputTabs = ui.row();
        outputTabs.setBackground(ui.shape(ui.bg, 16));
        outputTabs.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        originalTab = ui.button("Original", false, () -> switchOutput(false));
        englishTab = ui.button("English", false, () -> switchOutput(true));
        outputTabs.addView(originalTab, new LinearLayout.LayoutParams(0, -2, 1));
        outputTabs.addView(englishTab, new LinearLayout.LayoutParams(0, -2, 1));
        ui.add(transcriptCard, outputTabs, 6);
        findBox = ui.column();
        LinearLayout findRow = ui.row();
        findRow.setBackground(ui.shape(ui.bg, 14));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(ui.text);
        search.setHintTextColor(ui.secondary);
        search.setHint("Find a word or phrase");
        search.setContentDescription("Find in transcript");
        search.setBackgroundColor(0);
        search.setPadding(ui.dp(12), 0, 0, 0);
        search.setMinHeight(ui.dp(50));
        search.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(
                EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setText(find);
        findRow.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        clearFind = icon("close", "Clear transcript search", false, () -> search.setText(""));
        clearFind.setVisibility(find.isEmpty() ? View.GONE : View.VISIBLE);
        findRow.addView(clearFind);
        findBox.addView(findRow);
        LinearLayout findNav = ui.row();
        matchCount = ui.label("", 13, ui.secondary, false);
        matchCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        findNav.addView(matchCount, new LinearLayout.LayoutParams(0, -2, 1));
        findNav.addView(icon("back", "Previous transcript match", false, () -> navigate(-1)));
        findNav.addView(icon("next", "Next transcript match", false, () -> navigate(1)));
        findBox.addView(findNav);
        findBox.setVisibility(find.isEmpty() ? View.GONE : View.VISIBLE);
        ui.add(transcriptCard, findBox, 10);
        search.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

                    public void afterTextChanged(Editable e) {}

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        find = s.toString();
                        clearFind.setVisibility(find.isEmpty() ? View.GONE : View.VISIBLE);
                        selectedMatch = 0;
                        rebuildMatches();
                        styleLines();
                    }
                });
        transcriptState = ui.label("", 12, ui.secondary, false);
        transcriptState.setLineSpacing(ui.dp(3), 1);
        transcriptState.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        transcriptStatus = ui.column();
        transcriptStatus.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        ui.add(transcriptStatus, transcriptState, 0);
        transcriptTools = ui.column();
        speechAction = ui.button("", true, this::speechAction);
        ui.add(transcriptTools, speechAction, 14);
        cancelSpeech =
                ui.button(
                        "Cancel",
                        false,
                        () -> {
                            Transcription.cancel();
                            updateSpeech();
                        });
        ui.add(transcriptTools, cancelSpeech, 10);
        ui.add(transcriptStatus, transcriptTools, 0);
        ui.add(transcriptCard, transcriptStatus, 4);
        transcriptBody = ui.column();
        ui.add(transcriptCard, transcriptBody, 4);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        showTranscript();
        if (!find.isEmpty()) bindFindBack();
        updateSpeech();
        ui.enter(content);
    }

    private void showTranscript() {
        if (outputTabs != null) {
            outputTabs.setVisibility(english != null || englishVisible ? View.VISIBLE : View.GONE);
            originalTab.setSelected(!englishVisible);
            englishTab.setSelected(englishVisible);
            originalTab.setTextColor(!englishVisible ? ui.accent : ui.secondary);
            englishTab.setTextColor(englishVisible ? ui.accent : ui.secondary);
            originalTab.setBackground(ui.shape(!englishVisible ? ui.tint : 0, 14));
            englishTab.setBackground(ui.shape(englishVisible ? ui.tint : 0, 14));
            originalTab.setContentDescription(
                    "Original language transcript" + (!englishVisible ? ", selected" : ""));
            englishTab.setContentDescription(
                    "English translation" + (englishVisible ? ", selected" : ""));
        }
        transcriptBody.removeAllViews();
        lines.clear();
        if (transcript != null) {
            for (int i = 0; i < transcript.segments.size(); i++) {
                Transcript.Segment s = transcript.segments.get(i);
                TextView line = ui.label("", 15, ui.text, false);
                line.setLineSpacing(ui.dp(3), 1);
                line.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8));
                line.setMinHeight(ui.dp(48));
                line.setContentDescription(
                        (s.start >= 0
                                        ? "Play phrase from " + duration(s.start) + ". "
                                        : "Timing unavailable. ")
                                + s.text);
                line.setFocusable(true);
                AppMotion.press(line);
                line.setOnClickListener(
                        v -> {
                            if (s.start >= 0) seekTo(s.start, true);
                            else
                                Toast.makeText(
                                                this,
                                                "Timing unavailable. Use the player to listen.",
                                                Toast.LENGTH_SHORT)
                                        .show();
                        });
                ui.add(transcriptBody, line, 4);
                lines.add(line);
            }
        }
        rebuildMatches();
        styleLines();
        if (!matches.isEmpty()) revealMatch();
    }

    private void loadTranscriptOutput() {
        original = Transcription.cached(this, id);
        english = Transcription.cachedEnglish(this, id);
        transcript = englishVisible ? english : original;
    }

    static android.content.Intent callIntent(JSONObject message) {
        String raw = message.optString("returnnumber", "").replaceFirst("(?i)^tel:", "").trim();
        if (!raw.matches("[+()0-9 .-]+")) return null;
        String number = android.telephony.PhoneNumberUtils.normalizeNumber(raw);
        if (!number.matches("\\+?[0-9]{3,20}")) return null;
        return new android.content.Intent(
                android.content.Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number, null));
    }

    private void showShareOptions() {
        if (shareMenu != null) shareMenu.dismiss();
        shareMenu = new AppMenu(this, shareControl);
        shareMenu
                .getMenu()
                .add(0, 1, 0, "Transcript")
                .setEnabled(
                        (original != null && !original.segments.isEmpty())
                                || (english != null && !english.segments.isEmpty()));
        shareMenu.getMenu().add(0, 2, 1, "Voice recording").setEnabled(!preparingShare);
        shareMenu.setOnMenuItemClickListener(
                item -> {
                    if (item.getItemId() == 2) shareAudio();
                    else if (original != null
                            && !original.segments.isEmpty()
                            && english != null
                            && !english.segments.isEmpty()) {
                        final Transcript source = original, translated = english;
                        ui.show(
                                new AlertDialog.Builder(this)
                                        .setTitle("Share transcript")
                                        .setItems(
                                                new String[] {
                                                    "Original language", "English translation"
                                                },
                                                (d, which) ->
                                                        startShare(
                                                                ShareIntents.transcript(
                                                                        which == 0
                                                                                ? source
                                                                                : translated)))
                                        .setNegativeButton("Cancel", null)
                                        .create());
                    } else
                        startShare(
                                ShareIntents.transcript(
                                        original != null && !original.segments.isEmpty()
                                                ? original
                                                : english));
                    return true;
                });
        shareMenu.show();
    }

    private void shareAudio() {
        if (preparingShare) return;
        preparingShare = true;
        shareControl.setEnabled(false);
        Toast.makeText(this, "Preparing recording to share…", Toast.LENGTH_SHORT).show();
        worker.execute(
                () -> {
                    try {
                        android.net.Uri uri =
                                ShareProvider.prepare(
                                        this,
                                        CarrierClient.get(this).availableAudio(message),
                                        message.optString("date"));
                        String type = getContentResolver().getType(uri);
                        android.content.Intent share = ShareIntents.audio(uri, type);
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    preparingShare = false;
                                    shareControl.setEnabled(true);
                                    startShare(share);
                                });
                    } catch (Exception failure) {
                        handler.post(
                                () -> {
                                    if (destroyed) return;
                                    preparingShare = false;
                                    shareControl.setEnabled(true);
                                    Toast.makeText(
                                                    this,
                                                    "Couldn't prepare this recording. Check your"
                                                        + " connection and free space, then retry.",
                                                    Toast.LENGTH_LONG)
                                            .show();
                                });
                    }
                });
    }

    private void startShare(android.content.Intent payload) {
        pause();
        try {
            startActivity(android.content.Intent.createChooser(payload, "Share voicemail"));
        } catch (android.content.ActivityNotFoundException unavailable) {
            Toast.makeText(this, "No app is available to share this content.", Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void switchOutput(boolean translated) {
        englishVisible = translated;
        selectedMatch = 0;
        activeSegment = -1;
        transcript = englishVisible ? english : original;
        showTranscript();
        lastSpeechState = "";
        updateSpeech();
        AppMotion.reveal(transcriptBody, 140, 2);
    }

    private void showFind() {
        AppMotion.visibility(findBox, true);
        search.requestFocus();
        getSystemService(android.view.inputmethod.InputMethodManager.class)
                .showSoftInput(search, 0);
        bindFindBack();
    }

    private void bindFindBack() {
        if (Build.VERSION.SDK_INT >= 33 && findBack == null) {
            findBack = () -> closeFind();
            getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, findBack);
        }
    }

    private boolean closeFind() {
        if (findBox.getVisibility() != View.VISIBLE) return false;
        search.setText("");
        search.clearFocus();
        getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
        AppMotion.visibility(findBox, false);
        if (Build.VERSION.SDK_INT >= 33 && findBack != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(findBack);
            findBack = null;
        }
        findControl.requestFocus();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!closeFind()) super.onBackPressed();
    }

    private void showTranscriptOptions(View anchor) {
        if (transcriptMenu != null) transcriptMenu.dismiss();
        AppMenu menu = new AppMenu(this, anchor);
        transcriptMenu = menu;
        menu.getMenu()
                .add(0, 0, 0, "Find in transcript")
                .setEnabled(transcript != null && !transcript.segments.isEmpty());
        menu.getMenu()
                .add(
                        0,
                        1,
                        0,
                        "Language · "
                                + SpeechOptions.label(
                                        SpeechOptions.language(
                                                this, Transcription.selected(this))));
        menu.getMenu().add(0, 2, 1, "Transcription quality");
        menu.getMenu()
                .add(
                        0,
                        3,
                        2,
                        english == null ? "Translate to English" : "Update English translation")
                .setEnabled(!Transcription.busy);
        menu.getMenu()
                .add(
                        0,
                        4,
                        3,
                        original == null
                                ? "Generate original transcript"
                                : "Update original transcript")
                .setEnabled(!Transcription.busy);
        menu.setOnMenuItemClickListener(
                item -> {
                    if (item.getItemId() == 0) {
                        showFind();
                    } else if (item.getItemId() == 1)
                        SpeechOptions.chooseLanguage(
                                this,
                                () -> {
                                    lastSpeechState = "";
                                    updateSpeech();
                                });
                    else if (item.getItemId() == 2)
                        startActivity(
                                new android.content.Intent(
                                        this, TranscriptionSettingsActivity.class));
                    else if (item.getItemId() == 3)
                        SpeechOptions.requireMultilingual(
                                this,
                                () -> {
                                    switchOutput(true);
                                    speechAction();
                                });
                    else {
                        switchOutput(false);
                        speechAction();
                    }
                    return true;
                });
        menu.show();
    }

    private void rebuildMatches() {
        matches.clear();
        if (transcript != null) matches.addAll(TranscriptSearch.find(transcript.text(), find));
        selectedMatch = Math.max(0, Math.min(selectedMatch, matches.size() - 1));
        matchCount.setText(
                find.trim().isEmpty()
                        ? "Search this transcript"
                        : transcript == null
                                ? "Transcript not available"
                                : matches.isEmpty()
                                        ? "No matches"
                                        : (selectedMatch + 1) + " of " + matches.size());
    }

    private void styleLines() {
        if (transcript == null) return;
        int offset = 0;
        for (int i = 0; i < lines.size(); i++) {
            Transcript.Segment segment = transcript.segments.get(i);
            String prefix = (segment.start >= 0 ? duration(segment.start) : "—") + "\n";
            SpannableString value = new SpannableString(prefix + segment.text);
            value.setSpan(
                    new RelativeSizeSpan(.76f),
                    0,
                    prefix.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(
                    new ForegroundColorSpan(ui.secondary),
                    0,
                    prefix.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            for (int j = 0; j < matches.size(); j++) {
                int[] m = matches.get(j);
                int a = Math.max(0, m[0] - offset),
                        b = Math.min(segment.text.length(), m[1] - offset);
                if (b > a) {
                    value.setSpan(
                            new UnderlineSpan(), prefix.length() + a, prefix.length() + b, 33);
                    value.setSpan(
                            new StyleSpan(android.graphics.Typeface.BOLD),
                            prefix.length() + a,
                            prefix.length() + b,
                            33);
                    if (j == selectedMatch)
                        value.setSpan(
                                new BackgroundColorSpan(ui.tint),
                                prefix.length() + a,
                                prefix.length() + b,
                                33);
                }
            }
            TextView line = lines.get(i);
            line.setText(value);
            line.setBackground(ui.shape(i == activeSegment ? ui.tint : 0, 12));
            line.setSelected(i == activeSegment);
            line.setTypeface(
                    android.graphics.Typeface.create(
                            i == activeSegment ? "sans-serif-medium" : "sans-serif", 0));
            if (Build.VERSION.SDK_INT >= 30)
                line.setStateDescription(i == activeSegment ? "Current audio phrase" : "");
            offset += segment.text.length() + 1;
        }
    }

    private void navigate(int delta) {
        if (matches.isEmpty()) return;
        selectedMatch = (selectedMatch + delta + matches.size()) % matches.size();
        matchCount.setText((selectedMatch + 1) + " of " + matches.size());
        styleLines();
        revealMatch();
    }

    private void revealMatch() {
        if (transcript == null || matches.isEmpty()) return;
        int offset = 0, target = matches.get(selectedMatch)[0];
        for (int i = 0; i < lines.size(); i++) {
            int end = offset + transcript.segments.get(i).text.length();
            if (target <= end) {
                TextView line = lines.get(i);
                line.post(
                        () ->
                                line.requestRectangleOnScreen(
                                        new android.graphics.Rect(
                                                0, 0, line.getWidth(), line.getHeight()),
                                        false));
                return;
            }
            offset = end + 1;
        }
    }

    private void updateSpeech() {
        TranscriptionQuality quality = Transcription.selected(this);
        boolean ready = Transcription.ready(this);
        String state =
                quality.key
                        + ready
                        + Transcription.busy
                        + "|"
                        + Transcription.job
                        + "|"
                        + Transcription.status
                        + "|"
                        + Transcription.failure
                        + "|"
                        + Transcription.cancelled
                        + englishVisible
                        + SpeechOptions.language(this, quality);
        if (state.equals(lastSpeechState)) return;
        lastSpeechState = state;
        qualityControl.setText(quality.title + "  ›");
        qualityControl.setContentDescription(
                "Transcription quality. " + quality.label() + ". Change quality");
        if (transcribeAfterDownload
                && (!quality.key.equals(setupQuality)
                        || (!Transcription.busy && Transcription.cancelled)))
            transcribeAfterDownload = false;
        if (wasBusy && !Transcription.busy) {
            if (!Transcription.failure.isEmpty()) transcribeAfterDownload = false;
            loadTranscriptOutput();
            showTranscript();
            if (id.equals(Transcription.job) && Transcription.failure.isEmpty())
                AppMotion.reveal(transcriptBody, 180, 6);
        }
        if (transcribeAfterDownload
                && !Transcription.busy
                && ready
                && Transcription.failure.isEmpty()) {
            transcribeAfterDownload = false;
            Transcription.generate(this, message, englishVisible);
        }
        wasBusy = Transcription.busy;
        boolean own =
                Transcription.busy && (Transcription.downloading || id.equals(Transcription.job));
        boolean searchable = transcript != null && !transcript.segments.isEmpty();
        styleSpeechAction(searchable);
        findControl.setEnabled(searchable);
        findControl.setContentDescription(
                searchable
                        ? "Search transcript"
                        : "Search transcript unavailable until text is generated");
        boolean changed =
                transcript != null
                        && (!quality.key.equals(transcript.quality)
                                || !SpeechOptions.language(this, quality)
                                        .equals(transcript.language));
        String work =
                Transcription.cancelled
                        ? Transcription.downloading
                                ? "Pausing download..."
                                : "Stopping after the current phrase..."
                        : Transcription.status;
        String problem =
                (!Transcription.failure.isEmpty()
                                && (id.equals(Transcription.job)
                                        || Transcription.job.equals("model")))
                        ? Transcription.failure
                        : "";
        if (transcript != null) {
            String saved = TranscriptionQuality.forKey(transcript.quality).title;
            transcriptState.setText(
                    own
                            ? work
                            : !problem.isEmpty()
                                    ? problem + " Your previous transcript is kept."
                                    : transcript.segments.isEmpty()
                                            ? "No speech detected. You can still listen."
                                            : changed
                                                    ? quality.key.equals(transcript.quality)
                                                            ? "Language changed: "
                                                                    + SpeechOptions.label(
                                                                            SpeechOptions.language(
                                                                                    this, quality))
                                                                    + "."
                                                            : "Saved with " + saved
                                                    : englishVisible
                                                            ? "English translation · Tap a phrase"
                                                                    + " to hear the source audio."
                                                                    + " Timing is approximate."
                                                            : "Tap a phrase to play · Approximate"
                                                                    + " timing");
            transcriptTools.setVisibility(changed || own ? View.VISIBLE : View.GONE);
        } else {
            transcriptTools.setVisibility(View.VISIBLE);
            transcriptState.setText(
                    own
                            ? work
                            : !problem.isEmpty()
                                    ? problem
                                    : ready
                                            ? englishVisible
                                                    ? "Translate to English on this phone. Your"
                                                            + " original transcript is kept."
                                                    : "Create a transcript privately on this phone."
                                            : "Download "
                                                    + quality.label()
                                                    + " once ("
                                                    + quality.sizeLabel()
                                                    + "). Recordings stay on this phone.");
            if (Transcription.busy && !own)
                transcriptState.setText(
                        "Another recording is being transcribed. You can listen while it"
                                + " finishes.");
        }
        speechAction.setText(
                own
                        ? "Working…"
                        : transcript != null
                                ? englishVisible
                                        ? "Update English translation"
                                        : "Update transcript"
                                : ready
                                        ? englishVisible
                                                ? "Translate to English"
                                                : "Transcribe recording"
                                        : Transcription.downloadedBytes(this, quality) > 0
                                                ? "Resume model setup"
                                                : "Set up transcription");
        speechAction.setEnabled(!Transcription.busy);
        cancelSpeech.setText(Transcription.downloading ? "Pause download" : "Stop transcription");
        cancelSpeech.setVisibility(own ? View.VISIBLE : View.GONE);
        cancelSpeech.setEnabled(!Transcription.cancelled);
        transcriptState.setVisibility(
                transcript != null
                                && !transcript.segments.isEmpty()
                                && !changed
                                && !own
                                && problem.isEmpty()
                                && !englishVisible
                        ? View.GONE
                        : View.VISIBLE);
        boolean inline =
                searchable
                        && changed
                        && !own
                        && problem.isEmpty()
                        && getResources().getConfiguration().fontScale <= 1.05f
                        && getResources().getConfiguration().screenWidthDp >= 360;
        transcriptStatus.setOrientation(inline ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        transcriptStatus.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams stateSize =
                (LinearLayout.LayoutParams) transcriptState.getLayoutParams();
        stateSize.width = inline ? 0 : -1;
        stateSize.weight = inline ? 1 : 0;
        transcriptState.setLayoutParams(stateSize);
        LinearLayout.LayoutParams toolsSize =
                (LinearLayout.LayoutParams) transcriptTools.getLayoutParams();
        toolsSize.width = inline ? -2 : -1;
        toolsSize.setMarginStart(inline ? ui.dp(8) : 0);
        transcriptTools.setLayoutParams(toolsSize);
        transcriptStatus.setVisibility(
                transcriptState.getVisibility() == View.GONE
                                && transcriptTools.getVisibility() == View.GONE
                        ? View.GONE
                        : View.VISIBLE);
        speechAction.setAlpha(Transcription.busy ? .5f : 1f);
    }

    private void styleSpeechAction(boolean compact) {
        if (compact == compactSpeechAction) return;
        compactSpeechAction = compact;
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) speechAction.getLayoutParams();
        params.width = compact ? -2 : -1;
        params.gravity = compact ? Gravity.END : Gravity.FILL_HORIZONTAL;
        params.topMargin = ui.dp(compact ? 0 : 14);
        speechAction.setLayoutParams(params);
        speechAction.setTextSize(compact ? 13 : Ui.BODY);
        speechAction.setTextColor(compact ? ui.accent : ui.bg);
        speechAction.setBackground(
                compact
                        ? new android.graphics.drawable.InsetDrawable(
                                ui.buttonBackground(false), 0, ui.dp(6), 0, ui.dp(6))
                        : ui.buttonBackground(true));
        speechAction.setPadding(
                ui.dp(compact ? 12 : 16), ui.dp(10), ui.dp(compact ? 12 : 16), ui.dp(10));
    }

    private void speechAction() {
        if (englishVisible && Transcription.selected(this) != TranscriptionQuality.HIGHEST) {
            SpeechOptions.requireMultilingual(this, this::speechAction);
            return;
        }
        if (Transcription.ready(this)) {
            Transcription.generate(this, message, englishVisible);
            updateSpeech();
            return;
        }
        TranscriptionQuality quality = Transcription.selected(this);
        ui.show(
                new AlertDialog.Builder(this)
                        .setTitle(Transcription.downloadAction(this) + "?")
                        .setMessage(
                                quality.label()
                                        + " needs a one-time "
                                        + quality.sizeLabel()
                                        + " download from its public model host. Wi-Fi is"
                                        + " recommended. Audio and transcripts stay on this phone."
                                        + " Listening remains available."
                                        + (quality == TranscriptionQuality.HIGHEST
                                                ? "\n\n"
                                                      + "Allow about 2 GB of free storage for"
                                                      + " setup. Large v3 uses several GB of memory"
                                                      + " and can take minutes per recording. Keep"
                                                      + " the app open."
                                                : ""))
                        .setPositiveButton(
                                Transcription.downloadAction(this),
                                (d, w) -> {
                                    transcribeAfterDownload = true;
                                    setupQuality = quality.key;
                                    Transcription.download(this);
                                    updateSpeech();
                                })
                        .setNeutralButton(
                                "Change quality",
                                (d, w) ->
                                        startActivity(
                                                new android.content.Intent(
                                                        this, TranscriptionSettingsActivity.class)))
                        .setNegativeButton("Not now", null)
                        .create());
    }

    private void loadAudio() {
        if (loading || prepared) return;
        loading = true;
        playbackProblem = "";
        final int version = ++request;
        updatePlayer();
        worker.execute(
                () -> {
                    try {
                        final File file = CarrierClient.get(this).availableAudio(message);
                        handler.post(
                                () -> {
                                    if (destroyed || version != request) return;
                                    prepare(file);
                                });
                    } catch (Exception e) {
                        handler.post(
                                () -> {
                                    if (destroyed || version != request) return;
                                    loading = false;
                                    playerProblem("Couldn't open audio. Tap Play to retry.");
                                    play.setEnabled(true);
                                });
                    }
                });
    }

    private void prepare(File file) {
        try {
            player = new MediaPlayer();
            final MediaPlayer p = player;
            p.setAudioAttributes(attributes());
            p.setDataSource(file.getAbsolutePath());
            p.setOnPreparedListener(
                    ready -> {
                        if (destroyed || player != ready) return;
                        prepared = true;
                        loading = false;
                        seek.setMax(ready.getDuration());
                        if (pendingPosition > 0) seekTo(pendingPosition, autoPlay);
                        else if (autoPlay) start();
                        else updatePlayer();
                    });
            p.setOnSeekCompleteListener(
                    ready -> {
                        if (player != ready) return;
                        if (Math.abs(ready.getCurrentPosition() - pendingPosition) > 180) {
                            ready.seekTo(pendingPosition, MediaPlayer.SEEK_CLOSEST);
                            return;
                        }
                        seekPending = false;
                        if (autoPlay) {
                            autoPlay = false;
                            start();
                        }
                        updatePlayer();
                    });
            p.addOnRoutingChangedListener(
                    routing -> {
                        if (player != p || !prepared || !p.isPlaying()) return;
                        int setting = getSharedPreferences("settings", 0).getInt("audio_output", 0);
                        AudioDeviceInfo actual = p.getRoutedDevice();
                        if (actual != null
                                && ((setting == 2
                                                && actual.getType()
                                                        != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                                        || (setting == 1
                                                && actual.getType()
                                                        != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                                                && actual.getType()
                                                        != AudioDeviceInfo
                                                                .TYPE_BUILTIN_SPEAKER_SAFE))) {
                            pause();
                            playerProblem("Selected output unavailable. Choose another output.");
                        }
                    },
                    handler);
            p.setOnCompletionListener(
                    done -> {
                        audio.abandonAudioFocusRequest(focus);
                        updatePlayer();
                    });
            p.setOnErrorListener(
                    (failed, a, b) -> {
                        release();
                        playerProblem("Couldn't play audio. Tap Play to retry.");
                        return true;
                    });
            p.prepareAsync();
        } catch (Exception e) {
            release();
            playerProblem("Couldn't open audio. Tap Play to retry.");
        }
    }

    private void toggle() {
        if (!prepared) {
            autoPlay = true;
            loadAudio();
            return;
        }
        if (player.isPlaying()) pause();
        else start();
    }

    private void start() {
        if (!prepared || !visible) return;
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            playerProblem("Audio is in use. Try again.");
            return;
        }
        try {
            int setting = getSharedPreferences("settings", 0).getInt("audio_output", 0),
                    type =
                            setting == 1
                                    ? AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                                    : setting == 2 ? AudioDeviceInfo.TYPE_BUILTIN_EARPIECE : 0;
            AudioDeviceInfo preferred = null;
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                if (device.getType() == type) preferred = device;
            if ((type != 0 && preferred == null) || !player.setPreferredDevice(preferred)) {
                audio.abandonAudioFocusRequest(focus);
                playerProblem("Choose an available audio output.");
                return;
            }
            playbackProblem = "";
            if (player.getCurrentPosition() >= player.getDuration() - 80) player.seekTo(0);
            player.setPlaybackParams(
                    new PlaybackParams().setSpeed(SettingsActivity.speed(this)).setPitch(1));
            player.start();
            markReadAfterPlayback();
            autoPlay = false;
            updatePlayer();
        } catch (RuntimeException e) {
            pause();
            playerProblem("Playback couldn't start. Try 1× speed.");
        }
    }

    private void markReadAfterPlayback() {
        if (autoReadAttempted
                || InboxQuery.isRead(message.optString("flag"))
                || !getSharedPreferences("settings", 0).getBoolean("read_on_play", false)) return;
        autoReadAttempted = true;
        autoReadPending = MailboxBatch.read(this, java.util.Collections.singletonList(id), true);
        if (!autoReadPending)
            Toast.makeText(
                            this,
                            "Read status kept. Another mailbox update is running; use inbox actions"
                                + " to retry.",
                            Toast.LENGTH_LONG)
                    .show();
    }

    private void updateReadStatus() {
        if (!autoReadPending || MailboxBatch.busy) return;
        autoReadPending = false;
        boolean confirmed = false;
        for (JSONObject item : CarrierClient.get(this).cachedInbox())
            if (id.equals(item.optString("objectid"))) {
                confirmed = InboxQuery.isRead(item.optString("flag"));
                if (confirmed) message = item;
                break;
            }
        if (visible)
            Toast.makeText(
                            this,
                            confirmed
                                    ? "Marked as read"
                                    : "Read status not confirmed. Refresh the inbox before"
                                          + " retrying.",
                            Toast.LENGTH_LONG)
                    .show();
    }

    private void pause() {
        autoPlay = false;
        if (player != null && prepared && player.isPlaying()) player.pause();
        if (audio != null) audio.abandonAudioFocusRequest(focus);
        if (play != null) updatePlayer();
    }

    private void seekTo(int position, boolean start) {
        pendingPosition = position;
        autoPlay = start;
        if (!prepared) {
            loadAudio();
            return;
        }
        int duration = player.getDuration();
        if (position < 0 || position >= duration) {
            autoPlay = false;
            Toast.makeText(
                            this,
                            "That phrase's timing is unavailable. Use the player to listen.",
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        pendingPosition = position;
        if (!seekPending) {
            seekPending = true;
            player.seekTo(position, MediaPlayer.SEEK_CLOSEST);
        }
        updatePlayer();
    }

    private void jump(int amount) {
        if (prepared)
            seekTo(
                    Math.max(
                            0,
                            Math.min(
                                    player.getDuration() - 1,
                                    player.getCurrentPosition() + amount)),
                    false);
    }

    private void updatePlayer() {
        if (play == null) return;
        boolean playing = prepared && player != null && player.isPlaying();
        play.name = playing ? "pause" : "play";
        play.invalidate();
        play.setContentDescription(playing ? "Pause recording" : "Play recording");
        play.setEnabled(!loading);
        seek.setEnabled(prepared);
        if (prepared) {
            int position = player.getCurrentPosition();
            if (!seeking) seek.setProgress(position);
            time.setText(duration(position));
            remaining.setText("−" + duration(Math.max(0, player.getDuration() - position)));
            AudioDeviceInfo route = player.getRoutedDevice();
            String output =
                    route == null
                            ? "Ready to listen"
                            : route.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                                    ? "Phone earpiece"
                                    : route.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                                            ? "Phone speaker"
                                            : "Device audio";
            outputControl.setContentDescription("Audio output · " + output);
            playerState.setText(playbackProblem);
            playerState.setVisibility(playbackProblem.isEmpty() ? View.GONE : View.VISIBLE);
            int active = -1;
            if (transcript != null)
                for (int i = 0; i < transcript.segments.size(); i++) {
                    Transcript.Segment s = transcript.segments.get(i);
                    if (s.start >= 0 && position >= s.start && position < s.end) {
                        active = i;
                        break;
                    }
                }
            if (active != activeSegment) {
                activeSegment = active;
                styleLines();
            }
        } else if (loading) {
            playerState.setText("Opening recording…");
            playerState.setVisibility(View.VISIBLE);
        }
    }

    private Ui.Icon icon(String name, String label, boolean solid, Runnable click) {
        Ui.Icon control = ui.icon(name, label, solid, click);
        control.glyphSize(20);
        return control;
    }

    private void playerProblem(String message) {
        playbackProblem = message;
        playerState.setText(message);
        playerState.setVisibility(View.VISIBLE);
    }

    private void chooseSpeed() {
        String[] names = {"0.75×", "1×", "1.25×", "1.5×", "2×"};
        ui.show(
                new AlertDialog.Builder(this)
                        .setTitle("Playback speed")
                        .setSingleChoiceItems(
                                names,
                                getSharedPreferences("settings", 0).getInt("playback_speed", 1),
                                (d, w) -> {
                                    getSharedPreferences("settings", 0)
                                            .edit()
                                            .putInt("playback_speed", w)
                                            .apply();
                                    speed.setText(speedLabel());
                                    speed.setContentDescription("Playback speed, " + speedLabel());
                                    if (prepared && player.isPlaying()) start();
                                    d.dismiss();
                                })
                        .create());
    }

    private void chooseOutput() {
        ui.show(
                new AlertDialog.Builder(this)
                        .setTitle("Audio output")
                        .setSingleChoiceItems(
                                new String[] {"Follow device", "Phone speaker", "Phone earpiece"},
                                getSharedPreferences("settings", 0).getInt("audio_output", 0),
                                (d, w) -> {
                                    getSharedPreferences("settings", 0)
                                            .edit()
                                            .putInt("audio_output", w)
                                            .apply();
                                    if (prepared && player.isPlaying()) start();
                                    d.dismiss();
                                })
                        .create());
    }

    private void showGuide() {
        if (!visible || destroyed || isFinishing()) return;
        List<GuideOverlay.Tip> tips = new ArrayList<>();
        tips.add(
                new GuideOverlay.Tip(
                        play,
                        "Listen at your pace",
                        "Play and pause here. Use the progress bar or the small skip controls to"
                                + " move through the recording.",
                        false));
        tips.add(
                new GuideOverlay.Tip(
                        transcriptHeading,
                        "Read along",
                        "Set up a speech model once. Transcripts stay on this phone, and tapping"
                                + " a phrase plays that part of the audio.",
                        false));
        tips.add(
                new GuideOverlay.Tip(
                        findControl,
                        "Find the detail",
                        "Tap Search transcript to find a word or phrase, then use previous and"
                                + " next to move between matches. Searching won't start playback.",
                        false));
        tips.add(
                new GuideOverlay.Tip(
                        shareControl,
                        "Share a copy",
                        "Choose Transcript or Voice recording, then choose where to share. If an"
                            + " English translation is saved, you can share either text version.",
                        false));
        GuideOverlay.show(
                this,
                tips,
                () ->
                        getSharedPreferences("settings", 0)
                                .edit()
                                .putBoolean("guide_recording", false)
                                .apply());
    }

    private String speedLabel() {
        return String.valueOf(SettingsActivity.speed(this)).replace(".0", "") + "×";
    }

    private String displayDate() {
        try {
            return java.time.OffsetDateTime.parse(message.optString("date"))
                    .atZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(
                            java.time.format.DateTimeFormatter.ofLocalizedDateTime(
                                    java.time.format.FormatStyle.MEDIUM,
                                    java.time.format.FormatStyle.SHORT));
        } catch (Exception e) {
            return "Date unavailable";
        }
    }

    private static String duration(int ms) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", ms / 60000, (ms / 1000) % 60);
    }

    private AudioAttributes attributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private void release() {
        request++;
        loading = false;
        prepared = false;
        seekPending = false;
        if (player != null) {
            player.release();
            player = null;
        }
        if (audio != null) audio.abandonAudioFocusRequest(focus);
        if (play != null) {
            play.setEnabled(true);
            play.name = "play";
            play.invalidate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        handler.post(tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        loadTranscriptOutput();
        showTranscript();
        lastSpeechState = "";
        if (transcript == null
                && !Transcription.busy
                && Transcription.ready(this)
                && getSharedPreferences("settings", 0).getBoolean("auto_transcribe", true)
                && (!id.equals(Transcription.job) || Transcription.failure.isEmpty()))
            Transcription.generate(this, message, englishVisible);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(tick);
        pause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        visible = false;
        handler.removeCallbacks(tick);
        pause();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle s) {
        super.onSaveInstanceState(s);
        s.putBoolean("auto_read_attempted", autoReadAttempted);
        s.putBoolean("auto_read_pending", autoReadPending);
        s.putString("find", find);
        s.putBoolean("english", englishVisible);
        s.putBoolean("transcribe_after_download", transcribeAfterDownload);
        s.putString("setup_quality", setupQuality);
        s.putInt("match", selectedMatch);
        s.putInt("position", prepared ? player.getCurrentPosition() : pendingPosition);
        s.putInt("scroll", scroll.getScrollY());
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (transcriptMenu != null) transcriptMenu.dismiss();
        if (shareMenu != null) shareMenu.dismiss();
        handler.removeCallbacksAndMessages(null);
        release();
        worker.shutdown();
        super.onDestroy();
    }
}
