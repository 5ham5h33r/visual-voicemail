package org.visualvoicemail.app;

import java.util.*;

public final class ArchiveBatchTest {
    private static int checks;

    private static void check(boolean ok) {
        checks++;
        if (!ok) throw new AssertionError("check " + checks);
    }

    private static class Carrier implements ArchiveBatch.Backend {
        List<String> calls = new ArrayList<>();
        Set<String> present = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<String> unsafe = new HashSet<>(),
                uncertain = new HashSet<>(),
                rejected = new HashSet<>();
        boolean offline, persistFailure;

        public void saveAndVerify(String id) throws Exception {
            calls.add("save:" + id);
            if (unsafe.contains(id)) throw new Exception();
        }

        public void deleteCarrier(String id) throws Exception {
            calls.add("delete:" + id);
            if (!rejected.contains(id)) present.remove(id);
            if (uncertain.contains(id) || rejected.contains(id)) throw new Exception();
        }

        public Set<String> refreshCarrier() throws Exception {
            calls.add("refresh");
            if (offline) throw new Exception();
            return present;
        }

        public void confirmRemoved(String id) throws Exception {
            calls.add("confirm:" + id);
            if (persistFailure) throw new Exception();
        }
    }

    public static void main(String[] args) {
        Carrier c = new Carrier();
        ArchiveBatch.Result result = ArchiveBatch.run(Arrays.asList("a", "a", "b"), c);
        check(result.requested == 2 && result.saved == 2 && result.removed == 2);
        check(
                c.calls.equals(
                        Arrays.asList(
                                "save:a",
                                "delete:a",
                                "save:b",
                                "delete:b",
                                "refresh",
                                "confirm:a",
                                "confirm:b")));
        c = new Carrier();
        c.unsafe.add("a");
        c.rejected.add("b");
        c.uncertain.add("c");
        result = ArchiveBatch.run(Arrays.asList("a", "b", "c"), c);
        check(result.requested == 3 && result.saved == 2 && result.removed == 1);
        check(!c.calls.contains("delete:a"));
        check(c.present.contains("a"));
        check(c.present.contains("b") && !c.calls.contains("confirm:b"));
        check(c.calls.contains("confirm:c"));
        c = new Carrier();
        c.unsafe.add("a");
        result = ArchiveBatch.run(Arrays.asList("a"), c);
        check(result.saved == 0 && result.removed == 0);
        check(c.calls.equals(Arrays.asList("save:a")));
        c = new Carrier();
        c.offline = true;
        result = ArchiveBatch.run(Arrays.asList("a", "b"), c);
        check(result.saved == 2 && result.removed == 0);
        check(!c.calls.contains("confirm:a"));
        c.offline = false;
        result = ArchiveBatch.run(Arrays.asList("a", "b"), c);
        check(result.removed == 2);
        c = new Carrier();
        c.persistFailure = true;
        result = ArchiveBatch.run(Arrays.asList("a"), c);
        check(result.saved == 1 && result.removed == 0);
        for (List<String> invalid :
                Arrays.asList(
                        Collections.<String>emptyList(),
                        Arrays.asList("a", ""),
                        Arrays.asList("a", null))) {
            c = new Carrier();
            boolean blocked = false;
            try {
                ArchiveBatch.run(invalid, c);
            } catch (IllegalArgumentException expected) {
                blocked = true;
            }
            check(blocked && c.calls.isEmpty());
        }
        System.out.println(checks + " archive safety checks passed");
    }
}
