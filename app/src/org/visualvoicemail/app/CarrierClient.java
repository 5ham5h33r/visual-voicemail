package org.visualvoicemail.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.CancellationSignal;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Original client for the observed carrier protocol. Never logs authentication values. */
public final class CarrierClient {
    private static CarrierClient instance;
    public static synchronized CarrierClient get(Context context) {
        if (instance == null) instance = new CarrierClient(context);
        return instance;
    }
    private static final String ENTITLEMENT = "https://eas3.msg.t-mobile.com/generic_devices";
    private static final String MAILBOX = "https://cpaas-geo.t-mobile.com/v1/daasvmsvc/";
    // Public protocol profile/version used by this carrier's over-the-top VVM interface.
    private static final String MAILBOX_PROFILE = "VVMOTT";
    private static final String MAILBOX_PROFILE_VERSION = "10";
    private final Context context;
    private final ConnectivityManager connectivity;
    private String number = "";
    private volatile String token = "";
    private CancellationSignal cancellation;
    private long tokenExpires;
    private final PrivateStore store;
    private int nextMessage = 1;
    private final Map<String,Object> diagnostics = new ConcurrentHashMap<>();
    private final Object diagnosticsFileLock = new Object();

    private CarrierClient(Context context) {
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

    public Map<String,Object> diagnostics() { return diagnostics; }

    public long lastSyncTime() {
        JSONObject saved = store.get("inbox.bin");
        return saved == null ? 0 : saved.optLong("synced_at", 0);
    }

    public synchronized List<JSONObject> reconnect() throws Exception {
        token = ""; tokenExpires = 0; return sync();
    }

    private void stage(String name) throws Exception {
        if (cancellation != null) cancellation.throwIfCanceled();
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
        if (cancellation != null) cancellation.throwIfCanceled();
        JSONArray saved = new JSONArray();
        for (JSONObject message : result) saved.put(message);
        boolean persisted = store.put("inbox.bin", new JSONObject()
            .put("messages", saved).put("synced_at", System.currentTimeMillis()));
        diagnostics.put("encrypted_inbox_saved", persisted);
        if (!persisted) throw new IOException("The mailbox couldn't be saved on this phone. Free some storage and retry.");
        diagnostics.put("message_count", result.size());
        stage("mailbox_loaded");
        VoicemailAlerts.onMailbox(context, result);
        VoicemailAlerts.ensureScheduled(context);
        return SavedMailbox.get(context).merge(result, true);
    }

    /** Background work shares the same client/monitor as foreground mailbox operations. */
    public synchronized List<JSONObject> sync(CancellationSignal signal) throws Exception {
        signal.throwIfCanceled(); cancellation = signal;
        try { return sync(); }
        finally { signal.setOnCancelListener(null); cancellation = null; }
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
        } finally { disconnect(connection); }
    }

    private JSONObject mailbox(String path) throws Exception {
        return mailbox(path, "GET", null);
    }

