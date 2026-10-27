package org.visualvoicemail.app;

import java.util.*;

public final class ReadBatchTest {
    static int checks;

    static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }

    static final class Carrier implements ReadBatch.Backend {
        final Map<String, Boolean> states = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        String ambiguous = "", rejected = "";
        int refreshes;
        boolean offline;

        public void update(String id, boolean read) throws Exception {
            writes.add(id);
            if (id.equals(rejected)) throw new Exception("rejected");
            states.put(id, read);
            if (id.equals(ambiguous)) throw new Exception("response lost");
        }

        public Map<String, Boolean> refresh() throws Exception {
            refreshes++;
            if (offline) throw new Exception("cannot confirm");
            return new HashMap<>(states);
        }
    }

    public static void main(String[] args) throws Exception {
        Carrier c = new Carrier();
        c.ambiguous = "a";
        c.rejected = "b";
        c.states.put("b", false);
        check(ReadBatch.run(Arrays.asList("a", "b", "a", "c"), true, c) == 2);
        check(c.writes.equals(Arrays.asList("a", "b", "c")));
        check(c.refreshes == 1);
        check(!c.states.get("b"));
        c.writes.clear();
        c.ambiguous = "c";
        c.rejected = "a";
        check(ReadBatch.run(Arrays.asList("a", "b", "c"), false, c) == 2);
        check(c.writes.size() == 3 && c.refreshes == 2);
        // A rejected request can still already have the requested final state.
        check(ReadBatch.run(Arrays.asList("a"), true, c) == 1);
        c.offline = true;
        try {
            ReadBatch.run(Arrays.asList("a"), false, c);
            throw new AssertionError("unconfirmed success");
        } catch (Exception expected) {
            check(true);
        }
        int writes = c.writes.size();
        for (List<String> invalid :
                Arrays.asList(
                        Collections.<String>emptyList(),
                        Arrays.asList("a", ""),
                        Arrays.asList("a", null))) {
            try {
                ReadBatch.run(invalid, true, c);
                throw new AssertionError("invalid IDs accepted");
            } catch (IllegalArgumentException expected) {
                check(c.writes.size() == writes);
            }
        }
        // A disappearing message must never be counted as confirmed.
        ReadBatch.Backend missing =
                new ReadBatch.Backend() {
                    public void update(String id, boolean read) {}

                    public Map<String, Boolean> refresh() {
                        return Collections.emptyMap();
                    }
                };
        check(ReadBatch.run(Arrays.asList("gone"), false, missing) == 0);
        System.out.println("Passed " + checks + " bulk read confirmation checks.");
    }
}
