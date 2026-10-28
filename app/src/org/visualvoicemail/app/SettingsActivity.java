package org.visualvoicemail.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.view.WindowInsets;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** User settings with working preferences and explicit boundaries for unavailable features. */
public final class SettingsActivity extends MotionActivity {
    static final int REFRESH = 1, RECONNECT = 2, CLEAR_DOWNLOADS = 3;
    private static final int CONTACT_PERMISSION = 41;
    private static final int DOWNLOAD_FOLDER = 43;
    private static final int ALERT_PERMISSION = 45;
    static final String ACTION = "settings_action";
    private SharedPreferences preferences;
    private LinearLayout content;
    private ScrollView settingsScroll;
    private int scrollPosition, unfilteredScrollPosition, searchCursor, scrollGeneration;
    private String settingsQuery = "";
    private boolean restoreSearchFocus;
    private EditText settingsSearch;
    private View searchClear;
    private TextView settingsResultCount, noSettings;
    private final List<Section> sections = new ArrayList<>();

    private static final class Entry {
        final View view;
        final String title, detail;
        Entry(View view, String title, String detail) { this.view=view; this.title=title; this.detail=detail; }
    }
    private static final class Section {
        final String title;
        final TextView label;
        final LinearLayout group;
        final List<Entry> entries = new ArrayList<>();
        final List<View> notes = new ArrayList<>();
        Section(String title, TextView label, LinearLayout group) { this.title=title; this.label=label; this.group=group; }
    }
    private int background, surface, primary, secondary, accent, divider;
    private boolean firstResume = true;
    private static final String[] APPEARANCES = {"Use device setting", "Light", "Dark", "AMOLED (true black)"};
    private static final String[] SPEEDS = {"0.75×", "1×", "1.25×", "1.5×", "2×"};
    private static final float[] SPEED_VALUES = {.75f, 1f, 1.25f, 1.5f, 2f};

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppAppearance.context(base)); }
    @Override public void onCreate(Bundle state) {
        AppAppearance.apply(this); super.onCreate(state);
        preferences = getSharedPreferences("settings", MODE_PRIVATE);
        background = AppAppearance.background(this); surface = color(R.color.surface);
        primary = color(R.color.primary_text); secondary = color(R.color.secondary_text);
        accent = color(R.color.accent); divider = color(R.color.divider);
        if(state!=null) {
            scrollPosition=state.getInt("scroll",0);
            unfilteredScrollPosition=state.getInt("unfiltered_scroll",0);
            settingsQuery=state.getString("settings_query","");
            restoreSearchFocus=state.getBoolean("search_focus",false);
            searchCursor=state.getInt("search_cursor",settingsQuery.length());
        }
        build();
    }
    @Override protected void onResume() { super.onResume(); VoicemailAlerts.ensureScheduled(this); if (!firstResume) build(); firstResume = false; }

    private void build() {
        if(settingsScroll!=null) scrollPosition=settingsScroll.getScrollY();
        if(settingsSearch!=null) { restoreSearchFocus=settingsSearch.hasFocus(); searchCursor=settingsSearch.getSelectionStart(); }
        sections.clear();
        LinearLayout root = column(); root.setBackgroundColor(background);
        root.setClipToPadding(false);
        root.setPadding(dp(20), dp(20), dp(20), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(dp(20)+bars.left, dp(12)+bars.top, dp(20)+bars.right, dp(8)+Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom));
            } else view.setPadding(dp(20), dp(12)+insets.getSystemWindowInsetTop(), dp(20), dp(8)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        Ui controls = new Ui(this);
        LinearLayout heading = controls.row();
        heading.addView(controls.icon("back","Back to voicemail",false,this::navigateUp));
        TextView title = controls.label("Settings", Ui.SECTION_TITLE, primary, true);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(0,-2,1);
        titleSize.setMarginStart(dp(4)); heading.addView(title,titleSize);
        LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1,-2);
        headingSize.setMarginStart(-dp(12)); root.addView(heading,headingSize);
        LinearLayout searchBox = controls.row(); searchBox.setBackground(shape(surface,16));
        settingsSearch = new EditText(this);
        settingsSearch.setTextSize(14); settingsSearch.setSingleLine(true);
        settingsSearch.setTextColor(primary); settingsSearch.setHintTextColor(secondary);
        settingsSearch.setHint("Search settings"); settingsSearch.setContentDescription("Search settings by name or feature");
        settingsSearch.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        settingsSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        settingsSearch.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        settingsSearch.setPadding(dp(16),0,dp(8),0); settingsSearch.setMinHeight(dp(48));
        settingsSearch.setText(settingsQuery);
        searchBox.addView(settingsSearch,new LinearLayout.LayoutParams(0,-2,1));
        searchClear = controls.icon("close","Clear settings search",false,()->settingsSearch.setText(""));
        searchClear.setVisibility(settingsQuery.isEmpty()?View.GONE:View.VISIBLE); searchBox.addView(searchClear);
        LinearLayout.LayoutParams searchSize=new LinearLayout.LayoutParams(-1,-2);searchSize.topMargin=dp(8);root.addView(searchBox,searchSize);
        settingsResultCount=text("",12,secondary);settingsResultCount.setPadding(dp(4),dp(8),0,0);
        settingsResultCount.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(settingsResultCount);
        settingsSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count) {
                boolean wasSearching=SettingsSearch.active(settingsQuery);
                if(!wasSearching && SettingsSearch.active(text.toString()) && settingsScroll!=null)
                    unfilteredScrollPosition=settingsScroll.getScrollY();
                settingsQuery=text.toString();
                searchClear.setVisibility(text.length()==0?View.GONE:View.VISIBLE);
                filterSettings();
                int target=SettingsSearch.active(settingsQuery)?0:unfilteredScrollPosition;
                scrollAfterLayout(target);
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        settingsSearch.setOnEditorActionListener((view,action,event)->{
            if(action!=EditorInfo.IME_ACTION_SEARCH)return false;
            hideSearchKeyboard();return true;
        });
        root.setFocusableInTouchMode(true);root.requestFocus();
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false);
        settingsScroll=scroll;
        content = column(); content.setPadding(0,0,0,dp(20)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);

        LinearLayout appearance = group("APPEARANCE");
        row(appearance, "Theme", APPEARANCES[clamp(preferences.getInt("appearance",0),4)], () ->
            choose("Theme", APPEARANCES, preferences.getInt("appearance",0), choice -> {
                preferences.edit().putInt("appearance",choice).apply(); recreate();
            }));

        toggle(appearance,"Reduce motion","Limit animations and press effects.","reduce_motion",false);

        LinearLayout help = group("HELP & TUTORIAL");
        row(help,"App tutorial","Replay setup and tips on the actual controls",()->startActivity(TutorialActivity.replay(this)));

        LinearLayout inbox = group("INBOX & CONTACTS");
        boolean permitted = checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean names = permitted && preferences.getBoolean("contact_names",false);
        row(inbox, "Contact names", names ? "On · Names stay on this phone" : "Off · Number and date search remain available", () -> contactSettings());
        row(inbox, "Message order", preferences.getBoolean("oldest_first",false) ? "Oldest first" : "Newest first", () ->
            choose("Message order", new String[]{"Newest first","Oldest first"}, preferences.getBoolean("oldest_first",false)?1:0,
                choice -> { preferences.edit().putBoolean("oldest_first",choice==1).apply(); build(); }));

        LinearLayout playback = group("PLAYBACK");
        toggle(playback,"Mark as read when playback starts",
            "Off keeps read status manual. When on, carrier changes need a connection; only confirmed changes update the inbox.","read_on_play",false);
        int speed = clamp(preferences.getInt("playback_speed",1), SPEEDS.length);
        row(playback,"Playback speed",SPEEDS[speed], () -> choose("Playback speed", SPEEDS, speed,
            choice -> { preferences.edit().putInt("playback_speed",choice).apply(); build(); }));
        int skip = preferences.getInt("skip_seconds",10);
        String[] skips = {"5 seconds","10 seconds","15 seconds","30 seconds"}; int[] skipValues = {5,10,15,30};
        int selectedSkip = skip==5?0:skip==15?2:skip==30?3:1;
        row(playback,"Skip interval",skip+" seconds", () -> choose("Skip interval",skips,selectedSkip,
            choice -> { preferences.edit().putInt("skip_seconds",skipValues[choice]).apply(); build(); }));
        String[] outputs = {"Follow device","Phone speaker","Phone earpiece"};
        int output = clamp(preferences.getInt("audio_output",0),3);
        row(playback,"Audio output",outputs[output], () -> choose("Audio output",outputs,output,
            choice -> { preferences.edit().putInt("audio_output",choice).apply(); build(); }));
        note(playback,"Playback pauses when you leave the app. A selected phone output must be available before playback starts.");

        LinearLayout transcription = group("TRANSCRIPTION");
        row(transcription,"Transcription quality",Transcription.selected(this).label()+" · "+(Transcription.ready(this)?"Ready":Transcription.selected(this).sizeLabel()+" download"),()->startActivity(new Intent(this,TranscriptionSettingsActivity.class)));
        row(transcription,"Recording language",SpeechOptions.label(SpeechOptions.language(this,Transcription.selected(this))),()->SpeechOptions.chooseLanguage(this,()->build()));
        row(transcription,"Transcript output",SpeechOptions.english(this)?"Original + English translation":"Original language",()->SpeechOptions.chooseOutput(this,()->build()));
        toggle(transcription,"Transcribe on open","Once the selected model is ready, generate text as you open recordings.","auto_transcribe",true);
        note(transcription,"Recognition and English translation stay on this phone. Tiny and Base are English-only; other languages and translation use Large v3. Existing saved text is kept until you update it.");
        note(transcription,"Tap transcript phrases to listen; use Find to search. Timing and recognized words can be imperfect.");
        row(transcription,"Clear saved transcripts","Keep recordings, carrier messages, and the speech model",()->confirm("Clear saved transcripts?","Transcripts saved on this phone will be removed. Open a recording to generate its transcript again.","Clear transcripts",()->{
            info("Saved transcripts",Transcription.clear(this,false)?"Saved transcripts cleared.":"Wait for transcription or a model download to finish, then retry.");build();
        }));


        LinearLayout mailbox = group("MAILBOX & SYNC");
        row(mailbox,"Voicemail greeting","Record or choose audio, preview, then save to your carrier",()->startActivity(new Intent(this,GreetingActivity.class)));
        CarrierClient client = CarrierClient.get(this);
        long last = client.lastSyncTime();
        note(mailbox,last==0 ? "No successful sync saved yet." : "Last updated "+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(last)));
        toggle(mailbox,"Refresh on open","Check when returning to the app, at most once every 5 minutes.","refresh_on_open",true);
        row(mailbox,"Refresh mailbox","Return to the inbox and check for new messages", () -> action(REFRESH));
        row(mailbox,"Reconnect mailbox","Renew sign-in with Mint mobile data; keep saved messages", () -> confirm("Reconnect mailbox?",
            "Turn on mobile data and select Mint as your data SIM. Saved messages and downloads stay on this phone.","Reconnect", () -> action(RECONNECT)));
        row(mailbox,"Connection help","Mint data SIM and connection requirements", () -> info("Connection help",
            "Choose Mint as the mobile-data SIM and turn on mobile data for the first connection or renewed sign-in. Saved recordings can play offline.\n\nYour existing mailbox is used. You don't need to change call forwarding or your default dialer."));

        LinearLayout notifications = group("NOTIFICATIONS");
        row(notifications,"New voicemail alerts",VoicemailAlerts.status(this),()->notificationSettings());
        int interval=VoicemailAlerts.interval(this);
        row(notifications,"Check interval","About every "+interval+" minutes",()->choose("Check interval",
            new String[]{"15 minutes","30 minutes","1 hour"},interval==60?2:interval==30?1:0,choice->{
                preferences.edit().putInt("alert_interval",choice==2?60:choice==1?30:15).apply();
                VoicemailAlerts.ensureScheduled(this); build();
            }));
        row(notifications,"Sound, vibration & lock screen","Manage the New voicemail notification channel in Android",()->open(VoicemailAlerts.systemSettings(this)));
        row(notifications,"Send test notification","Check Android notification delivery without changing voicemail",()->{
            if(VoicemailAlerts.test(this)) info("Test sent","Look in your notification shade. This only tests display permissions, not background mailbox delivery.");
            else info("Notifications blocked","Enable New voicemail alerts and allow notifications in Android settings, then try again.");
        });
        note(notifications,"Checks use your carrier connection and a little data. Android controls timing and can delay checks while idle or saving battery. Force-stopping the app pauses checks until you reopen it. These are scheduled checks, not instant carrier push alerts.");
        long checked=preferences.getLong("background_checked_at",0);
        note(notifications,checked==0?"No background check completed yet.":"Last background check "+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(checked))
            +(preferences.getBoolean("background_check_ok",false)?" · Successful":" · Couldn't connect; open the inbox to retry"));

        LinearLayout storage = group("STORAGE");
        row(storage,"Download location",DownloadFolder.describe(this),()->downloadLocation());
        note(storage,"Used by Export audio copy in a message's menu. Exports are ordinary audio files in your selected folder. Saved voicemail stays in separate app-private storage; existing copies are not moved.");
        try {
            long[] stats = RecordingCache.stats(new File(getFilesDir(),"audio"));
            note(storage,stats[0]+(stats[0]==1?" privately cached recording":" privately cached recordings")+" · "+size(stats[1]));
        } catch (Exception ignored) { note(storage,"Downloaded storage is currently unavailable."); }
        row(storage,"Clear playback cache","Keep Saved recordings, exported files, carrier messages, and the inbox", () -> confirm("Clear playback cache?",
            "Remove temporary playback files. Recordings in Saved, exported copies, the inbox, and carrier messages are kept. A connection may be needed to play other recordings again.",
            "Clear cache", () -> action(CLEAR_DOWNLOADS)));

        LinearLayout privacy = group("PRIVACY & PERMISSIONS");
        row(privacy,"App permissions","Manage Contacts and other Android app permissions", () ->
            open(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        row(privacy,"Privacy","How messages, contacts, and downloads are handled", () -> info("Privacy",
            "Mailbox credentials and inbox metadata are encrypted on this phone using Android Keystore. Playback files use app-private storage. Recordings in Saved stay inside this app, including after carrier removal. Uninstalling removes these app-local recordings and transcripts. Export audio copies to your chosen folder for files you can keep outside the app; those files follow the folder's access and backup rules.\n\nContact names are looked up locally and kept in memory. Names and search queries are not sent to the carrier or a project server.\n\nTranscription runs locally. Saved transcripts are encrypted using Android Keystore. The one-time speech model download contacts its public model host; audio and recognized text are never uploaded for transcription.\n\nThere are no analytics or advertising services. Mailbox authentication and message requests communicate with the carrier."));

        LinearLayout about = group("ABOUT");
        String version;
        try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch (Exception ignored) { version="Unknown"; }
        note(about,"Voice Mail "+version+" · Development build");
        row(about,"Source code","View the project on GitHub", () -> open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/5ham5h33r/visual-voicemail"))));
        row(about,"Open-source license","MIT License", () -> {
            try (java.io.InputStream input=getResources().openRawResource(R.raw.license)) {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int read;
                while ((read=input.read(buffer))!=-1) bytes.write(buffer,0,read);
                info("MIT License",new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            } catch (Exception ignored) { info("License","The project is licensed under the MIT License. The full text is available in the source repository."); }
        });
        row(about,"Speech recognition licenses","sherpa-onnx, ONNX Runtime, Whisper, Silero VAD, and Kotlin",()->{
            try(java.io.InputStream input=getResources().openRawResource(R.raw.third_party_notices)) {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
                info("Speech recognition licenses",new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }catch(Exception ignored){info("Speech recognition licenses","License texts and upstream source links are available in the source repository under docs/licenses.");}
        });
        note(about,"Delete saves and verifies a local recording before removing its carrier copy. Saved recordings survive playback-cache clearing. Export important copies before uninstalling; uninstalling removes app-local recordings and transcripts.");
        noSettings=text("No matching settings\nTry a setting name, like theme or download.",15,secondary);
        noSettings.setGravity(Gravity.CENTER);noSettings.setPadding(dp(16),dp(32),dp(16),dp(32));content.addView(noSettings);
        filterSettings();
        scrollAfterLayout(scrollPosition);
        if(restoreSearchFocus) {
            settingsSearch.requestFocus();
            settingsSearch.setSelection(Math.max(0,Math.min(searchCursor,settingsSearch.length())));
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("scroll",settingsScroll==null?0:settingsScroll.getScrollY());
        state.putInt("unfiltered_scroll",unfilteredScrollPosition);
        state.putString("settings_query",settingsQuery);
        state.putBoolean("search_focus",settingsSearch!=null&&settingsSearch.hasFocus());
        state.putInt("search_cursor",settingsSearch==null?0:settingsSearch.getSelectionStart());
        super.onSaveInstanceState(state);
    }

    static float speed(Context context) { return SPEED_VALUES[clamp(context.getSharedPreferences("settings",MODE_PRIVATE).getInt("playback_speed",1),SPEED_VALUES.length)]; }
    private void notificationSettings() {
        boolean enabled=VoicemailAlerts.enabled(this), allowed=VoicemailAlerts.allowed(this);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("New voicemail alerts")
            .setMessage("Notify for newly discovered unread voicemail. Existing messages stay quiet when enabled. Checks run about every "+VoicemailAlerts.interval(this)+" minutes and may be delayed by Android. No caller details are shown.")
            .setNegativeButton("Cancel",null);
        if(enabled && !allowed) {
            builder.setPositiveButton("Android settings",(d,w)->open(VoicemailAlerts.systemSettings(this)))
                .setNeutralButton("Turn off",(d,w)->{VoicemailAlerts.disable(this);build();});
        } else builder.setPositiveButton(enabled?"Turn off":"Enable",(d,w)->{
            if(enabled)VoicemailAlerts.disable(this);else VoicemailAlerts.enable(this,ALERT_PERMISSION);
            build();
        });
        show(builder.create());
    }
    private void downloadLocation() {
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("Download location")
            .setMessage(DownloadFolder.describe(this)+"\n\nChoose where Save audio copy writes recordings. Other apps with folder access can open saved copies. Changing this location does not move existing files or the private playback cache.")
            .setPositiveButton("Choose folder",(d,w)->{
                try { startActivityForResult(DownloadFolder.picker(this),DOWNLOAD_FOLDER); }
                catch(android.content.ActivityNotFoundException ignored) { info("Folder picker unavailable","No file manager is available to choose a folder."); }
            }).setNegativeButton("Cancel",null);
        if(DownloadFolder.selected(this)!=null) builder.setNeutralButton("Forget folder",(d,w)->{
            if(DownloadFolder.forget(this)) build(); else info("Location not changed","The download location couldn't be cleared. Try again.");
        });
        show(builder.create());
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==DOWNLOAD_FOLDER&&result==RESULT_OK) {
            try { DownloadFolder.remember(this,data); build(); }
            catch(Exception failed) { info("Location not changed",failed.getMessage()==null?"Choose another folder and try again.":failed.getMessage()); }
        }
    }
    private static int clamp(int value,int size) { return Math.max(0,Math.min(value,size-1)); }
    private void contactSettings() {
        boolean granted=checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED;
        boolean enabled=granted && preferences.getBoolean("contact_names",false);
        boolean blocked=!granted && preferences.getBoolean("contacts_permission_asked",false)
            && !shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CONTACTS);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Contact names")
            .setMessage("Match voicemail callers with contacts saved on this phone. Names stay on your device. Number and date search work without Contacts access.")
            .setPositiveButton(enabled?"Turn off":blocked?"Open settings":"Enable",(d,w)->{
                if(enabled) { preferences.edit().putBoolean("contact_names",false).apply(); build(); }
                else if(granted) { preferences.edit().putBoolean("contact_names",true).apply(); build(); }
                else if(blocked) {
                    preferences.edit().putBoolean("contact_names",true).apply();
                    open(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
                } else {
                    preferences.edit().putBoolean("contacts_permission_asked",true).apply();
                    requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS},CONTACT_PERMISSION);
                }
            }).setNegativeButton("Cancel",null).create(); show(dialog);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==ALERT_PERMISSION) { VoicemailAlerts.ensureScheduled(this); VoicemailAlerts.checkSoon(this); build(); }
        if(request==CONTACT_PERMISSION) { preferences.edit().putBoolean("contact_names",grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED).apply(); build(); }
    }
    private interface Choice { void select(int choice); }
    private void choose(String title,String[] items,int selected,Choice chosen) {
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(items,clamp(selected,items.length),
            (selection,choice)->{ selection.dismiss(); chosen.select(choice); }).setNegativeButton("Cancel",null).create(); show(dialog);
    }
    private void info(String title,String message) { show(new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Done",null).create()); }
    private void confirm(String title,String message,String label,Runnable confirmed) {
        show(new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(label,(d,w)->confirmed.run()).setNegativeButton("Cancel",null).create());
    }
    private void show(AlertDialog dialog) {
        if(!AppMotion.enabled(this))dialog.getWindow().setWindowAnimations(0);
        dialog.show(); AppMotion.buttons(dialog); dialog.getWindow().setBackgroundDrawable(shape(surface,28));
        for (int which:new int[]{-1,-2,-3}) if (dialog.getButton(which)!=null) dialog.getButton(which).setAllCaps(false);
    }
    private void action(int action) { setResult(RESULT_OK,new Intent().putExtra(ACTION,action)); finish(); }
    private void open(Intent intent) { try { startActivity(intent); } catch (android.content.ActivityNotFoundException ignored) { info("Unavailable","No app is available to open this setting or link."); } }
    private LinearLayout group(String title) {
        TextView label=text(title,12,secondary); label.setLetterSpacing(.08f); label.setPadding(dp(4),dp(16),0,dp(8)); content.addView(label);
        if(Build.VERSION.SDK_INT>=28)label.setAccessibilityHeading(true);
        LinearLayout group=column(); group.setBackground(shape(surface,18)); group.setClipToOutline(true); content.addView(group);
        sections.add(new Section(title,label,group));return group;
    }
    private Section section(LinearLayout group) {
        for(Section section:sections)if(section.group==group)return section;
        throw new IllegalArgumentException("Unknown settings section");
    }
    private void scrollAfterLayout(int target) {
        final ScrollView scroll=settingsScroll;
        final int generation=++scrollGeneration;
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if(scroll.getViewTreeObserver().isAlive())scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                if(scroll==settingsScroll && generation==scrollGeneration) {
                    scroll.fling(0);scroll.computeScroll();scroll.scrollTo(0,target);
                }
                return true;
            }
        });
    }
    private void filterSettings() {
        boolean searching=SettingsSearch.active(settingsQuery);int count=0;
        for(Section section:sections) {
            int matches=0;
            for(Entry entry:section.entries) {
                boolean match=SettingsSearch.matches(settingsQuery,section.title,entry.title,entry.detail);
                entry.view.setVisibility(match?View.VISIBLE:View.GONE);
                if(match)matches++;
            }
            for(View note:section.notes)note.setVisibility(searching?View.GONE:View.VISIBLE);
            section.label.setVisibility(matches>0?View.VISIBLE:View.GONE);
            section.group.setVisibility(matches>0?View.VISIBLE:View.GONE);count+=matches;
        }
        settingsResultCount.setText(count+(count==1?" setting":" settings"));
        settingsResultCount.setVisibility(searching?View.VISIBLE:View.GONE);
        if(noSettings!=null)noSettings.setVisibility(searching&&count==0?View.VISIBLE:View.GONE);
    }
    private void hideSearchKeyboard() {
        if(settingsSearch==null)return;
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(settingsSearch.getWindowToken(),0);
        settingsSearch.clearFocus();restoreSearchFocus=false;
    }
    private SpannableString label(String title,String detail) {
        SpannableString text=new SpannableString(title+"\n"+detail); int start=title.length()+1;
        text.setSpan(new RelativeSizeSpan(.82f),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(secondary),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); return text;
    }
    private void row(LinearLayout parent,String title,String detail,Runnable action) {
        TextView view=text("",15,primary); view.setText(label(title,detail)); view.setPadding(dp(16),dp(12),dp(16),dp(12));
        view.setMinHeight(dp(60)); view.setGravity(Gravity.CENTER_VERTICAL); view.setFocusable(true); view.setClickable(true);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(divider),null,shape(surface,0)));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(Button.class.getName());
            }
        });
        view.setContentDescription(title+". "+detail); AppMotion.press(view); view.setOnClickListener(v->{hideSearchKeyboard();action.run();}); parent.addView(view,new LinearLayout.LayoutParams(-1,-2));
        section(parent).entries.add(new Entry(view,title,detail));
    }
    private void toggle(LinearLayout parent,String title,String detail,String key,boolean fallback) {
        Switch toggle=new AppMotion.Switch(this); toggle.setText(label(title,detail)); toggle.setTextSize(15); toggle.setTextColor(primary);
        toggle.setPadding(dp(16),dp(12),dp(16),dp(12)); toggle.setMinHeight(dp(64)); toggle.setSwitchPadding(dp(16));
        int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};
        toggle.setThumbTintList(new ColorStateList(states,new int[]{accent,secondary}));
        toggle.setTrackTintList(new ColorStateList(states,new int[]{color(R.color.accent_surface),divider}));
        toggle.setChecked(preferences.getBoolean(key,fallback)); toggle.setOnCheckedChangeListener((v,checked)->{hideSearchKeyboard();preferences.edit().putBoolean(key,checked).apply();AppMotion.configure(this);});
        parent.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        section(parent).entries.add(new Entry(toggle,title,detail));
    }
    private void note(LinearLayout parent,String detail) { TextView view=text(detail,12,secondary); view.setPadding(dp(16),dp(12),dp(16),dp(12)); parent.addView(view); section(parent).notes.add(view); }
    private String size(long bytes) { return bytes<1024 ? bytes+" B" : bytes<1024*1024 ? String.format(Locale.getDefault(),"%.1f KB",bytes/1024d) : String.format(Locale.getDefault(),"%.1f MB",bytes/1048576d); }
    private LinearLayout column() { LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String text,int size,int color) { TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans-serif",0)); return view; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private int color(int id) { return getResources().getColor(id,getTheme()); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
