package org.visualvoicemail.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Offline, optional guide. It never connects, plays audio, or requests permissions. */
public final class TutorialActivity extends Activity {
    private static final String SEEN = "tutorial_seen", PAGE = "tutorial_page";
    private static final String[] TOPICS = {"Welcome", "Connect your mailbox", "Inbox & search", "Playback", "Save & organize", "Notifications", "Make it yours"};
    private static final String[] TITLES = {"Your voicemail,\nin one place", "Start with your\nMint SIM", "Find the message\nyou need", "Listen your way", "Keep what matters", "Know when\nvoicemail arrives", "Make it feel\nlike your phone"};
    private static final String[] SUMMARIES = {
        "A quick tour of Voice Mail. Swipe through, choose a topic, or come back whenever you need.",
        "Connect to the voicemail you already have. This build supports Mint Mobile.",
        "Your messages, with simple filters and search that works on your saved inbox.",
        "Tap Play on a voicemail to open its player. One recording plays at a time.",
        "Save a separate audio file, change read status, or organize messages without erasing them.",
        "Enable alerts when you're ready. We'll notify you when a check finds new unread voicemail.",
        "Open the Settings gear to adjust appearance, playback, contacts, notifications, and storage."
    };
    // Short, grouped instructions instead of a wall of text on each slide.
    private static final String[][] HEADINGS = {
        {"Use your existing mailbox", "Stay with your phone app", "Return whenever you need"},
        {"Choose Mint for mobile data", "Tap Connect in the inbox", "Keep listening offline"},
        {"All, Unread, and Marked", "Search names, numbers, or dates", "Refresh when you need"},
        {"Play, pause, and seek", "Choose your listening style", "Leaving the app pauses audio"},
        {"Save audio copy", "Mark as read or unread", "Deletion marks are reversible"},
        {"Allow notifications", "Choose your check interval", "Check sound and delivery"},
        {"Light, Dark, or AMOLED", "Your data stays under your control", "Help is always here"}
    };
    private static final String[][] DETAILS = {
        {"Your recordings stay in your carrier mailbox. No call-forwarding setup is needed.", "Keep Samsung Phone or your preferred default dialer.", "Replay this guide from Settings → Help & tutorial. Nothing here changes your mailbox."},
        {"Turn on mobile data and select Mint as the data SIM before the first connection or renewed sign-in.", "Once connected, your messages appear in the inbox. Connection help and Reconnect mailbox are in Settings.", "Audio you've already played is cached privately. Uncached recordings need a connection; mailbox refresh can use Wi-Fi after setup."},
        {"Switch filters to find unread messages or messages you've marked for deletion.", "Contact names are optional: enable them in Settings and allow Contacts access. Names stay on this phone.", "Tap Refresh to check the carrier mailbox. Settings shows the last update and controls refresh on open."},
        {"Use the progress bar to jump within the recording, or the skip buttons to move backward and forward.", "Settings lets you change playback speed, skip length, and device/speaker/earpiece output. The player shows its actual audio route.", "Playback pauses when you leave Voice Mail. Return and tap Play to resume."},
        {"Open a message's three-dot menu. Choose the destination under Settings → Storage → Download location. Clearing playback cache keeps these saved copies.", "Use the message menu to change read status in your carrier mailbox. Listening alone doesn't mark a message read.", "Mark for deletion only marks a message on this phone. Find it in Marked and restore it from the menu. Permanent deletion is not available yet."},
        {"Enable New voicemail alerts in Settings, then allow Android's notification permission. Tapping an alert opens your unread inbox.", "Checks default to about every 15 minutes, with 30-minute and 1-hour options. Android may delay them; these aren't instant push alerts.", "Settings includes sound, vibration, lock-screen controls, and a test notification. Force-stopping the app pauses checks until you reopen it."},
        {"Follow the device theme or pick your own. AMOLED uses true-black backgrounds.", "Contact lookup is optional. The Privacy page explains local storage and how saved audio copies are handled.", "Replay any topic from Settings → Help & tutorial. Transcription, transcript search, and permanent message deletion are not available in this build."}
    };
    private int page, background, surface, primary, secondary, accent, accentSurface, divider;
    private boolean replay;
    private ScrollView scroll;

