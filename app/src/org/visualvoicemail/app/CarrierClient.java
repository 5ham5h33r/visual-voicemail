package org.visualvoicemail.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Original client for the observed carrier protocol. Never logs authentication values. */
public final class CarrierClient {
    private static final String ENTITLEMENT = "https://eas3.msg.t-mobile.com/generic_devices";
    private static final String MAILBOX = "https://cpaas-geo.t-mobile.com/v1/daasvmsvc/";
    // Public protocol profile/version used by this carrier's over-the-top VVM interface.
    private static final String MAILBOX_PROFILE = "VVMOTT";
    private static final String MAILBOX_PROFILE_VERSION = "10";
    private final Context context;
    private final ConnectivityManager connectivity;
    private String number = "";
    private String token = "";
    private long tokenExpires;
    private final PrivateStore store;
    private int nextMessage = 1;
    private final JSONObject diagnostics = new JSONObject();

    public CarrierClient(Context context) {
        this.context = context.getApplicationContext();
        connectivity = context.getSystemService(ConnectivityManager.class);
        store = new PrivateStore(context);
        JSONObject session = store.get("session.bin");
        if (session != null) {
            number = session.optString("number", "");
            token = session.optString("token", "");
            tokenExpires = session.optLong("expires", 0);
            try { diagnostics.put("session_restored", !token.isEmpty()); } catch (Exception ignored) { }
        }
    }

    public JSONObject diagnostics() { return diagnostics; }

    public long lastSyncTime() {
        JSONObject saved = store.get("inbox.bin");
        return saved == null ? 0 : saved.optLong("synced_at", 0);
    }

    public synchronized List<JSONObject> reconnect() throws Exception {
        token = ""; tokenExpires = 0; return sync();
    }

    private void stage(String name) throws Exception {
        diagnostics.put("stage", name);
        saveDiagnostics();
    }

    public synchronized List<JSONObject> sync() throws Exception {
        stage("connecting");
        if (token.isEmpty() || tokenExpires < System.currentTimeMillis() + 60000) authenticate();
        stage("listing");
        LinkedHashMap<String, JSONObject> unique = new LinkedHashMap<>();
        int offset = 0;
        while (true) {
            JSONObject page;
            String path = "voicemails?audiotype=audio%2Famr&offset=" + offset + "&count=100";
            try { page = mailbox(path); }
            catch (AuthExpired expired) { token = ""; authenticate(); page = mailbox(path); }
            JSONArray messages = page.optJSONArray("voicemails");
            int count = messages == null ? 0 : messages.length();
            int previousSize = unique.size();
            for (int i = 0; i < count; i++) {
                JSONObject message = messages.getJSONObject(i);
                String id = message.optString("objectid", "");
                if (!id.isEmpty()) unique.put(id, message);
            }
            if (count < 100 || unique.size() == previousSize) break;
            offset += count;
            if (offset >= 10000) throw new IOException("This mailbox is too large to sync in one request. Please try again later.");
        }
        List<JSONObject> result = new ArrayList<>(unique.values());
        JSONArray saved = new JSONArray();
        for (JSONObject message : result) saved.put(message);
        diagnostics.put("encrypted_inbox_saved", store.put("inbox.bin", new JSONObject()
            .put("messages", saved).put("synced_at", System.currentTimeMillis())));
        diagnostics.put("message_count", result.size());
        stage("mailbox_loaded");
        return result;
    }

