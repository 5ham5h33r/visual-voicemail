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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** User settings with working preferences and explicit boundaries for unavailable features. */
public final class SettingsActivity extends Activity {
    static final int REFRESH = 1, RECONNECT = 2, CLEAR_DOWNLOADS = 3;
    private static final int CONTACT_PERMISSION = 41;
    private static final int DOWNLOAD_FOLDER = 43;
    private static final int ALERT_PERMISSION = 45;
    static final String ACTION = "settings_action";
    private SharedPreferences preferences;
    private LinearLayout content;
    private ScrollView settingsScroll;
    private int scrollPosition;
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
        if(state!=null) scrollPosition=state.getInt("scroll",0);
        build();
    }
    @Override protected void onResume() { super.onResume(); VoicemailAlerts.ensureScheduled(this); if (!firstResume) build(); firstResume = false; }

    private void build() {
        if(settingsScroll!=null) scrollPosition=settingsScroll.getScrollY();
        LinearLayout root = column(); root.setBackgroundColor(background);
        root.setPadding(dp(24), dp(24), dp(24), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(dp(24)+bars.left, dp(12)+bars.top, dp(24)+bars.right, dp(8)+bars.bottom);
            } else view.setPadding(dp(24), dp(12)+insets.getSystemWindowInsetTop(), dp(24), dp(8)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout heading = column();
        Ui controls = new Ui(this);
        heading.addView(controls.icon("back","Back to voicemail",false,()->finish()));
        TextView title = text("Settings", 32, primary); title.setTypeface(Typeface.create("sans-serif-medium", 0));
        title.setPadding(0, dp(12), 0, dp(20)); heading.addView(title); root.addView(heading);
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false);
        settingsScroll=scroll;
        content = column(); content.setPadding(0,0,0,dp(24)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);

        LinearLayout appearance = group("APPEARANCE");
        row(appearance, "Theme", APPEARANCES[clamp(preferences.getInt("appearance",0),4)], () ->
            choose("Theme", APPEARANCES, preferences.getInt("appearance",0), choice -> {
                preferences.edit().putInt("appearance",choice).apply(); recreate();
            }));

        LinearLayout help = group("HELP & TUTORIAL");
        row(help,"App tutorial","Replay the guide to setup, messages, playback, saving, and alerts",()->startActivity(TutorialActivity.replay(this)));

        LinearLayout inbox = group("INBOX & CONTACTS");
        boolean permitted = checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean names = permitted && preferences.getBoolean("contact_names",false);
        row(inbox, "Contact names", names ? "On · Names stay on this phone" : "Off · Number and date search remain available", () -> contactSettings());
        row(inbox, "Message order", preferences.getBoolean("oldest_first",false) ? "Oldest first" : "Newest first", () ->
            choose("Message order", new String[]{"Newest first","Oldest first"}, preferences.getBoolean("oldest_first",false)?1:0,
                choice -> { preferences.edit().putBoolean("oldest_first",choice==1).apply(); build(); }));

        LinearLayout playback = group("PLAYBACK");
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
        toggle(transcription,"Transcribe on open","Once the English model is ready, transcribe recordings as you open them.","auto_transcribe",true);
        note(transcription,Transcription.ready(this)?"English model ready · Recognition runs on this phone":"English transcription · One-time 104 MB model download from the recording screen");
        note(transcription,"Opening a recording generates a transcript once the model is ready. Speech detection supplies approximate phrase anchors. Tap a phrase to play from there; use Find in transcript to search. Check important details against the audio.");
        row(transcription,"Clear saved transcripts","Keep recordings, carrier messages, and the speech model",()->confirm("Clear saved transcripts?","Transcripts saved on this phone will be removed. Open a recording to generate its transcript again.","Clear transcripts",()->{
            info("Saved transcripts",Transcription.clear(this,false)?"Saved transcripts cleared.":"Wait for transcription or a model download to finish, then retry.");build();
        }));
        row(transcription,"Remove speech model","Free about 104 MB; keep saved transcripts and recordings",()->confirm("Remove speech model?","Existing transcripts remain readable and searchable. A new model download will be needed to transcribe more recordings.","Remove model",()->{
            info("Speech model",Transcription.clear(this,true)?"Speech model removed.":"Wait for transcription or a model download to finish, then retry.");build();
        }));

        LinearLayout mailbox = group("MAILBOX & SYNC");
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
        note(storage,"Used by Save audio copy in a message's menu. Saved copies are ordinary audio files in your selected folder. Playback uses a separate private cache; existing copies are not moved.");
        try {
            long[] stats = RecordingCache.stats(new File(getFilesDir(),"audio"));
            note(storage,stats[0]+(stats[0]==1?" privately cached recording":" privately cached recordings")+" · "+size(stats[1]));
        } catch (Exception ignored) { note(storage,"Downloaded storage is currently unavailable."); }
        row(storage,"Clear playback cache","Keep saved audio copies, carrier messages, and the inbox", () -> confirm("Clear playback cache?",
            "Remove the app's private playback files. Saved copies in your download folder, the inbox, and carrier messages are kept. A connection may be needed to play recordings again.",
            "Clear cache", () -> action(CLEAR_DOWNLOADS)));

        LinearLayout privacy = group("PRIVACY & PERMISSIONS");
        row(privacy,"App permissions","Manage Contacts and other Android app permissions", () ->
            open(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        row(privacy,"Privacy","How messages, contacts, and downloads are handled", () -> info("Privacy",
            "Mailbox credentials and inbox metadata are encrypted on this phone using Android Keystore. Playback files use app-private storage. Saved audio copies go to your chosen folder and follow that folder's access and backup rules.\n\nContact names are looked up locally and kept in memory. Names and search queries are not sent to the carrier or a project server.\n\nTranscription runs locally. Saved transcripts are encrypted using Android Keystore. The one-time speech model download contacts its public model host; audio and recognized text are never uploaded for transcription.\n\nThere are no analytics or advertising services. Mailbox authentication and message requests communicate with the carrier."));

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
        note(about,"Permanent message deletion is not available in this build. Deletion marks are local and reversible.");
        final int restoreScroll=scrollPosition; scroll.post(()->scroll.scrollTo(0,restoreScroll));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("scroll",settingsScroll==null?0:settingsScroll.getScrollY()); super.onSaveInstanceState(state);
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
        dialog.show(); dialog.getWindow().setBackgroundDrawable(shape(surface,28));
        for (int which:new int[]{-1,-2,-3}) if (dialog.getButton(which)!=null) dialog.getButton(which).setAllCaps(false);
    }
    private void action(int action) { setResult(RESULT_OK,new Intent().putExtra(ACTION,action)); finish(); }
    private void open(Intent intent) { try { startActivity(intent); } catch (android.content.ActivityNotFoundException ignored) { info("Unavailable","No app is available to open this setting or link."); } }
    private LinearLayout group(String title) {
        TextView label=text(title,13,secondary); label.setLetterSpacing(.08f); label.setPadding(dp(4),dp(20),0,dp(12)); content.addView(label);
        LinearLayout group=column(); group.setBackground(shape(surface,24)); group.setClipToOutline(true); content.addView(group); return group;
    }
    private SpannableString label(String title,String detail) {
        SpannableString text=new SpannableString(title+"\n"+detail); int start=title.length()+1;
        text.setSpan(new RelativeSizeSpan(.82f),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(secondary),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); return text;
    }
    private void row(LinearLayout parent,String title,String detail,Runnable action) {
        TextView view=text("",16,primary); view.setText(label(title,detail)); view.setPadding(dp(20),dp(16),dp(20),dp(16));
        view.setMinHeight(dp(72)); view.setGravity(Gravity.CENTER_VERTICAL); view.setFocusable(true); view.setClickable(true);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(divider),null,shape(surface,0)));
        view.setContentDescription(title+". "+detail); view.setOnClickListener(v->action.run()); parent.addView(view,new LinearLayout.LayoutParams(-1,-2));
    }
    private void toggle(LinearLayout parent,String title,String detail,String key,boolean fallback) {
        Switch toggle=new Switch(this); toggle.setText(label(title,detail)); toggle.setTextSize(16); toggle.setTextColor(primary);
        toggle.setPadding(dp(20),dp(16),dp(20),dp(16)); toggle.setMinHeight(dp(72)); toggle.setSwitchPadding(dp(16));
        int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};
        toggle.setThumbTintList(new ColorStateList(states,new int[]{accent,secondary}));
        toggle.setTrackTintList(new ColorStateList(states,new int[]{color(R.color.accent_surface),divider}));
        toggle.setChecked(preferences.getBoolean(key,fallback)); toggle.setOnCheckedChangeListener((v,checked)->preferences.edit().putBoolean(key,checked).apply());
        parent.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
    }
    private void note(LinearLayout parent,String detail) { TextView view=text(detail,13,secondary); view.setPadding(dp(20),dp(16),dp(20),dp(16)); parent.addView(view); }
    private String size(long bytes) { return bytes<1024 ? bytes+" B" : bytes<1024*1024 ? String.format(Locale.getDefault(),"%.1f KB",bytes/1024d) : String.format(Locale.getDefault(),"%.1f MB",bytes/1048576d); }
    private LinearLayout column() { LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String text,int size,int color) { TextView view=new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans-serif",0)); return view; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable shape=new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private int color(int id) { return getResources().getColor(id,getTheme()); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