    private JSONObject mailbox(String path, String method, JSONObject body) throws Exception {
        return mailboxBytes(path, method, body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private JSONObject mailboxBytes(String path, String method, byte[] bytes, String contentType) throws Exception {
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
            if (bytes != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
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
        } finally { disconnect(connection); }
    }

    private JSONObject greetingRequest(String path,String method,byte[] bytes,String type)throws Exception {
        if(token.isEmpty()||tokenExpires<System.currentTimeMillis()+60000)authenticate();
        try{return mailboxBytes(path,method,bytes,type);}
        catch(AuthExpired expired){token="";authenticate();return mailboxBytes(path,method,bytes,type);}
    }

    synchronized JSONArray greetings()throws Exception {
        Map<String,JSONObject> unique=new LinkedHashMap<>();int offset=0;
        while(true) {
            JSONObject response=greetingRequest("greeting?audiotype=audio%2Famr&offset="+offset+"&count=100","GET",null,"application/json");
            JSONArray page=response.optJSONArray("greetings");
            if(page==null)throw new IOException("The carrier didn't return a greeting list.");
            int before=unique.size();
            for(int i=0;i<page.length();i++){JSONObject item=page.getJSONObject(i);String id=item.optString("objectid");if(!id.isEmpty())unique.put(id,item);}
            if(page.length()<100||unique.size()==before)break;
            offset+=page.length();if(offset>=10000)throw new IOException("The greeting library is too large to check in one request.");
        }
        JSONArray list=new JSONArray();for(JSONObject item:unique.values())list.put(item);
        if(!store.put("greetings.bin",new JSONObject().put("greetings",list)))throw new IOException("Couldn't save greeting details on this phone.");
        return list;
    }
    JSONArray cachedGreetings(){JSONObject saved=store.get("greetings.bin");JSONArray list=saved==null?null:saved.optJSONArray("greetings");return list==null?new JSONArray():list;}
    synchronized File greetingAudio(JSONObject greeting)throws Exception {
        JSONObject message=new JSONObject(greeting.toString());message.put("objectid","greeting-"+greeting.optString("objectid"));
        return audio(message);
    }
    synchronized String saveGreeting(File audio,int seconds)throws Exception {
        String hash=GreetingAudio.hash(audio);
        if(GreetingAudio.duration(audio)<1000)throw new IOException("Record at least one second for your greeting.");
        JSONObject previous=store.get("greeting-operation.bin");
        final JSONObject operation=previous!=null&&hash.equals(previous.optString("hash"))?previous:
                new JSONObject().put("hash",hash).put("title","Personal greeting "+UUID.randomUUID().toString().substring(0,8));
        if(!store.put("greeting-operation.bin",operation))throw new IOException("Couldn't save greeting progress. Nothing was uploaded.");
        final String title=operation.getString("title");
        return GreetingSave.run(title,operation.optString("uploaded_id"),new GreetingSave.Backend(){
            @Override public Map<String,GreetingSave.Entry> refresh()throws Exception {
                Map<String,GreetingSave.Entry> result=new LinkedHashMap<>();JSONArray items=greetings();
                for(int i=0;i<items.length();i++) {
                    JSONObject item=items.getJSONObject(i);String id=item.optString("objectid");
                    if(!id.isEmpty())result.put(id,new GreetingSave.Entry(id,item.optString("title"),"personal".equalsIgnoreCase(item.optString("type")),"CNS-Greeting-On".equalsIgnoreCase(item.optString("flag"))));
                }return result;
            }
            @Override public String uploadInactive(String name)throws Exception {
                JSONObject quota=greetingRequest("greeting/quota","GET",null,"application/json").optJSONObject("quota");
                if(quota!=null&&quota.optInt("maxnumberofallowedobjects",0)>0&&quota.optInt("currentobjectcount",0)>=quota.optInt("maxnumberofallowedobjects"))
                    throw new GreetingSave.Rejected("Your carrier's greeting storage is full. Call voicemail to manage it.");
                GreetingMultipart payload=new GreetingMultipart(audio,seconds,name);
                JSONObject uploaded=greetingRequest("greeting","POST",payload.bytes,payload.type);
                return uploaded.optString("objectid",uploaded.optString("objectId",""));
            }
            @Override public void rememberUploaded(String id)throws Exception {
                operation.put("uploaded_id",id);
                if(!store.put("greeting-operation.bin",operation))throw new IOException("Couldn't save upload progress. Refresh before retrying.");
            }
            @Override public void activate(String id)throws Exception {
                byte[] body=new JSONObject().put("objectId",id).put("flag","CNS-Greeting-On").toString().getBytes(StandardCharsets.UTF_8);
                greetingRequest("greeting/flag","PUT",body,"application/json");
            }
            @Override public void deactivate(String id)throws Exception {
                greetingRequest("greeting/flag?objectid="+java.net.URLEncoder.encode(id,"UTF-8")+"&flag=CNS-Greeting-On","DELETE",null,"application/json");
            }
            @Override public void confirm(String id)throws Exception {
                operation.put("complete",true);
                if(!store.put("greeting-operation.bin",operation))throw new IOException("Couldn't save confirmation. Refresh to check your greeting.");
            }
        });
    }

    /** Seen is added with PUT; unread removes that flag, never the voicemail itself. */
    public List<JSONObject> setRead(String id, boolean read) throws Exception {
        SavedMailbox saved = SavedMailbox.get(context);
        synchronized (saved) {
            if (saved.removed(id)) {
                saved.setRead(id, read);
                return cachedInbox();
            }
        }
        synchronized (this) {
            applyRead(id, read);
            List<JSONObject> refreshed = sync();
            for (JSONObject message : refreshed) if (id.equals(message.optString("objectid"))) {
                boolean verified = InboxQuery.isRead(message.optString("flag")) == read;
                diagnostics.put("read_update_verified", verified); saveDiagnostics();
                if (!verified) throw new IOException("Read status hasn't been confirmed by the carrier. Refresh to check.");
                return refreshed;
            }
            throw new IOException("This message is no longer in the mailbox. Refresh to check.");
        }
    }

    private void applyRead(String id, boolean read) throws Exception {
        if (id.isEmpty()) throw new IOException("This message has no identifier.");
        if (SavedMailbox.get(context).removed(id)) {
            SavedMailbox.get(context).setRead(id, read); return;
        }
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
        diagnostics.put("read_update_http_status", diagnostics.getOrDefault("mailbox_http_status",0));
    }

    /** Individual flag requests, then one server refresh; only confirmed states count as success. */
    int setReadBatch(List<String> ids, boolean read) throws Exception {
        boolean local = true;
        for (String id : ids) if (!SavedMailbox.get(context).removed(id)) local = false;
        if (local) return ReadBatch.run(ids, read, new ReadBatch.Backend() {
            @Override public void update(String id, boolean target) throws Exception {
                SavedMailbox saved = SavedMailbox.get(context);
                synchronized (saved) {
                    if (!saved.removed(id)) throw new IOException("Refresh to confirm carrier read status.");
                    saved.setRead(id, target);
                }
            }
            @Override public Map<String, Boolean> refresh() {
                Map<String, Boolean> states = new java.util.HashMap<>();
                for (JSONObject item : cachedInbox())
                    states.put(item.optString("objectid"), InboxQuery.isRead(item.optString("flag")));
                return states;
            }
        });
        synchronized (this) { return runReadBatch(ids, read); }
    }

    private int runReadBatch(List<String> ids, boolean read) throws Exception {
        boolean localOnly = true;
        for (String id : ids) if (!SavedMailbox.get(context).removed(id)) localOnly = false;
        final boolean offline = localOnly;
        return ReadBatch.run(ids, read, new ReadBatch.Backend() {
            @Override public void update(String id, boolean target) throws Exception { applyRead(id, target); }
            @Override public java.util.Map<String, Boolean> refresh() throws Exception {
                java.util.Map<String, Boolean> states = new java.util.HashMap<>();
                for (JSONObject message : offline ? cachedInbox() : sync())
                    states.put(message.optString("objectid"), InboxQuery.isRead(message.optString("flag")));
                return states;
            }
        });
    }

    /** User-requested carrier removal always follows a verified durable local save. */
    synchronized ArchiveBatch.Result archive(List<JSONObject> messages) {
        Map<String, JSONObject> selected = new LinkedHashMap<>();
        for (JSONObject message : messages) selected.put(message.optString("objectid"), message);
        SavedMailbox saved = SavedMailbox.get(context);
        return ArchiveBatch.run(selected.keySet(), new ArchiveBatch.Backend() {
            @Override public void saveAndVerify(String id) throws Exception {
                saved.saveAndVerify(selected.get(id), audio(selected.get(id)));
            }
            @Override public void deleteCarrier(String id) throws Exception {
                if (saved.removed(id)) return;
                if (token.isEmpty() || tokenExpires < System.currentTimeMillis() + 60000) authenticate();
                String path = "voicemail/" + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20");
                JSONObject result;
                try { result = mailbox(path, "DELETE", null); }
                catch (AuthExpired expired) { token = ""; authenticate(); result = mailbox(path, "DELETE", null); }
                JSONArray errors = result.optJSONArray("errors");
                if (errors != null && errors.length() > 0) throw new IOException("The carrier couldn't delete this voicemail.");
            }
            @Override public java.util.Set<String> refreshCarrier() throws Exception {
                java.util.Set<String> present = new java.util.HashSet<>();
                for (JSONObject message : sync()) if (!message.optBoolean("_carrier_removed"))
                    present.add(message.optString("objectid"));
                return present;
            }
            @Override public void confirmRemoved(String id) throws Exception { saved.confirmRemoved(id); }
        });
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
            File saved = SavedMailbox.get(context).cachedAudio(message.optString("objectid"));
            if (saved != null) return saved;
            File file = audioFile(message);
            return file.isFile() && file.length() > 0 ? file : null;
        } catch (Exception ignored) { return null; }
    }

    /** Resolve available audio without waiting for network synchronization. */
    public File availableAudio(JSONObject message) throws Exception {
        File local = cachedAudio(message);
        return local != null ? local : audio(message);
    }

    public synchronized File audio(JSONObject message) throws Exception {
        stage("downloading_audio");
        File saved = SavedMailbox.get(context).cachedAudio(message.optString("objectid"));
        if (saved != null) return saved;
        if (message.optBoolean("_carrier_removed")) throw new IOException("This saved recording is missing. Restore an exported backup if available.");
        File file = audioFile(message);
        if (file.isFile() && file.length() > 0) {
            diagnostics.put("audio_from_cache", true); diagnostics.put("audio_bytes", file.length());
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
            } finally { disconnect(connection); }
        }
        throw new IOException("Too many recording redirects.");
    }

