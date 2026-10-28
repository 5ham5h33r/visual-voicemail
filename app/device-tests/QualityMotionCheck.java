package org.visualvoicemail.checks;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.lang.reflect.*;
import java.util.*;

/** Exercise the real quality control, retained transcripts, and immediate/reduced tap feedback. */
public final class QualityMotionCheck extends DeviceCheckInstrumentation {
    private int checks;
    private Activity screen;
    private Context context;
    private SharedPreferences prefs;

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method m = type.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private void check(boolean value) throws Exception {
        checks++;
        if (!value) throw new Exception("check " + checks);
    }

    private boolean awaitActionWidth(Button action, boolean compact) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4000;
        do {
            waitForIdleSync();
            int width = action.getWidth(),
                    parent = ((View) field(screen, "transcriptStatus")).getWidth();
            View status = (View) field(screen, "transcriptStatus");
            parent -= status.getPaddingLeft() + status.getPaddingRight();
            if (width > 0 && parent > 0 && (compact ? width < parent : width == parent))
                return true;
            Thread.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private View find(View root, String text) {
        if (root instanceof TextView && ((TextView) root).getText().toString().startsWith(text))
            return root;
        if (root instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
                View found = find(((ViewGroup) root).getChildAt(i), text);
                if (found != null) return found;
            }
        return null;
    }

    private View awaitControl(Activity activity, String text) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            waitForIdleSync();
            View view = find(activity.getWindow().getDecorView(), text);
            if (activity.hasWindowFocus() && view != null) return view;
            Thread.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private void touch(View button, int action) {
        runOnMainSync(
                () -> {
                    long now = SystemClock.uptimeMillis();
                    MotionEvent e =
                            MotionEvent.obtain(
                                    now,
                                    now,
                                    action,
                                    button.getWidth() / 2f,
                                    button.getHeight() / 2f,
                                    0);
                    button.dispatchTouchEvent(e);
                    e.recycle();
                });
    }

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        context = getTargetContext();
        prefs = context.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        try {
            Class<?> speech = Class.forName("org.visualvoicemail.app.Transcription"),
                    carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            method(speech, "select", Context.class, String.class).invoke(null, context, "standard");
            Object client = method(carrier, "get", Context.class).invoke(null, context);
            List<?> inbox = (List<?>) method(carrier, "cachedInbox").invoke(client);
            String id = ((JSONObject) inbox.get(0)).getString("objectid");
            Object transcript =
                    method(speech, "cached", Context.class, String.class).invoke(null, context, id);
            check(transcript != null);
            String old = method(transcript.getClass(), "json").invoke(transcript).toString();
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(
                                            context, "org.visualvoicemail.app.RecordingActivity")
                                    .putExtra("id", id)
                                    .putExtra("english", false)
                                    .putExtra("caller", "Device check")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(800);
            Button quality = (Button) field(screen, "qualityControl");
            check(quality.getText().toString().contains("Standard"));
            Activity recording = screen;
            ActivityMonitor picker =
                    addMonitor(
                            "org.visualvoicemail.app.TranscriptionSettingsActivity", null, false);
            runOnMainSync(() -> quality.performClick());
            screen = waitForMonitorWithTimeout(picker, 5000);
            check(screen != null);
            Thread.sleep(400);
            View[] cards = (View[]) field(screen, "choices");
            check(cards.length == 3);
            check(((Button) field(screen, "language")).getText().toString().contains("English"));
            check(
                    ((Button) field(screen, "output"))
                            .getText()
                            .toString()
                            .contains("Original language"));
            View about = find(screen.getWindow().getDecorView(), "About these options");
            runOnMainSync(() -> about.performClick());
            waitForIdleSync();
            List<android.view.accessibility.AccessibilityNodeInfo> copy =
                    java.util.Collections.emptyList();
            long popupDeadline = SystemClock.uptimeMillis() + 5000;
            do {
                android.view.accessibility.AccessibilityNodeInfo popup =
                        getUiAutomation().getRootInActiveWindow();
                if (popup != null)
                    copy = popup.findAccessibilityNodeInfosByText("Standard (Whisper Tiny)");
                if (!copy.isEmpty()) break;
                Thread.sleep(50);
            } while (SystemClock.uptimeMillis() < popupDeadline);
            check(!copy.isEmpty());
            String explanation = copy.get(0).getText().toString();
            check(
                    explanation.contains("\n\nEnhanced (Whisper Base)")
                            && explanation.contains("\n\nHighest quality (Whisper Large v3)"));
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            runOnMainSync(() -> cards[2].performClick());
            check("highest".equals(prefs.getString("transcription_quality", "")));
            check(cards[2].isSelected() && !cards[0].isSelected() && !cards[1].isSelected());
            Object retained =
                    method(speech, "cached", Context.class, String.class).invoke(null, context, id);
            check(old.equals(method(retained.getClass(), "json").invoke(retained).toString()));
            runOnMainSync(() -> cards[1].performClick());
            check("enhanced".equals(prefs.getString("transcription_quality", "")));
            check(cards[1].isSelected());
            Object kept =
                    method(speech, "cached", Context.class, String.class).invoke(null, context, id);
            check(old.equals(method(kept.getClass(), "json").invoke(kept).toString()));
            runOnMainSync(() -> screen.finish());
            screen = recording;
            long resumeDeadline = SystemClock.uptimeMillis() + 5000;
            while ((!screen.hasWindowFocus() || !quality.getText().toString().contains("Enhanced"))
                    && SystemClock.uptimeMillis() < resumeDeadline) Thread.sleep(50);
            check(quality.getText().toString().contains("Enhanced"));
            check(
                    ((TextView) field(screen, "transcriptState"))
                            .getText()
                            .toString()
                            .contains("Saved with"));
            Object shown = field(screen, "transcript");
            Field shownQuality = shown.getClass().getDeclaredField("quality"),
                    shownLanguage = shown.getClass().getDeclaredField("language");
            shownQuality.setAccessible(true);
            shownLanguage.setAccessible(true);
            Object previousQuality = shownQuality.get(shown),
                    previousLanguage = shownLanguage.get(shown);
            shownQuality.set(shown, "highest");
            shownLanguage.set(shown, "en");
            method(speech, "select", Context.class, String.class).invoke(null, context, "highest");
            prefs.edit().putString("speech_language", "fr").commit();
            runOnMainSync(
                    () -> {
                        try {
                            method(recording.getClass(), "updateSpeech").invoke(recording);
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
            String changedLanguage =
                    ((TextView) field(screen, "transcriptState")).getText().toString();
            check(
                    changedLanguage.contains("Language changed: French.")
                            && !changedLanguage.contains("Saved with"));
            waitForIdleSync();
            Button update = (Button) field(screen, "speechAction");
            check(awaitActionWidth(update, true));
            check(update.getHeight() >= 48 * context.getResources().getDisplayMetrics().density);
            android.graphics.Bitmap surface =
                    android.graphics.Bitmap.createBitmap(
                            update.getWidth(),
                            update.getHeight(),
                            android.graphics.Bitmap.Config.ARGB_8888);
            runOnMainSync(() -> update.getBackground().draw(new android.graphics.Canvas(surface)));
            check(
                    android.graphics.Color.alpha(
                                    surface.getPixel(
                                            surface.getWidth() / 2, surface.getHeight() / 2))
                            > 0);
            surface.recycle();
            Field current = screen.getClass().getDeclaredField("transcript");
            current.setAccessible(true);
            current.set(screen, null);
            Field renderedState = screen.getClass().getDeclaredField("lastSpeechState");
            renderedState.setAccessible(true);
            renderedState.set(screen, "");
            runOnMainSync(
                    () -> {
                        try {
                            method(recording.getClass(), "updateSpeech").invoke(recording);
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
            waitForIdleSync();
            check(awaitActionWidth(update, false));
            current.set(screen, shown);
            prefs.edit().putString("speech_language", "en").commit();
            runOnMainSync(
                    () -> {
                        try {
                            method(recording.getClass(), "updateSpeech").invoke(recording);
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
            check(((View) field(screen, "transcriptState")).getVisibility() == View.GONE);
            Object unchanged =
                    method(speech, "cached", Context.class, String.class).invoke(null, context, id);
            check(old.equals(method(unchanged.getClass(), "json").invoke(unchanged).toString()));
            shownQuality.set(shown, previousQuality);
            shownLanguage.set(shown, previousLanguage);
            method(speech, "select", Context.class, String.class).invoke(null, context, "enhanced");
            screen =
                    startActivitySync(
                            new Intent()
                                    .setClassName(
                                            context, "org.visualvoicemail.app.SettingsActivity")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(300);
            Activity settings = screen;
            View qualityRow = find(screen.getWindow().getDecorView(), "Transcription quality");
            check(qualityRow != null);
            ActivityMonitor settingsPicker =
                    addMonitor(
                            "org.visualvoicemail.app.TranscriptionSettingsActivity", null, false);
            runOnMainSync(() -> qualityRow.performClick());
            screen = waitForMonitorWithTimeout(settingsPicker, 5000);
            check(screen != null);
            Thread.sleep(300);
            check(((View[]) field(screen, "choices"))[1].isSelected());
            runOnMainSync(() -> screen.finish());
            screen = settings;
            Thread.sleep(300);
            android.widget.Switch reduce =
                    (android.widget.Switch) awaitControl(screen, "Reduce motion");
            check(reduce != null);
            runOnMainSync(() -> reduce.setChecked(true));
            Class<?> motion = Class.forName("org.visualvoicemail.app.AppMotion");
            check(!(Boolean) method(motion, "enabled", Context.class).invoke(null, context));
            check(screen.getWindow().getAttributes().windowAnimations == 0);
            Class<?> uiClass = Class.forName("org.visualvoicemail.app.Ui");
            Constructor<?> constructor = uiClass.getDeclaredConstructor(Activity.class);
            constructor.setAccessible(true);
            Object ui = constructor.newInstance(screen);
            long[] clicks = {0, 0};
            Button[] sample = {null};
            runOnMainSync(
                    () -> {
                        try {
                            sample[0] =
                                    (Button)
                                            method(
                                                            uiClass,
                                                            "button",
                                                            String.class,
                                                            boolean.class,
                                                            Runnable.class)
                                                    .invoke(
                                                            ui,
                                                            "Motion check",
                                                            true,
                                                            (Runnable)
                                                                    () -> {
                                                                        clicks[0]++;
                                                                        clicks[1] =
                                                                                SystemClock
                                                                                        .uptimeMillis();
                                                                    });
                            ((ViewGroup) field(screen, "content")).addView(sample[0]);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            waitForIdleSync();
            runOnMainSync(() -> reduce.setChecked(false));
            touch(sample[0], MotionEvent.ACTION_DOWN);
            Thread.sleep(100);
            float pressed = sample[0].getScaleX();
            check(
                    android.animation.ValueAnimator.areAnimatorsEnabled()
                            ? pressed < .99f && pressed > .9f
                            : pressed == 1);
            long released = SystemClock.uptimeMillis();
            touch(sample[0], MotionEvent.ACTION_UP);
            waitForIdleSync();
            check(clicks[0] == 1);
            check(clicks[1] - released < 180);
            Thread.sleep(170);
            check(Math.abs(sample[0].getScaleX() - 1) < .001f);
            touch(sample[0], MotionEvent.ACTION_DOWN);
            Thread.sleep(100);
            touch(sample[0], MotionEvent.ACTION_CANCEL);
            Thread.sleep(170);
            check(Math.abs(sample[0].getScaleX() - 1) < .001f);
            check(clicks[0] == 1);
            runOnMainSync(() -> reduce.setChecked(true));
            touch(sample[0], MotionEvent.ACTION_DOWN);
            Thread.sleep(100);
            check(sample[0].getScaleX() == 1);
            touch(sample[0], MotionEvent.ACTION_UP);
            waitForIdleSync();
            check(clicks[0] == 2);
            runOnMainSync(
                    () -> {
                        try {
                            method(motion, "reveal", View.class, int.class, int.class)
                                    .invoke(null, sample[0], 180, 8);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
            check(sample[0].getAlpha() == 1 && sample[0].getTranslationY() == 0);
            check(
                    !(Boolean)
                            method(speech, "select", Context.class, String.class)
                                    .invoke(null, context, "unknown"));
            result.putBoolean("passed", true);
        } catch (Throwable e) {
            result.putString("failure_class", e.getClass().getSimpleName());
            if (e.getMessage() != null && e.getMessage().startsWith("check "))
                result.putString("failure", e.getMessage());
            result.putBoolean("passed", false);
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            for (String key :
                    new String[] {"transcription_quality", "reduce_motion", "speech_language"}) {
                Object v = before.get(key);
                if (v instanceof String) edit.putString(key, (String) v);
                else if (v instanceof Boolean) edit.putBoolean(key, (Boolean) v);
                else edit.remove(key);
            }
            edit.commit();
            result.putInt("checks", checks);
            finish(-1, result);
        }
    }
}
