package org.visualvoicemail.app;

/** Synthetic matching cases; no device, subscriber data, or network access. */
public final class InboxQueryTest {
    private static int checks;
    private static void expect(boolean condition, String purpose) {
        checks++; if (!condition) throw new AssertionError(purpose);
    }
    private static boolean match(String query) {
        return InboxQuery.matches(query, "tel:+12025550101", "+1 (202) 555-0101",
            "2026-09-12T16:00:00Z", "Sep 12, 2026 · 9:00 AM");
    }
    public static void main(String[] args) {
        expect(match(""), "Empty search retains a message");
        expect(match("   "), "Whitespace search retains a message");
        expect(match("2025550101"), "Search an unformatted number");
        expect(match("(202) 555-0101"), "Search a formatted number");
        expect(match("555-0101"), "Search part of a number");
        expect(match("2026-09"), "Search an ISO date prefix");
        expect(match("sEp 2026"), "Case-insensitive multi-term date search");
        expect(match("2026 555"), "All terms can span date and number");
        expect(!match("555 missing"), "All text terms must match");
        expect(!match("999999"), "Unrelated number does not match");
        expect(!match("October"), "Unrelated month does not match");
        expect(InboxQuery.matches("alex", "+12025550101", "Alex Morgan", "", ""), "Contact first name is searchable");
        expect(InboxQuery.matches("MORGAN alex", "+12025550101", "Alex Morgan", "", ""), "Contact names match across order and case");
        expect(InboxQuery.matches("Alex 2026", "+12025550101", "Alex Morgan", "2026-09-12", ""), "Contact and date terms can be combined");
        expect(InboxQuery.matches("5550101", "+12025550101", "Alex Morgan", "", ""), "Number search remains available with a contact name");
        expect(!InboxQuery.matches("alex", "+12025550101", "+1 (202) 555-0101", "", ""), "No name match when contacts are unavailable");
        expect(InboxQuery.matches("unknown", "", "Unknown caller", "", "Date unavailable"), "Unknown caller is searchable");
        expect(InboxQuery.isRead("Seen"), "Seen means read");
        expect(InboxQuery.isRead("seen"), "Read flags ignore case");
        expect(InboxQuery.isRead("1"), "Numeric read representation");
        expect(!InboxQuery.isRead("Recent"), "Recent means unread");
        expect(!InboxQuery.isRead(""), "Missing read flag is not assumed read");
        expect(InboxQuery.compareDates("2026-10-12T12:00:00Z","2026-09-12T12:00:00Z",false)<0,"Newest messages sort first");
        expect(InboxQuery.compareDates("2026-09-12T12:00:00Z","2026-10-12T12:00:00Z",true)<0,"Oldest messages sort first");
        expect(InboxQuery.compareDates("2026-09-12T12:00:00Z","2026-09-12T08:00:00-04:00",false)==0,"Sorting uses instants across timezones");
        expect(InboxQuery.compareDates("invalid","2026-09-12T12:00:00Z",true)>0,"Unknown dates sort last in oldest mode");
        expect(InboxQuery.compareDates("invalid","2026-09-12T12:00:00Z",false)>0,"Unknown dates sort last in newest mode");
        System.out.println("Passed " + checks + " inbox search/read-flag checks.");
    }
}
