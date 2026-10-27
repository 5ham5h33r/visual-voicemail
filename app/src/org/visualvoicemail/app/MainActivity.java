package org.visualvoicemail.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.CancellationSignal;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.PlaybackParams;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Original native inbox. No dialer role or transcription. */
public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService contactWorker = Executors.newSingleThreadExecutor();
    private Map<String, String> contactNames = new HashMap<>();
    private CancellationSignal contactSignal;
    private int contactGeneration;
    private boolean contactsLoading;
    private int displayedAppearance, pendingSettingsAction, requestedOutput;
    private boolean clearingDownloads;
    private static final int SETTINGS_REQUEST = 42;
    private static final int DOWNLOAD_FOLDER_REQUEST = 44;
    private static final int ALERT_PERMISSION = 45;
    private static final int TUTORIAL_REQUEST = 46;
    private boolean tutorialOpen;
    private String pendingSaveId = "";
    private boolean savingCopy;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, MessageRow> rows = new HashMap<>();
    private List<JSONObject> inbox = new ArrayList<>();
    private CarrierClient carrier;
    private MessageMarks marks;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private MediaPlayer player;
    private LinearLayout messages;
    private TextView status;
    private Button refresh;
    private EditText search;
    private View headerBrand, headerTitle;
    private TextView carrierName;
    private TextView section;
    private final Button[] filters = new Button[3];
    private String query = "";
    private int filter;
    private String activeId = "";
    private int generation;
    private boolean loading, prepared, visible, syncing, destroyed, updatingRead;
    private int background, surface, primary, secondary, accent, accentSurface, divider, error;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updatePlayback();
            if (visible && !destroyed) handler.postDelayed(this, 250);
        }
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppAppearance.context(base));
    }

    @Override public void onCreate(Bundle state) {
        displayedAppearance = AppAppearance.mode(this);
        AppAppearance.apply(this);
        super.onCreate(state);
        background = AppAppearance.background(this); surface = color(R.color.surface);
        primary = color(R.color.primary_text); secondary = color(R.color.secondary_text);
        accent = color(R.color.accent); accentSurface = color(R.color.accent_surface);
        divider = color(R.color.divider); error = color(R.color.error);
        carrier = CarrierClient.get(this);
        marks = new MessageMarks(this);
        if (state != null) { query = state.getString("query", ""); filter = state.getInt("filter", 0); pendingSettingsAction = state.getInt("settings_action", 0); pendingSaveId=state.getString("pending_save",""); }
        audio = getSystemService(AudioManager.class);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change -> {
                if (change < 0) pause("focus_lost");
            }, handler).build();
        buildLayout();
        inbox = carrier.cachedInbox();
        render();
        if (!inbox.isEmpty()) status.setText(summary() + " · " + freshness());
        else if (carrier.hasSession()) status.setText("Refresh to check your mailbox");
        else status.setText("Connect to your Mint Mobile mailbox");
        refresh.setText(carrier.hasSession() ? "Refresh" : "Connect");
        openNotification(getIntent());
    }

    private void buildLayout() {
        LinearLayout root = column();
        root.setBackgroundColor(background);
        root.setPadding(dp(24), dp(24), dp(24), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right;
                bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
                int headingVisibility = insets.isVisible(WindowInsets.Type.ime()) ? View.GONE : View.VISIBLE;
                if (headerBrand != null) headerBrand.setVisibility(headingVisibility);
                if (headerTitle != null) headerTitle.setVisibility(headingVisibility);
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(24) + left, dp(16) + top, dp(24) + right, dp(8) + bottom);
            return insets;
        });
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        headerBrand = top;
        carrierName = text("", 13, secondary, true);
        carrierName.setLetterSpacing(0.10f); carrierName.setSingleLine(true);
        carrierName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        updateCarrierName();
        top.addView(carrierName, new LinearLayout.LayoutParams(0, -2, 1));
        IconButton settings = new IconButton("settings", false);
        settings.setContentDescription("Settings");
        settings.setOnClickListener(v -> startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_REQUEST));
        top.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);
        TextView title = text("Voicemail", 36, primary, true);
        headerTitle = title;
        add(root, title, 24);
        status = text("", 15, secondary, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(root, status, 8);
        LinearLayout searchBox = row(); searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(shape(surface, 16));
        search = new EditText(this);
        search.setSingleLine(true); search.setTextSize(15); search.setTextColor(primary); search.setHintTextColor(secondary);
        search.setHint("Search contacts, numbers, dates"); search.setContentDescription("Search voicemail by contact name, number, or date");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT); search.setPadding(dp(16), 0, 0, 0);
        search.setMinHeight(dp(52)); search.setText(query);
        searchBox.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        IconButton clear = new IconButton("clear", false);
        clear.setBackground(new RippleDrawable(ColorStateList.valueOf(divider), shape(surface, 24), null));
        clear.setContentDescription("Clear voicemail search"); clear.setOnClickListener(v -> search.setText(""));
        searchBox.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                query = text.toString(); if (messages != null) render();
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        search.setOnEditorActionListener((field, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.clearFocus(); return true;
            }
            return false;
        });
        add(root, searchBox, 20);
        LinearLayout filterRow = row();
        for (int i = 0; i < filters.length; i++) {
            final int choice = i;
            filters[i] = button("", false); filters[i].setMinWidth(0); filters[i].setMinimumWidth(0);
            filters[i].setPadding(dp(8), 0, dp(8), 0);
            filters[i].setOnClickListener(v -> { filter = choice; render(); });
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) item.setMarginStart(dp(8));
            filterRow.addView(filters[i], item);
        }
        add(root, filterRow, 12);
        LinearLayout tools = row(); tools.setGravity(Gravity.CENTER_VERTICAL);
        section = text("INBOX", 13, secondary, true);
        section.setLetterSpacing(0.08f);
        tools.addView(section, new LinearLayout.LayoutParams(0, -2, 1));
        refresh = button("Refresh", false);
        refresh.setOnClickListener(v -> sync()); tools.addView(refresh);
        add(root, tools, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false); scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        messages = column(); messages.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void sync() { sync(false); }

    private void sync(boolean reconnect) {
        if (syncing || updatingRead || clearingDownloads) return;
        syncing = true; refresh.setEnabled(false); refresh.setText("Refreshing…");
        status.setTextColor(secondary); status.setText("Checking your mailbox…");
        worker.execute(() -> {
            try {
                List<JSONObject> result = reconnect ? carrier.reconnect() : carrier.sync();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    syncing = false; inbox = result;
                    boolean found = false;
                    for (JSONObject item : inbox) if (activeId.equals(item.optString("objectid"))) found = true;
                    if (!found && !activeId.isEmpty()) { release(); activeId = ""; }
                    render();
                    reloadContactNames();
                    status.setTextColor(secondary); status.setText(summary() + " · Just updated");
                    refresh.setEnabled(true); refresh.setText("Refresh");
                    offerAlerts();
                });
            } catch (Exception exception) {
                carrier.saveDiagnostics();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    syncing = false; refresh.setEnabled(true); refresh.setText("Retry refresh");
                    status.setTextColor(error);
                    status.setText("Couldn't refresh. " + safeMessage(exception)
                        + (inbox.isEmpty() ? "" : " Your saved inbox is still available."));
                });
            }
        });
    }

    private void render() {
        messages.removeAllViews(); rows.clear();
        List<JSONObject> shown = new ArrayList<>(); int markedCount = 0;
        for (JSONObject message : inbox) {
            boolean marked = marks.contains(message.optString("objectid"));
            if (marked) markedCount++;
            if (filter == 1 && InboxQuery.isRead(message.optString("flag"))) continue;
            if (filter == 2 && !marked) continue;
            if (InboxQuery.matches(query, message.optString("returnnumber"), caller(message), message.optString("date"), date(message))) shown.add(message);
        }
        boolean oldestFirst = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("oldest_first", false);
        shown.sort((a,b) -> InboxQuery.compareDates(a.optString("date"),b.optString("date"),oldestFirst));
        String[] labels = {"All", "Unread", "Marked" + (markedCount == 0 ? "" : " (" + markedCount + ")")};
        for (int i = 0; i < filters.length; i++) {
            filters[i].setText(labels[i]); filters[i].setSelected(filter == i);
            filters[i].setBackground(new RippleDrawable(ColorStateList.valueOf(divider), shape(filter == i ? accentSurface : background, 24), null));
            filters[i].setContentDescription(labels[i] + " voicemails" + (filter == i ? ", selected" : ""));
        }
        section.setText(query.trim().isEmpty() && filter == 0 ? "INBOX" : shown.size() + (shown.size() == 1 ? " RESULT" : " RESULTS"));
        boolean activeShown = false;
        for (JSONObject message : shown) if (activeId.equals(message.optString("objectid"))) activeShown = true;
        if (!activeShown && player != null && prepared && player.isPlaying()) pause("filtered_out");
        try {
            carrier.diagnostics().put("search_result_count", shown.size());
            carrier.diagnostics().put("search_active", !query.trim().isEmpty());
            carrier.diagnostics().put("deletion_mark_count", markedCount); carrier.diagnostics().put("inbox_filter", filter);
            carrier.saveDiagnostics();
        } catch (Exception ignored) { }
        if (shown.isEmpty()) {
            LinearLayout empty = column(); empty.setPadding(dp(24), dp(32), dp(24), dp(32));
            empty.setBackground(shape(surface, 24));
            boolean narrowed = !query.trim().isEmpty() || filter != 0;
            empty.addView(text(contactsLoading && narrowed ? "Looking up contact names…" : narrowed ? "No matching voicemails" : carrier.hasSession() ? "No voicemails" : "Your voicemail, here", 18, primary, true));
            add(empty, text(narrowed ? contactsLoading ? "Names are loading from this phone."
                : !contactsEnabled() && !query.trim().isEmpty() ? "For name search, enable Contact names in Settings. You can also search a number or date, or choose All."
                : "Try a different search or choose All."
                : carrier.hasSession() ? "Refresh to check for new messages."
                : "Connect once using Mint mobile data. Your messages will appear here, ready to play.", 15, secondary, false), 12);
            messages.addView(empty); return;
        }
        if (filter == 2) add(messages, text("Marked on this phone. No recordings have been deleted. Use Restore to remove a mark.", 13, secondary, false), 8);
        for (JSONObject message : shown) {
            String id = message.optString("objectid");
            MessageRow card = new MessageRow(message);
            rows.put(id, card); add(messages, card.root, 12);
        }
        TextView foot = text("Downloaded recordings are available offline.", 13, secondary, false);
        foot.setGravity(Gravity.CENTER); foot.setPadding(dp(16), dp(8), dp(16), 0);
        add(messages, foot, 16);
        updatePlayback();
    }

    private final class MessageRow {
        final LinearLayout root = column();
        final LinearLayout transport = column();
        final IconButton play = new IconButton("play", true);
        final SeekBar seek = new SeekBar(MainActivity.this);
        final TextView elapsed = text("0:00", 13, secondary, false);
        final TextView length;
        final TextView saved;
        final TextView output = text("", 13, secondary, false);
        final String caller;
        boolean seeking;
        MessageRow(JSONObject message) {
            caller = caller(message);
            root.setPadding(dp(20), dp(20), dp(20), dp(20));
            root.setBackground(shape(surface, 24));
            LinearLayout heading = row(); heading.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info = column();
            TextView name = text(caller, 18, primary, true);
            info.addView(name);
            if (contactNames.containsKey(ContactNames.number(message)))
                add(info, text(formattedNumber(message), 13, secondary, false), 4);
            add(info, text(date(message) + " · " + duration((int)(message.optDouble("duration", 0) * 1000)), 13, secondary, false), 8);
            heading.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams playSize = new LinearLayout.LayoutParams(dp(52), dp(52)); playSize.setMarginStart(dp(12));
            heading.addView(play, playSize);
            play.setContentDescription("Play voicemail from " + caller);
            play.setOnClickListener(v -> toggle(message)); root.addView(heading);
            String state = marks.contains(message.optString("objectid")) ? "Marked for deletion"
                : InboxQuery.isRead(message.optString("flag")) ? "Read" : "Unread";
            saved = text(state + " · " + (carrier.cachedAudio(message) == null ? "Tap play to download" : "Saved on this phone"), 13, secondary, false);
            LinearLayout details = row(); details.setGravity(Gravity.CENTER_VERTICAL);
            details.addView(saved, new LinearLayout.LayoutParams(0, -2, 1));
            IconButton more = new IconButton("more", false);
            more.setBackground(new RippleDrawable(ColorStateList.valueOf(divider), shape(surface, 24), null));
            more.setContentDescription("Actions for voicemail from " + caller);
            more.setOnClickListener(v -> showActions(message));
            details.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48))); add(root, details, 8);
            root.setOnLongClickListener(v -> { showActions(message); return true; });
            seek.setProgressTintList(ColorStateList.valueOf(accent));
            seek.setThumbTintList(ColorStateList.valueOf(accent));
            seek.setProgressBackgroundTintList(ColorStateList.valueOf(divider));
            seek.setContentDescription("Voicemail playback position");
            seek.setKeyProgressIncrement(1000);
            transport.addView(seek, new LinearLayout.LayoutParams(-1, dp(48)));
            LinearLayout time = row();
            time.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
            length = text(duration((int)(message.optDouble("duration", 0) * 1000)), 13, secondary, false);
            time.addView(length); transport.addView(time);
            add(transport, output, 8);
            LinearLayout skip = row(); skip.setGravity(Gravity.CENTER);
            int seconds = getSharedPreferences("settings", MODE_PRIVATE).getInt("skip_seconds", 10);
            Button back = button("−"+seconds+" sec", true), ahead = button("+"+seconds+" sec", true);
            back.setContentDescription("Skip back "+seconds+" seconds"); ahead.setContentDescription("Skip forward "+seconds+" seconds");
            back.setOnClickListener(v -> skip(-seconds*1000)); ahead.setOnClickListener(v -> skip(seconds*1000));
            skip.addView(back);
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2); gap.setMarginStart(dp(8));
            skip.addView(ahead, gap); add(transport, skip, 8);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) elapsed.setText(duration(progress));
                }
                @Override public void onStopTrackingTouch(SeekBar bar) {
                    seeking = false; seekTo(bar.getProgress());
                }
            });
            transport.setVisibility(activeId.equals(message.optString("objectid")) ? View.VISIBLE : View.GONE);
            add(root, transport, 8);
        }
    }

    private void showActions(JSONObject message) {
        boolean read = InboxQuery.isRead(message.optString("flag"));
        String id = message.optString("objectid"); boolean marked = marks.contains(id);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Voicemail actions")
            .setItems(new String[]{read ? "Mark as unread" : "Mark as read", marked ? "Restore from deletion marks" : "Mark for deletion", "Save audio copy"}, (selection, action) -> {
                if (action == 0) changeRead(message, !read);
                else if(action==2) saveCopy(message);
                else if (marks.set(id, !marked)) {
                    render(); status.setTextColor(secondary);
                    status.setText(marked ? "Deletion mark removed" : "Marked for deletion on this phone · Nothing erased");
                } else { status.setTextColor(error); status.setText("Couldn't save the deletion mark. Try again."); }
            }).setNegativeButton("Cancel", null).create();
        dialog.show(); dialog.getWindow().setBackgroundDrawable(shape(surface, 28));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
    }

    private void saveCopy(JSONObject message) {
        if(savingCopy || clearingDownloads) { status.setText("Wait for the current file operation, then try again."); return; }
        Uri destination=DownloadFolder.selected(this);
        if(!DownloadFolder.accessible(this,destination)) {
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose download location")
                .setMessage("Choose a folder for saved audio copies. These are ordinary audio files that other apps with folder access can open. Carrier messages and the private playback cache are kept.")
                .setPositiveButton("Choose folder",(d,w)->{
                    pendingSaveId=message.optString("objectid");
                    try { startActivityForResult(DownloadFolder.picker(this),DOWNLOAD_FOLDER_REQUEST); }
                    catch(android.content.ActivityNotFoundException unavailable) { pendingSaveId=""; status.setText("No file manager is available to choose a download folder."); }
                }).setNegativeButton("Cancel",null).create();
            dialog.show(); dialog.getWindow().setBackgroundDrawable(shape(surface,28));
            dialog.getButton(-1).setAllCaps(false); dialog.getButton(-2).setAllCaps(false); return;
        }
        savingCopy=true; status.setTextColor(secondary); status.setText("Saving audio copy…");
        worker.execute(()->{
            try {
                File recording=carrier.audio(message);
                DownloadFolder.save(getApplicationContext(),destination,recording,message.optString("date"));
                record("audio_copy_saved",true);
                runOnUiThread(()->{
                    if(destroyed)return;
                    savingCopy=false; render(); status.setTextColor(secondary); status.setText("Audio copy saved · "+DownloadFolder.describe(this));
                });
            } catch(Exception failure) {
                record("audio_copy_saved",false);
                runOnUiThread(()->{
                    if(destroyed)return;
                    savingCopy=false; status.setTextColor(error);
                    status.setText("Couldn't save the audio copy. Check Download location in Settings and your connection, then retry.");
                });
            }
        });
    }

    private void changeRead(JSONObject message, boolean read) {
        if (updatingRead || syncing) { status.setText("Wait for the current mailbox update, then try again."); return; }
        updatingRead = true; refresh.setEnabled(false);
        status.setTextColor(secondary); status.setText(read ? "Marking as read…" : "Marking as unread…");
        worker.execute(() -> {
            try {
                List<JSONObject> result = carrier.setRead(message.optString("objectid"), read);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    updatingRead = false; refresh.setEnabled(true); inbox = result; render(); reloadContactNames();
                    status.setTextColor(secondary); status.setText(read ? "Marked as read · Synced with carrier" : "Marked as unread · Synced with carrier");
                });
            } catch (Exception exception) {
                carrier.saveDiagnostics();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    updatingRead = false; refresh.setEnabled(true); status.setTextColor(error);
                    status.setText("Couldn't confirm read status. Refresh to check your mailbox, then retry.");
                });
            }
        });
    }

    private void toggle(JSONObject message) {
        if(clearingDownloads) { status.setText("Wait for the playback cache to finish clearing."); return; }
        String id = message.optString("objectid");
        if (activeId.equals(id) && player != null && prepared) {
            if (player.isPlaying()) pause("paused"); else start();
            updatePlayback(); return;
        }
        if (activeId.equals(id) && loading) return;
        release(); activeId = id; loading = true;
        int request = generation;
        render();
        File cached = carrier.cachedAudio(message);
        if (cached != null) {
            record("audio_from_cache", true); prepare(cached, request); return;
        }
        worker.execute(() -> {
            try {
                File file = carrier.audio(message);
                runOnUiThread(() -> { if (!destroyed && request == generation) prepare(file, request); });
            } catch (Exception exception) {
                runOnUiThread(() -> { if (!destroyed && request == generation) playbackError(safeMessage(exception)); });
            }
        });
    }

    private void prepare(File file, int request) {
        try {
            MediaPlayer next = new MediaPlayer(); player = next;
            next.setAudioAttributes(attributes()); next.setDataSource(file.getAbsolutePath());
            next.addOnRoutingChangedListener(routing -> { if (player == next) verifyOutput(); }, handler);
            next.setOnPreparedListener(ready -> {
                if (destroyed || request != generation || player != ready) return;
                loading = false; prepared = true;
                record("playback_duration_ms", ready.getDuration());
                if (visible) start();
                render();
            });
            next.setOnCompletionListener(done -> {
                if (player != done) return;
                audio.abandonAudioFocusRequest(focus);
                record("playback_completed", true); record("playback_state", "completed"); updatePlayback();
            });
            next.setOnErrorListener((failed, what, extra) -> {
                if (player == failed) playbackError("This recording couldn't be played. Refresh your inbox and try again.");
                return true;
            });
            next.setOnSeekCompleteListener(seeked -> {
                if (player == seeked) { record("playback_position_ms", seeked.getCurrentPosition()); updatePlayback(); }
            });
            next.prepareAsync();
        } catch (Exception exception) { playbackError("This recording couldn't be opened. Refresh and try again."); }
    }

    private void start() {
        if (!prepared || player == null || !visible) return;
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            status.setTextColor(error); status.setText("Audio is in use. Try playing again when it's available."); return;
        }
        int output = getSharedPreferences("settings", MODE_PRIVATE).getInt("audio_output",0);
        requestedOutput = output==1 ? AudioDeviceInfo.TYPE_BUILTIN_SPEAKER : output==2 ? AudioDeviceInfo.TYPE_BUILTIN_EARPIECE : 0;
        AudioDeviceInfo preferred = null;
        if (requestedOutput != 0) for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            if (device.getType() == requestedOutput) { preferred = device; break; }
        if ((requestedOutput != 0 && preferred == null) || !player.setPreferredDevice(preferred)) {
            audio.abandonAudioFocusRequest(focus); status.setTextColor(error);
            status.setText("Selected audio output isn't available. Choose another output in Settings."); return;
        }
        if (player.getCurrentPosition() >= player.getDuration() - 100) player.seekTo(0);
        try { player.setPlaybackParams(new PlaybackParams().setSpeed(SettingsActivity.speed(this)).setPitch(1f)); }
        catch (RuntimeException unsupported) {
            getSharedPreferences("settings",MODE_PRIVATE).edit().putInt("playback_speed",1).apply();
            status.setTextColor(secondary); status.setText("This recording uses normal playback speed.");
        }
        try {
            player.start(); record("playback_started", true); record("playback_state", "playing");
            record("playback_speed", player.getPlaybackParams().getSpeed());
        } catch(RuntimeException failed) { playbackError("Playback couldn't start. Choose another output or speed in Settings and retry."); }
    }

    private void verifyOutput() {
        if (player == null || !prepared || !player.isPlaying()) return;
        AudioDeviceInfo actual = player.getRoutedDevice();
        if (actual == null) return;
        record("audio_output_type", actual.getType());
        if (requestedOutput != 0 && actual.getType() != requestedOutput
                && !(requestedOutput == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && actual.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)) {
            pause("output_unavailable"); status.setTextColor(error);
            status.setText("Playback paused because the selected audio output couldn't be used. Check Settings.");
        }
    }

    private void pause(String reason) {
        if (player != null && prepared && player.isPlaying()) {
            player.pause(); record("playback_state", reason); record("playback_position_ms", player.getCurrentPosition());
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        updatePlayback();
    }

    private void skip(int delta) {
        if (player != null && prepared) seekTo(player.getCurrentPosition() + delta);
    }

    private void seekTo(int position) {
        if (player != null && prepared) {
            player.seekTo(Math.max(0, Math.min(position, player.getDuration())), MediaPlayer.SEEK_CLOSEST);
        }
    }

    private void updatePlayback() {
        for (Map.Entry<String, MessageRow> entry : rows.entrySet()) {
            MessageRow row = entry.getValue(); boolean active = entry.getKey().equals(activeId);
            boolean playing = active && player != null && prepared && player.isPlaying();
            row.play.symbol = playing ? "pause" : "play"; row.play.invalidate();
            row.play.setEnabled(!active || !loading);
            row.play.setContentDescription((playing ? "Pause" : "Play") + " voicemail from " + row.caller);
            if (active) {
                row.transport.setVisibility(View.VISIBLE);
                row.seek.setEnabled(prepared && player != null);
                if (loading) row.saved.setText("Loading recording…");
                if (prepared && player != null) {
                    row.seek.setMax(player.getDuration());
                    if (!row.seeking) { row.seek.setProgress(player.getCurrentPosition()); row.elapsed.setText(duration(player.getCurrentPosition())); }
                    row.length.setText(duration(player.getDuration()));
                    AudioDeviceInfo device = player.getRoutedDevice();
                    row.output.setText(playing && device != null ? "Output: "+outputName(device.getType())
                        : player.getCurrentPosition()>=player.getDuration()?"Playback complete":"Playback paused");
                    if (Build.VERSION.SDK_INT >= 30) row.seek.setStateDescription(
                        duration(player.getCurrentPosition()) + " of " + duration(player.getDuration()));
                }
            }
        }
    }

    private void playbackError(String message) {
        release(); status.setTextColor(error); status.setText(message);
        MessageRow row = rows.get(activeId);
        if (row != null) row.saved.setText("Couldn't load recording · Tap play to retry");
        updatePlayback();
    }

    private void release() {
        generation++; loading = false; prepared = false;
        if (player != null) { player.release(); player = null; }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
    }

    private final class IconButton extends View {
        String symbol; final boolean filled;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconButton(String symbol, boolean filled) {
            super(MainActivity.this); this.symbol = symbol; this.filled = filled;
            setFocusable(true); setClickable(true); setMinimumWidth(dp(48)); setMinimumHeight(dp(48));
            setBackground(new RippleDrawable(ColorStateList.valueOf(divider), shape(filled ? accentSurface : background, 28), null));
        }
        @Override public CharSequence getAccessibilityClassName() { return Button.class.getName(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.translate(getWidth()/2f, getHeight()/2f);
            canvas.scale(dp(1), dp(1));
            paint.setColor(filled ? accent : secondary); paint.setStyle(Paint.Style.FILL); paint.setAlpha(isEnabled() ? 255 : 100);
            if ("play".equals(symbol)) {
                Path triangle = new Path(); triangle.moveTo(-5,-9); triangle.lineTo(9,0); triangle.lineTo(-5,9); triangle.close(); canvas.drawPath(triangle, paint);
            } else if ("pause".equals(symbol)) {
                canvas.drawRoundRect(-7,-9,-2,9,1,1,paint); canvas.drawRoundRect(2,-9,7,9,1,1,paint);
            } else if ("clear".equals(symbol)) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
                canvas.drawLine(-5,-5,5,5,paint); canvas.drawLine(-5,5,5,-5,paint);
            } else if ("more".equals(symbol)) {
                canvas.drawCircle(-6,0,1.6f,paint); canvas.drawCircle(0,0,1.6f,paint); canvas.drawCircle(6,0,1.6f,paint);
            } else if ("settings".equals(symbol)) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
                canvas.drawCircle(0,0,8,paint); canvas.drawCircle(0,0,3,paint);
                for (int i=0;i<8;i++) { canvas.drawLine(0,-12,0,-9,paint); canvas.rotate(45); }
            } else {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f); canvas.drawCircle(0,0,9,paint);
                paint.setStyle(Paint.Style.FILL); canvas.drawArc(-7,-7,7,7,-90,180,true,paint);
            }
            canvas.restore();
        }
    }

    private AudioAttributes attributes() { return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(); }
    private String summary() { return inbox.size() + (inbox.size() == 1 ? " voicemail" : " voicemails"); }
    private String freshness() {
        long last=carrier.lastSyncTime();
        return last==0 ? "Saved inbox" : "Updated "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(last));
    }
    private String outputName(int type) {
        if (type==AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) return "phone earpiece";
        if (type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE) return "phone speaker";
        if (type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type==AudioDeviceInfo.TYPE_BLE_HEADSET) return "Bluetooth";
        return "device audio";
    }
    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        // Network parser errors can include URLs or payload excerpts; show only our known user-facing messages.
        if (message != null && (message.startsWith("Turn on mobile data") || message.matches("(?:Carrier|Mailbox|Recording) [A-Za-z ]+ (?:HTTP |code )\\d+\\."))) return message;
        return "Check your connection and try again.";
    }
    private String caller(JSONObject message) {
        String name = contactNames.get(ContactNames.number(message));
        return name == null ? formattedNumber(message) : name;
    }
    private String formattedNumber(JSONObject message) {
        String number = ContactNames.number(message);
        if (number.isEmpty()) return "Unknown caller";
        String formatted = PhoneNumberUtils.formatNumber(number, Locale.getDefault().getCountry());
        return formatted == null ? number : formatted;
    }
    private String date(JSONObject message) {
        try {
            ZonedDateTime date = OffsetDateTime.parse(message.getString("date")).atZoneSameInstant(ZoneId.systemDefault());
            java.time.LocalDate today = java.time.LocalDate.now();
            String day = date.toLocalDate().equals(today) ? "Today" : date.toLocalDate().equals(today.minusDays(1)) ? "Yesterday"
                : date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
            String pattern = android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a";
            return day + " · " + date.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception ignored) { return "Date unavailable"; }
    }
    private String duration(int ms) { int seconds = Math.max(0, ms / 1000); return String.format(Locale.getDefault(), "%d:%02d", seconds/60, seconds%60); }
    private void record(String key, Object value) { try { carrier.diagnostics().put(key, value); carrier.saveDiagnostics(); } catch (Exception ignored) { } }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int color(int resource) { return getResources().getColor(resource, getTheme()); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private TextView text(String content, int size, int color, boolean medium) {
        TextView label = new TextView(this); label.setText(content); label.setTextSize(size); label.setTextColor(color);
        label.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return label;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private Button button(String label, boolean filled) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(15);
        button.setTextColor(accent); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(80)); button.setMinimumWidth(dp(80)); button.setPadding(dp(16), 0, dp(16), 0);
        button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(divider), shape(filled ? accentSurface : background, 24), null));
        return button;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); parent.addView(child, params);
    }
    private boolean contactsEnabled() {
        return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("contact_names", false)
            && checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void reloadContactNames() {
        int request = ++contactGeneration;
        if (contactSignal != null) contactSignal.cancel();
        contactSignal = new CancellationSignal();
        CancellationSignal cancel = contactSignal;
        if (!contactsEnabled()) {
            contactsLoading = false; contactNames.clear();
            record("contact_names_enabled", false); record("contact_match_count", 0);
            render(); return;
        }
        contactsLoading = true;
        List<JSONObject> snapshot = new ArrayList<>(inbox);
        contactWorker.execute(() -> {
            try {
                Map<String, String> names = ContactNames.lookup(getApplicationContext(), snapshot, cancel);
                runOnUiThread(() -> {
                    if (destroyed || request != contactGeneration || !contactsEnabled()) return;
                    contactNames = names; contactsLoading = false;
                    record("contact_names_enabled", true); record("contact_match_count", names.size()); render();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (destroyed || request != contactGeneration) return;
                    contactsLoading = false; contactNames.clear(); render();
                    status.setTextColor(error); status.setText("Couldn't load contact names. Number and date search still work. Check Contact names in Settings.");
                });
            }
        });
    }

    @Override protected void onStart() { super.onStart(); visible = true; handler.post(ticker); reloadContactNames(); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==TUTORIAL_REQUEST) tutorialOpen=false;
        if (request==SETTINGS_REQUEST && result==RESULT_OK && data!=null) pendingSettingsAction=data.getIntExtra(SettingsActivity.ACTION,0);
        if(request==DOWNLOAD_FOLDER_REQUEST) {
            String requested=pendingSaveId; pendingSaveId="";
            if(result!=RESULT_OK) return;
            try {
                DownloadFolder.remember(this,data);
                for(JSONObject message:inbox) if(requested.equals(message.optString("objectid"))) { saveCopy(message); return; }
                status.setText("Download location saved. Refresh the inbox and choose a message to save.");
            } catch(Exception failed) { status.setTextColor(error); status.setText("Download location couldn't be saved. Choose another folder and try again."); }
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (displayedAppearance!=AppAppearance.mode(this)) { recreate(); return; }
        updateCarrierName();
        // A background check may have updated the persisted inbox while this screen was stopped.
        if (!syncing && !updatingRead) inbox = carrier.cachedInbox();
        render();
        VoicemailAlerts.ensureScheduled(this);
        if (TutorialActivity.needed(this)) {
            if (!tutorialOpen) { tutorialOpen=true; startActivityForResult(new Intent(this,TutorialActivity.class),TUTORIAL_REQUEST); }
            return;
        }
        tutorialOpen=false;
        offerAlerts();
        int action=pendingSettingsAction; pendingSettingsAction=0;
        if (action==SettingsActivity.REFRESH) sync();
        else if (action==SettingsActivity.RECONNECT) sync(true);
        else if (action==SettingsActivity.CLEAR_DOWNLOADS) clearDownloads();
        else if (carrier.hasSession() && getSharedPreferences("settings",MODE_PRIVATE).getBoolean("refresh_on_open",true)) {
            long now=System.currentTimeMillis();
            long attempted=getSharedPreferences("settings",MODE_PRIVATE).getLong("last_open_refresh_attempt",0);
            if (now-carrier.lastSyncTime()>300000 && now-attempted>300000) {
                getSharedPreferences("settings",MODE_PRIVATE).edit().putLong("last_open_refresh_attempt",now).apply(); sync();
            }
        }
    }
    private void updateCarrierName() {
        String name=SimCarrier.name(this);
        carrierName.setText(name.toUpperCase(Locale.ROOT));
        carrierName.setContentDescription("Carrier: "+name);
    }
    private void offerAlerts() {
        if (!visible || TutorialActivity.needed(this) || !carrier.hasSession() || isFinishing() || getSharedPreferences("settings",MODE_PRIVATE).getBoolean("alerts_prompted",false)) return;
        getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("alerts_prompted",true).apply();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("New voicemail alerts")
            .setMessage("Get a notification when a check finds new unread voicemail. Background checks run about every "+VoicemailAlerts.interval(this)+" minutes; Android may delay them to save battery. Caller details stay out of the alert. You can change this in Settings.")
            .setPositiveButton("Enable",(d,w)->VoicemailAlerts.enable(this,ALERT_PERMISSION))
            .setNegativeButton("Not now",(d,w)->VoicemailAlerts.disable(this)).create();
        dialog.show(); dialog.getWindow().setBackgroundDrawable(shape(surface,28));
        dialog.getButton(-1).setAllCaps(false); dialog.getButton(-2).setAllCaps(false);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==ALERT_PERMISSION) { VoicemailAlerts.ensureScheduled(this); VoicemailAlerts.checkSoon(this); }
    }
    private void openNotification(Intent intent) {
        if (intent == null || !VoicemailAlerts.OPEN.equals(intent.getAction())) return;
        // An explicit notification tap should reach the message, even before the optional tour.
        TutorialActivity.acknowledge(this);
        query=""; filter=1; if(search!=null)search.setText("");
        VoicemailAlerts.dismiss(this); intent.setAction(null);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); openNotification(intent);
        if(!syncing && !updatingRead) inbox=carrier.cachedInbox();
        render();
    }
    private void clearDownloads() {
        if (clearingDownloads) return;
        clearingDownloads=true; release(); activeId=""; refresh.setEnabled(false); status.setText("Clearing playback cache…");
        worker.execute(()->{
            try {
                RecordingCache.clear(new File(getFilesDir(),"audio"));
                runOnUiThread(()->{ if(destroyed)return; clearingDownloads=false; refresh.setEnabled(true); render(); status.setTextColor(secondary); status.setText("Playback cache cleared · Saved copies and carrier messages kept"); });
            } catch(Exception failed) {
                runOnUiThread(()->{ if(destroyed)return; clearingDownloads=false; refresh.setEnabled(true); render(); status.setTextColor(error); status.setText("Some playback files couldn't be cleared. Try again."); });
            }
        });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("query", query); state.putInt("filter", filter); super.onSaveInstanceState(state);
        state.putInt("settings_action",pendingSettingsAction);
        state.putString("pending_save",pendingSaveId);
    }
    @Override protected void onStop() {
        visible = false; handler.removeCallbacks(ticker); pause("background_paused"); super.onStop();
    }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null); release(); worker.shutdown();
        if (contactSignal != null) contactSignal.cancel(); contactWorker.shutdown(); super.onDestroy();
    }
}
