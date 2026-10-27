package org.visualvoicemail.app;

import android.app.Activity;
import android.os.Bundle;
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
                    }
                });
            } catch (Exception error) {
                carrier.saveDiagnostics();
                runOnUiThread(() -> { status.setText(error.getMessage()); refresh.setEnabled(true); });
            }
        });
    }
}