    static boolean needed(Context context) { return !context.getSharedPreferences("settings", MODE_PRIVATE).getBoolean(SEEN, false); }
    static Intent replay(Context context) { return new Intent(context, TutorialActivity.class).putExtra("replay", true); }
    static void acknowledge(Context context) { context.getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean(SEEN, true).putInt(PAGE, 0).apply(); }
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppAppearance.context(base)); }
    @Override public void onCreate(Bundle state) {
        AppAppearance.apply(this); super.onCreate(state); setTitle("Voice Mail tutorial");
        background=AppAppearance.background(this); surface=color(R.color.surface); primary=color(R.color.primary_text);
        secondary=color(R.color.secondary_text); accent=color(R.color.accent); accentSurface=color(R.color.accent_surface); divider=color(R.color.divider);
        replay=getIntent().getBooleanExtra("replay", false);
        if(Build.VERSION.SDK_INT>=33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::previousOrExit);
        page=state!=null?state.getInt(PAGE,0):replay?0:getSharedPreferences("settings",MODE_PRIVATE).getInt(PAGE,0);
        page=Math.max(0,Math.min(page,TOPICS.length-1)); build(false);
        if(state!=null) { final int y=state.getInt("scroll",0); scroll.post(()->scroll.scrollTo(0,y)); }
    }
    private void build(boolean announce) {
        LinearLayout root=column(); root.setBackgroundColor(background); root.setPadding(dp(24),dp(16),dp(24),dp(12));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                view.setPadding(dp(24)+bars.left,dp(12)+bars.top,dp(24)+bars.right,dp(12)+bars.bottom);
            } else view.setPadding(dp(24),dp(12)+insets.getSystemWindowInsetTop(),dp(24),dp(12)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout top=row(); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("VOICE MAIL",13,secondary,false); brand.setLetterSpacing(.08f); brand.setSingleLine(true); brand.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button topics=button("Topics",false); topics.setContentDescription("Choose a tutorial topic"); topics.setOnClickListener(v->topics()); top.addView(topics);
        Button skip=button(replay?"Close":"Skip",false); skip.setContentDescription(replay?"Close tutorial and return to Settings":"Skip tutorial and open inbox"); skip.setOnClickListener(v->complete()); top.addView(skip); root.addView(top);

        TextView progress=text((page+1)+" OF "+TOPICS.length+" · "+TOPICS[page].toUpperCase(java.util.Locale.ROOT),12,secondary,false);
        progress.setPadding(0,dp(12),0,dp(12)); progress.setContentDescription("Step "+(page+1)+" of "+TOPICS.length+". "+TOPICS[page]); root.addView(progress);
        LinearLayout indicator=row(); indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        for(int i=0;i<TOPICS.length;i++) {
            View bar=new View(this); bar.setBackground(shape(i<=page?accent:divider,3));
            LinearLayout.LayoutParams part=new LinearLayout.LayoutParams(0,dp(4),1); if(i>0)part.setMarginStart(dp(5)); indicator.addView(bar,part);
        }
        root.addView(indicator);
        scroll=new SlideScrollView(this); scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(true);
        LinearLayout content=column(); content.setPadding(0,dp(24),0,dp(20));
        Illustration icon=new Illustration(this,page); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams art=new LinearLayout.LayoutParams(dp(80),dp(80)); art.bottomMargin=dp(22); content.addView(icon,art);
        TextView title=text(TITLES[page],32,primary,true); if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        content.addView(title); TextView summary=text(SUMMARIES[page],16,secondary,false); summary.setPadding(0,dp(12),0,dp(22)); content.addView(summary);
        LinearLayout card=column(); card.setPadding(dp(20),dp(6),dp(20),dp(6)); card.setBackground(shape(surface,24));
        for(int i=0;i<HEADINGS[page].length;i++) {
            if(i>0) { View line=new View(this); line.setBackgroundColor(divider); card.addView(line,new LinearLayout.LayoutParams(-1,dp(1))); }
            TextView heading=text(HEADINGS[page][i],16,primary,true); heading.setPadding(0,dp(16),0,dp(6)); card.addView(heading);
            TextView detail=text(DETAILS[page][i],14,secondary,false); detail.setPadding(0,0,0,dp(16)); card.addView(detail);
        }
        content.addView(card); scroll.addView(content); LinearLayout.LayoutParams body=new LinearLayout.LayoutParams(-1,0,1); body.topMargin=dp(4); root.addView(scroll,body);
        LinearLayout footer=row(); footer.setPadding(0,dp(12),0,0);
        Button back=button("Back",false); back.setEnabled(page>0); if(page==0)back.setAlpha(.45f); back.setOnClickListener(v->move(-1));
        footer.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        Button next=button(page==TOPICS.length-1?(replay?"Done":"Open inbox"):"Next",true); next.setOnClickListener(v->{if(page==TOPICS.length-1)complete();else move(1);});
        LinearLayout.LayoutParams nextSize=new LinearLayout.LayoutParams(0,-2,1); nextSize.setMarginStart(dp(12)); footer.addView(next,nextSize); root.addView(footer);
        if(announce && Build.VERSION.SDK_INT>=28)root.setAccessibilityPaneTitle("Step "+(page+1)+" of "+TOPICS.length+". "+TOPICS[page]);
        setContentView(root);
        if(announce && Build.VERSION.SDK_INT<28)progress.announceForAccessibility("Step "+(page+1)+" of "+TOPICS.length+". "+TOPICS[page]);
    }
    private void move(int delta) { int next=page+delta; if(next>=0&&next<TOPICS.length)go(next); }
    private void go(int next) {
        page=next;
        if(!replay)getSharedPreferences("settings",MODE_PRIVATE).edit().putInt(PAGE,page).apply();
        build(true);
    }
    private void topics() {
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Tutorial topics").setSingleChoiceItems(TOPICS,page,(d,choice)->{d.dismiss();go(choice);})
            .setNegativeButton("Cancel",null).create();
        dialog.show(); dialog.getWindow().setBackgroundDrawable(shape(surface,28)); dialog.getButton(-2).setAllCaps(false);
    }
    private void complete() { acknowledge(this); setResult(RESULT_OK); finish(); }
    private void previousOrExit() { if(page>0)move(-1);else complete(); }
    @Override public void onBackPressed() { previousOrExit(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putInt(PAGE,page); state.putInt("scroll",scroll.getScrollY()); super.onSaveInstanceState(state); }
    private final class SlideScrollView extends ScrollView {
        private float startX,startY;
        SlideScrollView(Context context) { super(context); }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN) { startX=event.getX(); startY=event.getY(); }
            if(event.getActionMasked()==MotionEvent.ACTION_UP) {
                float dx=event.getX()-startX,dy=event.getY()-startY;
                if(Math.abs(dx)>dp(72)&&Math.abs(dx)>Math.abs(dy)*1.5f) {
                    MotionEvent cancel=MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL); super.onTouchEvent(cancel); cancel.recycle();
                    move(dx<0?1:-1); return true;
                }
            }
            return super.onTouchEvent(event);
        }
    }
    private final class Illustration extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private final int topic;
        Illustration(Context context,int topic) { super(context); this.topic=topic; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/80f,getHeight()/80f);
            paint.setStyle(Paint.Style.FILL); paint.setColor(accentSurface); canvas.drawRoundRect(0,0,80,80,24,24,paint);
            paint.setColor(accent); paint.setStrokeWidth(3); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
            if(topic==0) { canvas.drawCircle(27,40,9,paint);canvas.drawCircle(53,40,9,paint);canvas.drawLine(27,49,53,49,paint); }
            else if(topic==1) {
                Path sim=new Path();sim.moveTo(25,18);sim.lineTo(45,18);sim.lineTo(56,29);sim.lineTo(56,61);sim.lineTo(25,61);sim.close();canvas.drawPath(sim,paint);
                canvas.drawRoundRect(32,36,49,51,3,3,paint);canvas.drawLine(40,36,40,51,paint);canvas.drawLine(32,43,49,43,paint);
            } else if(topic==2) { canvas.drawCircle(35,35,14,paint);canvas.drawLine(46,46,59,59,paint); }
            else if(topic==3) {
                paint.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(32,23);play.lineTo(56,40);play.lineTo(32,57);play.close();canvas.drawPath(play,paint);
            } else if(topic==4) {
                canvas.drawLine(40,20,40,47,paint);canvas.drawLine(29,37,40,48,paint);canvas.drawLine(51,37,40,48,paint);
                canvas.drawLine(22,51,22,60,paint);canvas.drawLine(22,60,58,60,paint);canvas.drawLine(58,60,58,51,paint);
            } else if(topic==5) {
                Path bell=new Path();bell.moveTo(23,54);bell.lineTo(28,46);bell.lineTo(28,34);bell.cubicTo(28,15,52,15,52,34);bell.lineTo(52,46);bell.lineTo(57,54);bell.close();canvas.drawPath(bell,paint);canvas.drawArc(34,53,46,64,0,180,false,paint);
            } else {
                canvas.drawCircle(40,40,20,paint);paint.setStyle(Paint.Style.FILL);canvas.drawArc(24,24,56,56,-90,180,true,paint);
            }
            canvas.restore();
        }
    }
    private Button button(String label,boolean filled) {
        Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setTextSize(15);button.setTextColor(filled?background:accent);
        button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(dp(52));button.setMinimumHeight(dp(52));button.setPadding(dp(14),dp(8),dp(14),dp(8));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(divider),shape(filled?accent:background,18),null));return button;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(2),1.05f);
        view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",0));return view;
    }
    private LinearLayout row() { LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);return layout; }
    private LinearLayout column() { LinearLayout layout=row();layout.setOrientation(LinearLayout.VERTICAL);return layout; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape; }
    private int color(int id) { return getResources().getColor(id,getTheme()); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
