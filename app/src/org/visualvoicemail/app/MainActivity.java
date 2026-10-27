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

/** Native carrier inbox with local transcript search; no dialer role. */
public final class MainActivity extends MotionActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService contactWorker = Executors.newSingleThreadExecutor();
    private Map<String, String> contactNames = new HashMap<>();
    private CancellationSignal contactSignal;
    private int contactGeneration;
    private boolean contactsLoading;
    private final Map<String, Transcript> transcripts = new HashMap<>();
    private int displayedAppearance, pendingSettingsAction;
    private boolean clearingDownloads;
    private static final int SETTINGS_REQUEST = 42;
    private static final int DOWNLOAD_FOLDER_REQUEST = 44;
    private static final int ALERT_PERMISSION = 45;
    private static final int TUTORIAL_REQUEST = 46;
    private boolean tutorialOpen, guidePending;
    private View settingsControl, filterControl;
    private String pendingSaveId = "";
    private boolean savingCopy;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, MessageRow> rows = new HashMap<>();
    private List<JSONObject> inbox = new ArrayList<>();
    private final java.util.Set<String> selectedIds = new java.util.LinkedHashSet<>();
    private List<JSONObject> shownMessages = new ArrayList<>();
    private boolean selecting, selectionBackRegistered;
    private LinearLayout selectionBar, bulkBar, batchPanel, inboxTools;
    private TextView selectionCount, batchText;
    private Button selectAll, selectControl, batchCancel, batchDismiss;
    private AlertDialog bulkDialog;
    private long speechVersion = -1, mailboxVersion = -1;
    private android.window.OnBackInvokedCallback selectionBack;
    private final Runnable batchTicker = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            updateBatchPanel();
            handler.postDelayed(this, 400);
        }
    };
    private CarrierClient carrier;
    private MessageMarks marks;
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
    private boolean visible, syncing, destroyed, updatingRead;
    private int background, surface, primary, secondary, accent, accentSurface, divider, error;

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
        if (state != null) {
            selecting = state.getBoolean("selecting", false);
            ArrayList<String> selected = state.getStringArrayList("selected_ids");
            if (selected != null) selectedIds.addAll(selected);
        }
        buildLayout();
        inbox = carrier.cachedInbox();
        render();
        if (!inbox.isEmpty()) status.setText(summary() + " · " + freshness());
        else if (carrier.hasSession()) status.setText("Refresh to check your mailbox");
        else status.setText("Connect to your Mint Mobile mailbox");
        refresh.setText(carrier.hasSession() ? "Refresh" : "Connect");
        openNotification(getIntent());
        guidePending=getIntent().getBooleanExtra("guide",false);
    }

    private void buildLayout() {
        LinearLayout root = column();
        root.setBackgroundColor(background);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right;
                bottom = Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
                int headingVisibility = selecting || insets.isVisible(WindowInsets.Type.ime()) ? View.GONE : View.VISIBLE;
                if (headerBrand != null) headerBrand.setVisibility(headingVisibility);
                if (headerTitle != null) headerTitle.setVisibility(headingVisibility);
            } else {
                left = insets.getSystemWindowInsetLeft(); top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(20) + left, dp(12) + top, dp(20) + right, dp(8) + bottom);
            return insets;
        });
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        headerBrand = top;
        carrierName = text("", 12, secondary, true);
        carrierName.setLetterSpacing(0.10f); carrierName.setSingleLine(true);
        carrierName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        updateCarrierName();
        top.addView(carrierName, new LinearLayout.LayoutParams(0, -2, 1));
        IconButton settings = new IconButton("settings", false);
        settingsControl=settings;
        settings.setContentDescription("Settings");
        settings.setOnClickListener(v -> startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_REQUEST));
        top.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);
        selectionBar = row(); selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        IconButton close = new IconButton("clear", false);
        close.setContentDescription("Cancel voicemail selection");
        close.setOnClickListener(v -> clearSelection());
        selectionBar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        selectionCount = text("", 17, primary, true);
        selectionCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        selectionBar.addView(selectionCount, new LinearLayout.LayoutParams(0, -2, 1));
        selectAll = button("Select all", false);
        selectAll.setTextSize(13);
        selectAll.setOnClickListener(v -> selectAllShown());
        selectionBar.addView(selectAll);
        IconButton bulkMore = new IconButton("more", false);
        bulkMore.setContentDescription("More selected voicemail actions");
        bulkMore.setOnClickListener(v -> showBulkActions());
        selectionBar.addView(bulkMore, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(selectionBar);
        TextView title = text("Voicemail", Ui.PAGE_TITLE, primary, true);
        headerTitle = title;
        add(root, title, 12);
        status = text("", 13, secondary, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(root, status, 6);
        LinearLayout searchBox = row(); searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(shape(surface, 16));
        search = new EditText(this);
        search.setSingleLine(true); search.setTextSize(14); search.setTextColor(primary); search.setHintTextColor(secondary);
        search.setHint("Search voicemail"); search.setContentDescription("Search contacts, numbers, dates, and saved transcripts");
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT); search.setPadding(dp(16), 0, 0, 0);
        search.setMinHeight(dp(48)); search.setText(query);
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
        add(root, searchBox, 14);
        LinearLayout filterRow = row(); filterControl=filterRow;
        for (int i = 0; i < filters.length; i++) {
            final int choice = i;
            filters[i] = button("", false); filters[i].setMinWidth(0); filters[i].setMinimumWidth(0);
            filters[i].setPadding(dp(8), 0, dp(8), 0);
            filters[i].setOnClickListener(v -> { if(filter==choice)return; filter = choice; render(); AppMotion.reveal(messages,140,4); });
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) item.setMarginStart(dp(8));
            filterRow.addView(filters[i], item);
        }
        add(root, filterRow, 8);
        LinearLayout tools = row(); inboxTools = tools; tools.setGravity(Gravity.CENTER_VERTICAL);
        section = text("INBOX", 13, secondary, true);
        section.setLetterSpacing(0.08f);
        tools.addView(section, new LinearLayout.LayoutParams(0, -2, 1));
        selectControl = button("Select", false);
        selectControl.setTextSize(13);
        selectControl.setOnClickListener(v -> { selecting = true; render(); });
        tools.addView(selectControl);
        refresh = button("Refresh", false);
        refresh.setOnClickListener(v -> sync()); tools.addView(refresh);
        add(root, tools, 14);
        bulkBar = row();
        String[] actions = {"Transcribe", "Mark read", "Delete"};
        for (int i = 0; i < actions.length; i++) {
            final int action = i;
            Button control = button(actions[i], false); control.setTextSize(13);
            control.setMinWidth(0); control.setMinimumWidth(0);
            control.setPadding(dp(4), 0, dp(4), 0);
            control.setOnClickListener(v -> {
                if (action == 0) confirmTranscripts();
                else if (action == 1) changeSelectedRead(true);
                else confirmMarks(true);
            });
            control.setContentDescription(i == 2 ? "Mark selected voicemails for deletion" : actions[i] + " for selected voicemails");
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) size.setMarginStart(dp(6));
            bulkBar.addView(control, size);
        }
        add(root, bulkBar, 4);
        batchPanel = column();
        batchText = text("", 13, secondary, false);
        batchText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        batchPanel.addView(batchText);
        LinearLayout batchButtons = row();
        batchCancel = button("Cancel transcription queue", false);
        batchCancel.setTextSize(13); batchCancel.setOnClickListener(v -> { Transcription.cancel(); updateBatchPanel(); });
        batchDismiss = button("Dismiss", false); batchDismiss.setTextSize(13);
        batchDismiss.setOnClickListener(v -> { Transcription.dismissBatch(); MailboxBatch.dismiss(); updateBatchPanel(); });
        batchButtons.addView(batchCancel); batchButtons.addView(batchDismiss);
        batchPanel.addView(batchButtons); add(root, batchPanel, 8);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false); scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        messages = column(); messages.setPadding(0, dp(4), 0, dp(16));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void sync() { sync(false); }

    private void sync(boolean reconnect) {
        if (syncing || updatingRead || clearingDownloads || MailboxBatch.busy) return;
        syncing = true; refresh.setEnabled(false); refresh.setText("Refreshing…");
        status.setTextColor(secondary); status.setText("Checking your mailbox…");
        worker.execute(() -> {
            try {
                List<JSONObject> result = reconnect ? carrier.reconnect() : carrier.sync();
                runOnUiThread(() -> {
                    if (destroyed) return;
                    syncing = false; inbox = result; loadTranscripts();
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
            if (InboxQuery.matches(query, message.optString("returnnumber"), caller(message), message.optString("date"), date(message)) || (transcripts.containsKey(message.optString("objectid")) && transcripts.get(message.optString("objectid")).matches(query))) shown.add(message);
        }
        boolean oldestFirst = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("oldest_first", false);
        shown.sort((a,b) -> InboxQuery.compareDates(a.optString("date"),b.optString("date"),oldestFirst));
        shownMessages = shown;
        java.util.Set<String> visibleIds = new java.util.HashSet<>();
        for (JSONObject message : shown) visibleIds.add(message.optString("objectid"));
        selectedIds.retainAll(visibleIds);
        if (shown.isEmpty()) selecting = false;
        updateSelection();
        String[] labels = {"All", "Unread", "Marked" + (markedCount == 0 ? "" : " (" + markedCount + ")")};
        for (int i = 0; i < filters.length; i++) {
            filters[i].setText(labels[i]); filters[i].setSelected(filter == i);
            filters[i].setBackground(new RippleDrawable(ColorStateList.valueOf(divider), shape(filter == i ? accentSurface : background, 24), null));
            filters[i].setContentDescription(labels[i] + " voicemails" + (filter == i ? ", selected" : ""));
        }
        section.setText(query.trim().isEmpty() && filter == 0 ? "INBOX" : shown.size() + (shown.size() == 1 ? " RESULT" : " RESULTS"));
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
            rows.put(id, card); add(messages, card.root, 8);
        }
        TextView foot = text(query.trim().isEmpty() ? "Saved recordings and transcripts are available offline." : "Search includes saved transcripts. Open a recording to transcribe it.", 13, secondary, false);
        foot.setGravity(Gravity.CENTER); foot.setPadding(dp(16), dp(8), dp(16), 0);
        add(messages, foot, 16);
    }

    private void updateSelection() {
        selectionBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        inboxTools.setVisibility(selecting ? View.GONE : View.VISIBLE);
        bulkBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        headerBrand.setVisibility(selecting ? View.GONE : View.VISIBLE);
        headerTitle.setVisibility(selecting ? View.GONE : View.VISIBLE);
        selectControl.setVisibility(!selecting && !shownMessages.isEmpty() ? View.VISIBLE : View.GONE);
        selectionCount.setText(selectedIds.size() + " selected");
        selectAll.setText(!shownMessages.isEmpty() && selectedIds.size() == shownMessages.size() ? "Deselect all" : "Select all");
        selectAll.setContentDescription("Select or deselect all " + shownMessages.size() + " voicemails in current results");
        for (int i = 0; i < bulkBar.getChildCount(); i++)
            bulkBar.getChildAt(i).setEnabled(!selectedIds.isEmpty() && !MailboxBatch.busy);
        if (Build.VERSION.SDK_INT >= 33) {
            if (selecting && !selectionBackRegistered) {
                if (selectionBack == null) selectionBack = this::clearSelection;
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, selectionBack);
                selectionBackRegistered = true;
            } else if (!selecting && selectionBackRegistered) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(selectionBack);
                selectionBackRegistered = false;
            }
        }
    }

    private void toggleSelection(JSONObject message) {
        String id = message.optString("objectid");
        if (!selectedIds.remove(id)) selectedIds.add(id);
        render();
    }

    private void clearSelection() { selectedIds.clear(); selecting = false; render(); }

    private void selectAllShown() {
        if (selectedIds.size() == shownMessages.size()) selectedIds.clear();
        else for (JSONObject message : shownMessages) selectedIds.add(message.optString("objectid"));
        render();
    }

    private List<JSONObject> selectedMessages() {
        List<JSONObject> selected = new ArrayList<>();
        for (JSONObject message : shownMessages)
            if (selectedIds.contains(message.optString("objectid"))) selected.add(message);
        return selected;
    }

    private void showBulkActions() {
        if (selectedIds.isEmpty()) { status.setText("Select one or more voicemails first."); return; }
        if (MailboxBatch.busy) { status.setText("Wait for the current mailbox update."); return; }
        showBulkDialog(new AlertDialog.Builder(this).setTitle(selectedIds.size() + " selected")
                .setItems(new String[]{"Generate transcripts", "Mark as read", "Mark as unread", "Mark for deletion", "Restore deletion marks"}, (d, action) -> {
                    if (action == 0) confirmTranscripts();
                    else if (action == 1 || action == 2) changeSelectedRead(action == 1);
                    else confirmMarks(action == 3);
                }).setNegativeButton("Cancel", null).create());
    }

    private void showBulkDialog(AlertDialog dialog) {
        bulkDialog = dialog;
        if (!AppMotion.enabled(this)) dialog.getWindow().setWindowAnimations(0);
        dialog.show(); AppMotion.buttons(dialog);
        dialog.getWindow().setBackgroundDrawable(shape(surface, 28));
    }

    private void confirmMarks(boolean marked) {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty()) return;
        showBulkDialog(new AlertDialog.Builder(this)
                .setTitle((marked ? "Mark " : "Restore ") + ids.size() + " voicemails?")
                .setMessage(marked ? "Marked for deletion on this phone. Carrier recordings and saved transcripts are kept. You can undo this using Restore deletion marks."
                        : "Remove the deletion marks from these messages. Your recordings and transcripts are kept.")
                .setPositiveButton(marked ? "Mark for deletion" : "Restore", (d, w) -> {
                    if (marks.setAll(ids, marked)) {
                        clearSelection(); status.setTextColor(secondary);
                        status.setText(ids.size() + (marked ? " marked for deletion · Nothing erased" : " deletion marks removed"));
                    } else { status.setTextColor(error); status.setText("Couldn't save deletion marks. Nothing changed; try again."); }
                }).setNegativeButton("Cancel", null).create());
    }

    private void changeSelectedRead(boolean read) {
        List<String> ids = new ArrayList<>(selectedIds);
        if (ids.isEmpty() || syncing || updatingRead || MailboxBatch.busy) {
            status.setText("Wait for the current mailbox update, then retry."); return;
        }
        if (MailboxBatch.read(this, ids, read)) { clearSelection(); updateBatchPanel(); }
    }

    private void confirmTranscripts() {
        final List<JSONObject> selected = selectedMessages();
        if (selected.isEmpty()) return;
        if (Transcription.busy) { status.setText("Finish or cancel the current transcription or model download first."); return; }
        if (!Transcription.ready(this)) {
            showBulkDialog(new AlertDialog.Builder(this).setTitle("Set up transcription")
                    .setMessage("Download your selected model first. Your selection will be kept when you return.")
                    .setPositiveButton("Choose model", (d, w) -> startActivity(new Intent(this, TranscriptionSettingsActivity.class)))
                    .setNegativeButton("Not now", null).create()); return;
        }
        int existing = 0;
        for (JSONObject message : selected) if (Transcription.cached(this, message.optString("objectid")) != null) existing++;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Transcribe " + selected.size() + " voicemails?")
                .setMessage(Transcription.selected(this).label() + " · On this phone. Missing recordings download from your carrier. Keep the app open. "
                        + existing + " already have transcripts; Generate missing keeps them. Cancel stops at the next phrase boundary."
                        + (Transcription.selected(this) == TranscriptionQuality.HIGHEST ? " Large v3 can take minutes per message." : ""))
                .setPositiveButton("Generate missing", (d, w) -> startTranscripts(selected, false))
                .setNegativeButton("Cancel", null);
        if (existing > 0) dialog.setNeutralButton("Update all", (d, w) -> startTranscripts(selected, true));
        showBulkDialog(dialog.create());
    }

    private void startTranscripts(List<JSONObject> selected, boolean replace) {
        if (Transcription.generateBatch(this, selected, replace)) { clearSelection(); updateBatchPanel(); }
        else status.setText("Another speech operation started. Retry when it finishes.");
    }

    private void updateBatchPanel() {
        String text = MailboxBatch.status;
        if (!Transcription.batchStatus.isEmpty()) text += (text.isEmpty() ? "" : "\n") + Transcription.batchStatus;
        if (Transcription.batch && Transcription.cancelled) text += "\nCancelling after the current phrase…";
        if (!text.contentEquals(batchText.getText())) batchText.setText(text);
        batchPanel.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        batchCancel.setVisibility(Transcription.batch ? View.VISIBLE : View.GONE);
        batchCancel.setEnabled(!Transcription.cancelled);
        batchDismiss.setVisibility(!Transcription.batch && !MailboxBatch.busy ? View.VISIBLE : View.GONE);
        refresh.setEnabled(!syncing && !updatingRead && !clearingDownloads && !MailboxBatch.busy);
        if (speechVersion != Transcription.batchVersion) {
            speechVersion = Transcription.batchVersion;
            loadTranscripts();
        }
        if (mailboxVersion != MailboxBatch.version && !MailboxBatch.busy) {
            mailboxVersion = MailboxBatch.version;
            if (!MailboxBatch.busy && !syncing && !updatingRead) {
                inbox = carrier.cachedInbox(); render();
            }
        }
    }

    @Override public void onBackPressed() {
        if (selecting) clearSelection(); else super.onBackPressed();
    }

    private final class MessageRow {
        final LinearLayout root = column();
        final IconButton play = new IconButton("play", true);
        final TextView saved;
        final String caller;
        MessageRow(JSONObject message) {
            caller = caller(message);
            root.setPadding(dp(16), dp(16), dp(16), dp(12));
            root.setBackground(shape(selectedIds.contains(message.optString("objectid")) ? accentSurface : surface, 18));
            LinearLayout heading = row(); heading.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info = column();
            TextView name = text(caller, 16, primary, true);
            info.addView(name);
            if (contactNames.containsKey(ContactNames.number(message)))
                add(info, text(formattedNumber(message), 12, secondary, false), 4);
            add(info, text(date(message) + " · " + duration((int)(message.optDouble("duration", 0) * 1000)), 12, secondary, false), 4);
            if (selecting) {
                android.widget.CheckBox check = new android.widget.CheckBox(MainActivity.this);
                check.setButtonTintList(ColorStateList.valueOf(accent));
                check.setChecked(selectedIds.contains(message.optString("objectid")));
                check.setContentDescription("Select voicemail from " + caller);
                check.setOnClickListener(v -> toggleSelection(message));
                heading.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
            }
            heading.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams playSize = new LinearLayout.LayoutParams(dp(48), dp(48)); playSize.setMarginStart(dp(12));
            heading.addView(play, playSize);
            play.setContentDescription("Play voicemail from " + caller);
            play.setOnClickListener(v -> { if (selecting) toggleSelection(message); else openRecording(message, true); });
            play.setVisibility(selecting ? View.GONE : View.VISIBLE); root.addView(heading);
            root.setOnClickListener(v -> { if (selecting) toggleSelection(message); else openRecording(message, false); });
            root.setFocusable(true); AppMotion.press(root);
            root.setContentDescription((selecting ? "Select" : "Open") + " voicemail from " + caller + ", " + date(message));
            root.setSelected(selecting && selectedIds.contains(message.optString("objectid")));
            if (selecting) root.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCheckable(true); info.setChecked(host.isSelected());
                }
            });
            String state = marks.contains(message.optString("objectid")) ? "Marked for deletion"
                : InboxQuery.isRead(message.optString("flag")) ? "Read" : "Unread";
            saved = text(state + " · " + (transcripts.containsKey(message.optString("objectid")) ? "Transcript available" : carrier.cachedAudio(message) == null ? "Open to listen" : "Saved on this phone"), 12, secondary, false);
            Transcript transcript = transcripts.get(message.optString("objectid"));
            if (transcript != null && !transcript.text().isEmpty()) {
                String excerpt = transcript.text();
                if (!query.trim().isEmpty()) {
                    java.util.List<int[]> found = TranscriptSearch.find(excerpt, query);
                    if (!found.isEmpty()) excerpt = excerpt.substring(Math.max(0, found.get(0)[0]-30));
                }
                TextView preview = text(excerpt, 14, secondary, false);
                preview.setMaxLines(1); preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
                add(root, preview, 8);
            }
            IconButton more = new IconButton("more", false);
            more.setBackground(new RippleDrawable(ColorStateList.valueOf(divider), shape(surface, 24), null));
            more.setContentDescription("Actions for voicemail from " + caller);
            more.setOnClickListener(v -> { if (selecting) toggleSelection(message); else showActions(message); });
            more.setVisibility(selecting ? View.GONE : View.VISIBLE);
            heading.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48))); add(root, saved, 6);
            root.setOnLongClickListener(v -> {
                selecting = true; toggleSelection(message);
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.clearFocus();
                return true;
            });

        }
    }

    private void openRecording(JSONObject message, boolean play) {
        startActivity(new Intent(this, RecordingActivity.class).putExtra("id", message.optString("objectid"))
            .putExtra("caller", caller(message)).putExtra("find", query).putExtra("play", play));
    }

    private void loadTranscripts() {
        final List<JSONObject> snapshot = new ArrayList<>(inbox);
        worker.execute(() -> {
            Map<String, Transcript> loaded = new HashMap<>();
            for (JSONObject item : snapshot) {
                String id = item.optString("objectid");
                Transcript value = Transcription.cached(this, id);
                if (value != null) loaded.put(id, value);
            }
            runOnUiThread(() -> { if (!destroyed) { transcripts.clear(); transcripts.putAll(loaded); render(); } });
        });
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
        if(!AppMotion.enabled(this))dialog.getWindow().setWindowAnimations(0); dialog.show(); AppMotion.buttons(dialog); dialog.getWindow().setBackgroundDrawable(shape(surface, 28));
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
            if(!AppMotion.enabled(this))dialog.getWindow().setWindowAnimations(0); dialog.show(); AppMotion.buttons(dialog); dialog.getWindow().setBackgroundDrawable(shape(surface,28));
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
        if (updatingRead || syncing || MailboxBatch.busy) { status.setText("Wait for the current mailbox update, then try again."); return; }
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

    private final class IconButton extends View {
        String symbol; final boolean filled;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        IconButton(String symbol, boolean filled) {
            super(MainActivity.this); this.symbol = symbol; this.filled = filled;
            setFocusable(true); setClickable(true); setMinimumWidth(dp(48)); setMinimumHeight(dp(48));
            AppMotion.press(this);
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

    private String summary() { return inbox.size() + (inbox.size() == 1 ? " voicemail" : " voicemails"); }
    private String freshness() {
        long last=carrier.lastSyncTime();
        return last==0 ? "Saved inbox" : "Updated "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(last));
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
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setTextColor(accent); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(80)); button.setMinimumWidth(dp(80)); button.setPadding(dp(16), 0, dp(16), 0);
        button.setStateListAnimator(null); AppMotion.press(button);
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

    @Override protected void onStart() { super.onStart(); visible = true; reloadContactNames(); }
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
        handler.removeCallbacks(batchTicker); handler.post(batchTicker);
        loadTranscripts();
        if(guidePending)handler.postDelayed(this::showGuide,500);
        if (displayedAppearance!=AppAppearance.mode(this)) { recreate(); return; }
        updateCarrierName();
        // A background check may have updated the persisted inbox while this screen was stopped.
        if (!syncing && !updatingRead && !MailboxBatch.busy) inbox = carrier.cachedInbox();
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
        if(!AppMotion.enabled(this))dialog.getWindow().setWindowAnimations(0); dialog.show(); AppMotion.buttons(dialog); dialog.getWindow().setBackgroundDrawable(shape(surface,28));
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
        if(intent.getBooleanExtra("guide",false)){guidePending=true;handler.postDelayed(this::showGuide,500);}
        if(!syncing && !updatingRead) inbox=carrier.cachedInbox();
        render();
    }
    private void showGuide() {
        if(!guidePending || !visible || isFinishing())return;
        guidePending=false; getIntent().removeExtra("guide");
        List<GuideOverlay.Tip> tips=new ArrayList<>();
        tips.add(new GuideOverlay.Tip(search,"Find a message","Search a caller, a date, or words in transcripts already saved on this phone.",false));
        tips.add(new GuideOverlay.Tip(filterControl,"Keep your place","Unread shows messages you haven't marked read. Marked holds reversible deletion marks.",false));
        tips.add(new GuideOverlay.Tip(settingsControl,"Make it yours","Themes, contact names, alerts, download location, and this tour live in Settings.",false));
        tips.add(new GuideOverlay.Tip(()->rows.isEmpty()?refresh:rows.values().iterator().next().root,rows.isEmpty()?"Connect your mailbox":"Open a recording",rows.isEmpty()?"Tap the highlighted button with Mint mobile data turned on. Your existing mailbox stays in place.":"Tap the highlighted message to see its transcript and player. Long-press a message or tap Select for bulk actions. The three-dot menu has individual actions.",true));
        GuideOverlay.show(this,tips,()->{},()->getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("guide_recording",false).apply());
    }

    private void clearDownloads() {
        if (clearingDownloads) return;
        clearingDownloads=true; refresh.setEnabled(false); status.setText("Clearing playback cache…");
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
        state.putBoolean("selecting", selecting);
        state.putStringArrayList("selected_ids", new ArrayList<>(selectedIds));
        state.putString("query", query); state.putInt("filter", filter); super.onSaveInstanceState(state);
        state.putInt("settings_action",pendingSettingsAction);
        state.putString("pending_save",pendingSaveId);
    }
    @Override protected void onStop() {
        visible = false; handler.removeCallbacks(batchTicker); super.onStop();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (Build.VERSION.SDK_INT >= 33 && selectionBackRegistered)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(selectionBack);
        handler.removeCallbacksAndMessages(null); worker.shutdown();
        if (contactSignal != null) contactSignal.cancel(); contactWorker.shutdown(); super.onDestroy();
    }
}
