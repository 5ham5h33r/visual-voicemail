package org.visualvoicemail.probe;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Original, read-only interoperability probe. No mailbox provisioning or access. */
public final class MainActivity extends Activity {
    private static final String ENDPOINT = "https://eas3.msg.t-mobile.com/generic_devices";
    private TextView output;
    private Button cellular;
    private Button baseline;
    private final AtomicBoolean running = new AtomicBoolean();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("Visual Voice Mail · Research Probe");
        title.setTextSize(22);
        layout.addView(title);
        TextView scope = new TextView(this);
        scope.setText("Tests subscriber identification using our own app identity. "
            + "No dialer role, phone-state permission, mailbox changes, or push registration. "
            + "Only response codes and presence flags are saved; subscriber numbers and tokens are omitted.");
        layout.addView(scope);
        baseline = new Button(this);
        baseline.setText("Test default network (control)");
        baseline.setOnClickListener(v -> start(false));
        layout.addView(baseline);
        cellular = new Button(this);
        cellular.setText("Test cellular network");
        cellular.setOnClickListener(v -> start(true));
        layout.addView(cellular);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText("Ready. Requests go only to T-Mobile's entitlement endpoint.");
        layout.addView(output);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }

    private void start(boolean useCellular) {
        if (!running.compareAndSet(false, true)) return;
        cellular.setEnabled(false);
        baseline.setEnabled(false);
        output.setText("Requesting " + (useCellular ? "cellular" : "default") + " network…");
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (!useCellular) {
            Network network = cm.getActiveNetwork();
            if (network == null) finishResult(error("No active network"));
            else new Thread(() -> probe(network, false, null)).start();
            return;
        }
        AtomicBoolean handled = new AtomicBoolean();
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                if (handled.compareAndSet(false, true)) {
                    new Thread(() -> probe(network, true, this)).start();
                }
            }
            @Override public void onUnavailable() {
                if (handled.compareAndSet(false, true)) {
                    finishResult(error("Cellular network unavailable or timed out"));
                }
            }
        };
        try {
            cm.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback, 20000);
        } catch (Exception e) {
            finishResult(error(e.getClass().getSimpleName()));
        }
    }

    private void probe(Network network, boolean boundCellular, ConnectivityManager.NetworkCallback callback) {
        JSONObject result = new JSONObject();
        HttpsURLConnection connection = null;
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities nc = cm.getNetworkCapabilities(network);
            result.put("requested_cellular", boundCellular);
            result.put("transport_cellular", nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
            result.put("transport_wifi", nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
            result.put("carrier_privileges", getSystemService(TelephonyManager.class).hasCarrierPrivileges());
            result.put("default_dialer", getSystemService(TelecomManager.class).getDefaultDialerPackage());
            result.put("app_id", getPackageName());
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            String deviceId = Base64.encodeToString(("AID:" + androidId
                + SubscriptionManager.getDefaultDataSubscriptionId()).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            JSONArray request = new JSONArray()
                .put(new JSONObject().put("method", "3gppAuthentication").put("message-id", 1)
                    .put("os-type", 0).put("device-type", 0).put("device-id", deviceId))
                .put(new JSONObject().put("method", "getMSISDN").put("message-id", 2).put("device-id", deviceId));
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            connection = (HttpsURLConnection) network.openConnection(new URL(ENDPOINT));
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Encoding", "gzip");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-application-category", "vvm");
            connection.setRequestProperty("User-Agent", "VisualVoiceMailResearch org.visualvoicemail.probe 0.1 Android");
            byte[] payload = compressed.toByteArray();
            connection.setFixedLengthStreamingMode(payload.length);
            try (java.io.OutputStream stream = connection.getOutputStream()) { stream.write(payload); }
            int status = connection.getResponseCode();
            result.put("http_status", status);
            InputStream body = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            if (body != null) try (InputStream stream = body) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (response.size() + count > 65536) throw new IllegalStateException("Response too large");
                    response.write(buffer, 0, count);
                }
            }
            result.put("response_bytes", response.size());
            JSONArray summaries = new JSONArray();
            try {
                JSONArray messages = new JSONArray(response.toString("UTF-8"));
                for (int i = 0; i < messages.length(); i++) {
                    JSONObject message = messages.getJSONObject(i);
                    JSONObject summary = new JSONObject();
                    summary.put("message_id", message.optInt("message-id", -1));
                    summary.put("response_code", message.optInt("response-code", -1));
                    summary.put("has_msisdn", hasValue(message, "msisdn"));
                    summary.put("has_service_fingerprint", hasValue(message, "service-fingerprint"));
                    summary.put("has_aka_challenge", hasValue(message, "aka-challenge"));
                    summary.put("has_aka_token", hasValue(message, "aka-token"));
                    summaries.put(summary);
                }
                result.put("messages", summaries);
            } catch (org.json.JSONException ignored) {
                result.put("json_array_response", false);
            }
        } catch (Exception e) {
            try { result.put("error_type", e.getClass().getSimpleName()); } catch (Exception ignored) { }
        } finally {
            if (connection != null) connection.disconnect();
            if (callback != null) {
                try { getSystemService(ConnectivityManager.class).unregisterNetworkCallback(callback); }
                catch (Exception ignored) { }
            }
        }
        finishResult(result);
    }

    private JSONObject error(String message) {
        JSONObject json = new JSONObject();
        try { json.put("error", message); } catch (Exception ignored) { }
        return json;
    }

    private static boolean hasValue(JSONObject object, String key) {
        return object.has(key) && !object.isNull(key) && !object.optString(key, "").isEmpty();
    }

    private void finishResult(JSONObject result) {
        try {
            try (java.io.FileOutputStream file = openFileOutput("result.json", MODE_PRIVATE)) {
                file.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
        runOnUiThread(() -> {
            try { output.setText(result.toString(2)); } catch (Exception ignored) { }
            cellular.setEnabled(true);
            baseline.setEnabled(true);
            running.set(false);
        });
    }
}