    private void configure(HttpsURLConnection connection) {
        if (cancellation != null) {
            cancellation.throwIfCanceled();
            cancellation.setOnCancelListener(connection::disconnect);
        }
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "VisualVoiceMail org.visualvoicemail.app 0.34 Android");
    }

    private void disconnect(HttpsURLConnection connection) {
        if (cancellation != null) cancellation.setOnCancelListener(null);
        connection.disconnect();
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
        synchronized (diagnosticsFileLock) {
            try (FileOutputStream output = context.openFileOutput("diagnostics.json", Context.MODE_PRIVATE)) {
                output.write(new JSONObject(diagnostics).toString(2).getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
    }

    public List<JSONObject> cachedInbox() {
        List<JSONObject> result = new ArrayList<>();
        JSONObject saved = store.get("inbox.bin");
        JSONArray messages = saved == null ? null : saved.optJSONArray("messages");
        if (messages != null) for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message != null) result.add(message);
        }
        try { return SavedMailbox.get(context).merge(result, false); }
        catch (Exception unavailable) { return result; }
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
            boolean available;
            try {
                if (cancellation != null) cancellation.setOnCancelListener(() -> { close(); ready.countDown(); });
                available = ready.await(22, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) { close(); throw interrupted; }
            finally { if (cancellation != null) cancellation.setOnCancelListener(null); }
            if (cancellation != null && cancellation.isCanceled()) { close(); cancellation.throwIfCanceled(); }
            if (!available || network == null) {
                close();
                throw new IOException("Turn on mobile data and choose Mint as your data SIM, then try again.");
            }
        }
        @Override public void close() { try { connectivity.unregisterNetworkCallback(callback); } catch (Exception ignored) { } }
    }
}
