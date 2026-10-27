package org.visualvoicemail.app;

import java.util.*;

public final class GreetingSaveTest {
    private static int checks;

    private static void check(boolean ok) {
        checks++;
        if (!ok) throw new AssertionError("check " + checks);
    }

    private static final class Carrier implements GreetingSave.Backend {
        Map<String, GreetingSave.Entry> entries = new LinkedHashMap<>();
        List<String> calls = new ArrayList<>();
        boolean uploadTimeout,
                rejectUpload,
                wrongId,
                activationTimeout,
                stale,
                rememberFail,
                confirmFail,
                duplicate,
                independentFlags,
                deactivateFail,
                deactivateTimeout;

        Carrier() {
            entries.put("old", new GreetingSave.Entry("old", "Existing", true, true));
            entries.put("name", new GreetingSave.Entry("name", "Name", false, true));
        }

        public Map<String, GreetingSave.Entry> refresh() {
            calls.add("refresh");
            return new LinkedHashMap<>(entries);
        }

        public String uploadInactive(String title) throws Exception {
            calls.add("upload");
            if (rejectUpload) throw new Exception();
            entries.put("new", new GreetingSave.Entry("new", title, true, false));
            if (duplicate)
                entries.put("duplicate", new GreetingSave.Entry("duplicate", title, true, false));
            if (uploadTimeout) throw new Exception();
            return wrongId ? "old" : "new";
        }

        public void rememberUploaded(String id) throws Exception {
            calls.add("remember:" + id);
            if (rememberFail) throw new Exception();
        }

        public void activate(String id) throws Exception {
            calls.add("activate:" + id);
            if (!stale)
                for (GreetingSave.Entry item : new ArrayList<>(entries.values()))
                    if (item.personal)
                        entries.put(
                                item.id,
                                new GreetingSave.Entry(
                                        item.id,
                                        item.title,
                                        true,
                                        item.id.equals(id) || (independentFlags && item.active)));
            if (activationTimeout) throw new Exception();
        }

        public void deactivate(String id) throws Exception {
            calls.add("deactivate:" + id);
            if (deactivateFail) throw new Exception();
            GreetingSave.Entry item = entries.get(id);
            entries.put(id, new GreetingSave.Entry(id, item.title, item.personal, false));
            if (deactivateTimeout) throw new Exception();
        }

        public void confirm(String id) throws Exception {
            calls.add("confirm:" + id);
            if (confirmFail) throw new Exception();
        }
    }

    private static boolean failed(Carrier carrier, String pending) {
        try {
            GreetingSave.run("Draft", pending, carrier);
            return false;
        } catch (Exception expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        Carrier c = new Carrier();
        check("new".equals(GreetingSave.run("Draft", "", c)));
        check(c.calls.indexOf("remember:new") < c.calls.indexOf("activate:new"));
        check(c.calls.get(c.calls.size() - 1).equals("confirm:new"));
        check(c.entries.containsKey("old") && c.entries.get("name").active);
        check(!c.entries.get("old").active && c.entries.get("new").active);
        c = new Carrier();
        c.uploadTimeout = true;
        check(!failed(c, ""));
        check(c.calls.contains("activate:new"));
        c = new Carrier();
        c.activationTimeout = true;
        check(!failed(c, ""));
        check(c.calls.contains("confirm:new"));
        c = new Carrier();
        c.rejectUpload = true;
        check(failed(c, ""));
        check(c.entries.get("old").active && !c.calls.contains("activate:new"));
        c = new Carrier();
        c.rememberFail = true;
        check(failed(c, ""));
        check(!c.calls.contains("activate:new") && c.entries.get("old").active);
        c = new Carrier();
        c.stale = true;
        check(failed(c, ""));
        check(!c.calls.contains("confirm:new") && c.entries.get("old").active);
        c.stale = false;
        c.calls.clear();
        check(!failed(c, "new"));
        check(!c.calls.contains("upload"));
        c = new Carrier();
        c.confirmFail = true;
        check(failed(c, ""));
        check(c.entries.get("new").active);
        c = new Carrier();
        c.wrongId = true;
        check(failed(c, ""));
        check(!c.calls.contains("activate:old"));
        c = new Carrier();
        c.uploadTimeout = true;
        c.duplicate = true;
        check(failed(c, ""));
        check(!c.calls.contains("activate:new"));
        c = new Carrier();
        check(failed(c, "old"));
        check(!c.calls.contains("upload") && !c.calls.contains("activate:old"));
        c = new Carrier();
        c.independentFlags = true;
        check(!failed(c, ""));
        check(c.calls.indexOf("deactivate:old") > c.calls.indexOf("activate:new"));
        check(!c.calls.contains("deactivate:name") && c.entries.get("name").active);
        c = new Carrier();
        c.independentFlags = true;
        c.deactivateFail = true;
        check(failed(c, ""));
        check(c.entries.get("new").active && c.entries.get("old").active);
        c = new Carrier();
        c.independentFlags = true;
        c.deactivateTimeout = true;
        check(!failed(c, ""));
        check(c.entries.containsKey("old") && !c.entries.get("old").active);
        System.out.println(checks + " greeting save confirmation checks passed");
    }
}
