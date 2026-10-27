package org.visualvoicemail.app;

import android.app.Activity;
import android.os.Bundle;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private CarrierClient carrier;
    private TextView status;
    private LinearLayout messages;
    private Button refresh;
    private MediaPlayer player;
    private Button activePlay;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        carrier = new CarrierClient(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 100, 40, 40);
        TextView title = new TextView(this);
        title.setText("Voice Mail"); title.setTextSize(30);
        root.addView(title);
        status = new TextView(this);
        status.setText("Connect to your existing Mint voicemail.");
        root.addView(status);
        refresh = new Button(this);
        refresh.setText("Connect voicemail");
        refresh.setOnClickListener(v -> sync());
        root.addView(refresh);
        messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL);
        root.addView(messages);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }

    private void sync() {
        refresh.setEnabled(false); status.setText("Connecting to your carrier mailbox…");
        worker.execute(() -> {
            try {
                java.util.List<JSONObject> inbox = carrier.sync();
                runOnUiThread(() -> {
                    messages.removeAllViews();
                    status.setText(inbox.size() + (inbox.size() == 1 ? " voicemail" : " voicemails"));
                    refresh.setText("Refresh"); refresh.setEnabled(true);
                    for (JSONObject message : inbox) {
                        TextView row = new TextView(this);
                        row.setText(message.optString("returnnumber", "Unknown caller") + "\n"
                            + message.optString("date", "") + " · " + message.optDouble("duration", 0) + " seconds");
                        row.setTextSize(18); row.setPadding(0, 30, 0, 30);
                        messages.addView(row);
                        Button play = new Button(this);
                        play.setText("Play voicemail");
                        play.setOnClickListener(v -> play(message, play));
                        messages.addView(play);
                    }
                });
            } catch (Exception error) {
                carrier.saveDiagnostics();
                runOnUiThread(() -> { status.setText(error.getMessage()); refresh.setEnabled(true); });
            }
        });
    }

    private void play(JSONObject message, Button button) {
        if (player != null && activePlay == button) {
            if (player.isPlaying()) { player.pause(); button.setText("Resume"); }
            else { player.start(); button.setText("Pause"); }
            return;
        }
        if (player != null) { player.release(); player = null; }
        if (activePlay != null) activePlay.setText("Play voicemail");
        activePlay = button;
        button.setEnabled(false); button.setText("Downloading…");
        worker.execute(() -> {
            try {
                java.io.File recording = carrier.audio(message);
                runOnUiThread(() -> {
                    try {
                        player = new MediaPlayer();
                        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        player.setDataSource(recording.getAbsolutePath());
                        player.setOnPreparedListener(prepared -> {
                            prepared.start(); button.setEnabled(true); button.setText("Pause");
                            try { carrier.diagnostics().put("playback_started", true)
                                .put("playback_duration_ms", prepared.getDuration()); carrier.saveDiagnostics(); }
                            catch (Exception ignored) { }
                        });
                        player.setOnCompletionListener(completed -> {
                            button.setText("Play again");
                            try { carrier.diagnostics().put("playback_completed", true); carrier.saveDiagnostics(); }
                            catch (Exception ignored) { }
                        });
                        player.setOnErrorListener((failed, what, extra) -> {
                            status.setText("Could not play this recording (" + what + ", " + extra + ").");
                            button.setEnabled(true); button.setText("Try playback again");
                            failed.release(); player = null; return true;
                        });
                        player.prepareAsync();
                    } catch (Exception error) { status.setText(error.getMessage()); button.setEnabled(true); }
                });
            } catch (Exception error) {
                carrier.saveDiagnostics();
                runOnUiThread(() -> { status.setText(error.getMessage()); button.setEnabled(true); button.setText("Retry download"); });
            }
        });
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null && player.isPlaying()) { player.pause(); if (activePlay != null) activePlay.setText("Resume"); }
    }

    @Override protected void onDestroy() {
        if (player != null) player.release();
        worker.shutdown();
        super.onDestroy();
    }
}