    private void authenticate() throws Exception {
        stage("acquiring_cellular");
        try (Cellular cellular = new Cellular()) {
            Network network = cellular.network;
            String id = deviceId();
            stage("identifying");
            JSONArray identification = entitlement(network, new JSONArray()
                .put(authentication(id)).put(new JSONObject().put("method", "getMSISDN")
                    .put("message-id", nextMessage++).put("device-id", id)));
            JSONObject subscriber = identification.getJSONObject(1);
            requireSuccess(subscriber, "identification");
            number = subscriber.getString("msisdn");
            String fingerprint = subscriber.getString("service-fingerprint");
            JSONObject service = new JSONObject().put("service-name", "vvm").put("service-fingerprint", fingerprint);
            stage("establishing_service");
            JSONArray provision = entitlement(network, new JSONArray().put(authentication(id))
                .put(new JSONObject().put("method", "manageService").put("message-id", nextMessage++)
                    .put("operation", 0).put("device-id", id).put("service-item", service)));
            JSONObject provisionResult = provision.getJSONObject(1);
            diagnostics.put("service_response", provisionResult.optInt("response-code", -1));
            requireSuccess(provisionResult, "service registration");
            JSONObject returnedInstance = provisionResult.getJSONObject("service-instance");
            String instanceNumber = returnedInstance.optString("msisdn", number);
            diagnostics.put("has_service_instance_id", !returnedInstance.optString("service-instance-id", "").isEmpty());
            diagnostics.put("instance_number_matches_identification", number.equals(instanceNumber));
            JSONObject instance = new JSONObject().put("service-name", "vvm")
                .put("service-instance-id", returnedInstance.getString("service-instance-id"))
                .put("msisdn", instanceNumber);
            stage("requesting_token");
            JSONArray access = entitlement(network, new JSONArray().put(authentication(id))
                .put(new JSONObject().put("method", "manageService").put("message-id", nextMessage++)
                    .put("operation", 5).put("device-id", id).put("service-item", service)
                    .put("service-instance", instance)));
            JSONObject accessResult = access.getJSONObject(1);
            diagnostics.put("token_auth_response", access.getJSONObject(0).optInt("response-code", -1));
            diagnostics.put("token_response", accessResult.optInt("response-code", -1));
            requireSuccess(accessResult, "mailbox access");
            JSONObject credentials = accessResult.getJSONObject("instance-token");
            String rawToken = credentials.getString("service-instance-token");
            if (rawToken.isEmpty()) throw new IOException("The carrier returned no mailbox access token.");
            token = Base64.encodeToString(rawToken.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            try { tokenExpires = java.time.OffsetDateTime.parse(credentials.getString("expiration-time")).toInstant().toEpochMilli(); }
            catch (Exception ignored) { tokenExpires = System.currentTimeMillis() + 5 * 60 * 1000; }
            diagnostics.put("encrypted_session_saved", store.put("session.bin", new JSONObject()
                .put("number", number).put("token", token).put("expires", tokenExpires)));
            diagnostics.put("has_mailbox_token", true);
            stage("authenticated");
        }
    }

    private JSONObject authentication(String id) throws Exception {
        return new JSONObject().put("method", "3gppAuthentication").put("message-id", nextMessage++)
            .put("device-type", 0).put("os-type", 0).put("device-id", id);
    }

    private void requireSuccess(JSONObject message, String purpose) throws IOException {
        int code = message.optInt("response-code", -1);
        if (code != 1000) throw new IOException("Carrier " + purpose + " returned code " + code + ".");
    }

    private String deviceId() {
        String localId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return Base64.encodeToString(("AID:" + localId + SubscriptionManager.getDefaultDataSubscriptionId())
            .getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private JSONArray entitlement(Network network, JSONArray messages) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(messages.toString().getBytes(StandardCharsets.UTF_8));
        }
        HttpsURLConnection connection = (HttpsURLConnection) network.openConnection(new URL(ENTITLEMENT));
        try {
            configure(connection);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Encoding", "gzip");
            connection.setRequestProperty("x-application-category", "vvm");
            connection.setFixedLengthStreamingMode(bytes.size());
            try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes.toByteArray()); }
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("Carrier connection returned HTTP " + code + ".");
            return new JSONArray(new String(read(connection.getInputStream(), 1024 * 1024), StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    private JSONObject mailbox(String path) throws Exception {
        return mailbox(path, "GET", null);
    }

    private JSONObject mailbox(String path, String method, JSONObject body) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(MAILBOX + path).openConnection();
        try {
            configure(connection);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("msisdn", "tel:+" + number.replaceFirst("^\\+", ""));
            connection.setRequestProperty("client-id", MAILBOX_PROFILE);
            connection.setRequestProperty("client-type", "vvm");
            connection.setRequestProperty("client-version", MAILBOX_PROFILE_VERSION);
            connection.setRequestProperty("X-Correlation-ID", UUID.randomUUID().toString());
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            diagnostics.put("mailbox_http_status", code);
            if (code == 401) throw new AuthExpired();
            if (code < 200 || code >= 300) throw new IOException("Mailbox request returned HTTP " + code + ".");
            if (code == 204) return new JSONObject();
            byte[] response = read(connection.getInputStream(), 8 * 1024 * 1024);
            return response.length == 0 ? new JSONObject() : new JSONObject(new String(response, StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    /** Seen is added with PUT; unread removes that flag, never the voicemail itself. */
    public synchronized List<JSONObject> setRead(String id, boolean read) throws Exception {
        if (id.isEmpty()) throw new IOException("This message has no identifier.");
        diagnostics.put("read_update_verified", false);
        stage("updating_read_status");
        if (token.isEmpty() || tokenExpires < System.currentTimeMillis() + 60000) authenticate();
        String path = read ? "voicemail/flag" : "voicemail/flag?objectid="
            + java.net.URLEncoder.encode(id, "UTF-8") + "&flag=Seen";
        JSONObject body = read ? new JSONObject().put("voicemails", new JSONArray().put(
            new JSONObject().put("objectId", id).put("flag", "Seen"))) : null;
        JSONObject result;
        try { result = mailbox(path, read ? "PUT" : "DELETE", body); }
        catch (AuthExpired expired) { token = ""; authenticate(); result = mailbox(path, read ? "PUT" : "DELETE", body); }
        JSONArray errors = result.optJSONArray("errors");
        if (errors != null && errors.length() > 0) throw new IOException("The carrier could not change this message's read status.");
        diagnostics.put("read_update_http_status", diagnostics.optInt("mailbox_http_status"));
        List<JSONObject> refreshed = sync();
        for (JSONObject message : refreshed) if (id.equals(message.optString("objectid"))) {
            boolean verified = InboxQuery.isRead(message.optString("flag")) == read;
            diagnostics.put("read_update_verified", verified); saveDiagnostics();
            if (!verified) throw new IOException("Read status hasn't been confirmed by the carrier. Refresh to check.");
            return refreshed;
        }
        throw new IOException("This message is no longer in the mailbox. Refresh to check.");
    }

    private File audioFile(JSONObject message) throws Exception {
        String id = message.optString("objectid", "");
        if (id.isEmpty()) throw new IOException("This message has no identifier.");
        StringBuilder filename = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)))
            filename.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        File directory = new File(context.getFilesDir(), "audio");
        return new File(directory, filename + ".audio");
    }

    /** Local lookup must stay independent of a potentially slow mailbox sync. */
    public File cachedAudio(JSONObject message) {
        try {
            File file = audioFile(message);
            return file.isFile() && file.length() > 0 ? file : null;
        } catch (Exception ignored) { return null; }
    }

    public synchronized File audio(JSONObject message) throws Exception {
        stage("downloading_audio");
        File file = audioFile(message);
        if (file.isFile() && file.length() > 0) {
            diagnostics.put("audio_from_cache", true).put("audio_bytes", file.length());
            stage("audio_ready"); return file;
        }
        diagnostics.put("audio_from_cache", false);
        JSONObject object = message.optJSONObject("objectaudio");
        if (object == null) throw new IOException("The carrier supplied no audio for this message.");
        byte[] bytes;
        String inline = object.optString("audiopayload", "");
        if (!inline.isEmpty() && !object.isNull("audiopayload")) bytes = Base64.decode(inline, Base64.DEFAULT);
        else {
            URL url = new URL(object.getString("audiopayloadurl"));
            bytes = payload(url);
        }
        if (bytes.length == 0) throw new IOException("The recording is empty.");
        File directory = file.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create audio storage.");
        android.util.AtomicFile recording = new android.util.AtomicFile(file);
        FileOutputStream output = recording.startWrite();
        try { output.write(bytes); recording.finishWrite(output); }
        catch (Exception error) { recording.failWrite(output); throw error; }
        diagnostics.put("audio_bytes", bytes.length);
        stage("audio_downloaded");
        return file;
    }

    private byte[] payload(URL url) throws Exception {
        for (int redirect = 0; redirect < 4; redirect++) {
            if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Recording URL must use HTTPS.");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            try {
                configure(connection);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    url = new URL(url, connection.getHeaderField("Location"));
                    continue;
                }
                if (code != 200) throw new IOException("Recording request returned HTTP " + code + ".");
                return read(connection.getInputStream(), 25 * 1024 * 1024);
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many recording redirects.");
    }

    private void configure(HttpsURLConnection connection) {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "VisualVoiceMail org.visualvoicemail.app 0.7 Android");
    }

    private static byte[] read(InputStream input, int maximum) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                if (output.size() + length > maximum) throw new IOException("Carrier response exceeded the size limit.");
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }

    public void saveDiagnostics() {
        try (FileOutputStream output = context.openFileOutput("diagnostics.json", Context.MODE_PRIVATE)) {
            output.write(diagnostics.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    public List<JSONObject> cachedInbox() {
        List<JSONObject> result = new ArrayList<>();
        JSONObject saved = store.get("inbox.bin");
        JSONArray messages = saved == null ? null : saved.optJSONArray("messages");
        if (messages != null) for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null) result.add(message);
        }
        return result;
    }

    public boolean hasSession() { return !token.isEmpty(); }

    private static final class AuthExpired extends IOException { private static final long serialVersionUID = 1L; }

    private final class Cellular implements AutoCloseable {
        private final ConnectivityManager.NetworkCallback callback;
        private Network network;
        Cellular() throws Exception {
            CountDownLatch ready = new CountDownLatch(1);
            callback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network available) { network = available; ready.countDown(); }
                @Override public void onUnavailable() { ready.countDown(); }
            };
            connectivity.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback, 20000);
            if (!ready.await(22, TimeUnit.SECONDS) || network == null) {
                close();
                throw new IOException("Turn on mobile data and choose Mint as your data SIM, then try again.");
            }
        }
        @Override public void close() { try { connectivity.unregisterNetworkCallback(callback); } catch (Exception ignored) { } }
    }
}
