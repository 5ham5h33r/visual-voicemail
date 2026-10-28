package org.visualvoicemail.checks;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;

import org.json.JSONObject;

import java.lang.reflect.*;
import java.util.*;

/** Real TalkBack remains enabled; public emulator fixtures and accessibility focus only. */
public final class AccessibilityWalkthroughCheck extends DeviceCheckInstrumentation {
    private Activity screen;
    private UiAutomation automation;
    private int checks, focused;
    private String page;

    private Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private void invoke(String name) throws Exception {
        Method m = screen.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        runOnMainSync(
                () -> {
                    try {
                        m.invoke(screen);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        waitForIdleSync();
    }

    private void check(boolean good, String detail) throws Exception {
        checks++;
        if (!good) throw new Exception(page + ": " + detail);
    }

    private AccessibilityNodeInfo root() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 6000;
        do {
            AccessibilityNodeInfo node = automation.getRootInActiveWindow();
            if (node != null
                    && getTargetContext().getPackageName().contentEquals(node.getPackageName()))
                return node;
            Thread.sleep(60);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new Exception(page + ": app accessibility window unavailable");
    }

    private boolean label(AccessibilityNodeInfo node) {
        return node.getContentDescription() != null && node.getContentDescription().length() > 0
                || node.getText() != null && node.getText().length() > 0;
    }

    private void walk(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> scrolls)
            throws Exception {
        if (!node.isVisibleToUser()) return;
        if (node.isScrollable()) scrolls.add(node);
        if (node.isImportantForAccessibility()
                && node.isEnabled()
                && (node.isClickable() || node.isEditable())) {
            check(label(node), "unlabeled interactive control");
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            check(bounds.width() > 0 && bounds.height() > 0, "empty accessibility bounds");
            boolean accepted =
                    node.isAccessibilityFocused()
                            || node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            if (!accepted) {
                if (!node.refresh() || !node.isVisibleToUser()) return;
                accepted = node.isAccessibilityFocused();
            }
            check(accepted, "accessibility focus rejected for " + node.getClassName());
            focused++;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) walk(child, scrolls);
        }
    }

    private void scan() throws Exception {
        for (int step = 0; step < 10; step++) {
            List<AccessibilityNodeInfo> scrolls = new ArrayList<>();
            walk(root(), scrolls);
            boolean moved = false;
            for (AccessibilityNodeInfo scroll : scrolls)
                if ((scroll.getActions() & AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != 0) {
                    moved = scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    if (moved) break;
                }
            if (!moved) return;
            Thread.sleep(250);
        }
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        Context c = getTargetContext();
        SharedPreferences prefs = c.getSharedPreferences("settings", 0);
        Map<String, ?> before = prefs.getAll();
        try {
            page = "initialization";
            check(
                    Arrays.asList("ranchu", "goldfish").contains(Build.HARDWARE),
                    "emulator required");
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo serviceInfo = automation.getServiceInfo();
            serviceInfo.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            automation.setServiceInfo(serviceInfo);
            AccessibilityManager manager = c.getSystemService(AccessibilityManager.class);
            boolean talkback = false;
            long deadline = SystemClock.uptimeMillis() + 6000;
            do {
                for (AccessibilityServiceInfo service :
                        manager.getEnabledAccessibilityServiceList(
                                AccessibilityServiceInfo.FEEDBACK_SPOKEN))
                    if (service.getResolveInfo()
                            .serviceInfo
                            .packageName
                            .equals("com.google.android.marvin.talkback")) talkback = true;
                if (talkback) break;
                Thread.sleep(100);
            } while (SystemClock.uptimeMillis() < deadline);
            check(talkback, "TalkBack service must be enabled");
            out.putBoolean("talkback_active", true);
            prefs.edit()
                    .putBoolean("auto_transcribe", false)
                    .putBoolean("refresh_on_open", false)
                    .putBoolean("guide_recording", false)
                    .putBoolean("tutorial_seen", true)
                    .putBoolean("reduce_motion", true)
                    .commit();
            Class<?> carrier = Class.forName("org.visualvoicemail.app.CarrierClient");
            Object client = carrier.getMethod("get", Context.class).invoke(null, c);
            List<?> inbox = (List<?>) carrier.getMethod("cachedInbox").invoke(client);
            for (Object item : inbox)
                check(
                        ((JSONObject) item).optString("objectid").startsWith("emulator-sample-"),
                        "sample inbox required");
            String id = ((JSONObject) inbox.get(0)).getString("objectid");
            for (String name :
                    new String[] {
                        "MainActivity",
                        "RecordingActivity",
                        "SettingsActivity",
                        "TranscriptionSettingsActivity",
                        "GreetingActivity",
                        "TutorialActivity"
                    }) {
                page = name;
                screen =
                        startActivitySync(
                                new Intent()
                                        .setClassName(c, "org.visualvoicemail.app." + name)
                                        .putExtra("id", id)
                                        .putExtra("caller", "Sample caller")
                                        .putExtra("replay", true)
                                        .addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                Thread.sleep(450);
                scan();
                if (name.equals("MainActivity")) {
                    runOnMainSync(() -> ((ScrollView) quietField("inboxScroll")).scrollTo(0, 0));
                    invoke("enterSelection");
                    scan();
                    Map<?, ?> rows = (Map<?, ?>) field(screen, "rows");
                    Object row = rows.values().iterator().next();
                    CheckBox box = (CheckBox) field(row, "check");
                    AccessibilityNodeInfo node = box.createAccessibilityNodeInfo();
                    check(node.isCheckable(), "selection must expose native checkbox state");
                    boolean selected = node.isChecked();
                    runOnMainSync(() -> box.performClick());
                    check(box.isChecked() != selected, "selection action must toggle");
                    invoke("clearSelection");
                    Field pending = screen.getClass().getDeclaredField("guidePending");
                    pending.setAccessible(true);
                    pending.setBoolean(screen, true);
                    invoke("showGuide");
                    scan();
                    AccessibilityNodeInfo guide = root();
                    List<AccessibilityNodeInfo> skip =
                            guide.findAccessibilityNodeInfosByText("Skip tour");
                    check(!skip.isEmpty(), "tour skip action discoverable");
                    skip.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                Activity done = screen;
                runOnMainSync(done::finish);
                screen = null;
            }
            check(focused > 60, "interactive focus coverage");
            out.putBoolean("passed", true);
        } catch (Throwable error) {
            while (error.getCause() != null) error = error.getCause();
            out.putString("failure_class", error.getClass().getSimpleName());
            out.putString(
                    "failure",
                    error.getMessage() != null && error.getMessage().startsWith(page + ":")
                            ? error.getMessage()
                            : page + ": walkthrough failure");
        } finally {
            if (screen != null) runOnMainSync(() -> screen.finish());
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    new String[] {
                        "auto_transcribe",
                        "refresh_on_open",
                        "guide_recording",
                        "tutorial_seen",
                        "reduce_motion"
                    }) {
                Object v = before.get(key);
                if (v instanceof Boolean) restore.putBoolean(key, (Boolean) v);
                else restore.remove(key);
            }
            restore.commit();
        }
        out.putInt("checks", checks);
        out.putInt("focused_controls", focused);
        finish(-1, out);
    }

    private Object quietField(String name) {
        try {
            return field(screen, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
