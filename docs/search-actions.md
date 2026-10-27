# Search and message actions

Version 0.4 adds a search field and All, Unread, and Marked filters. Search runs locally over the entire downloaded inbox, including old messages returned by the existing paginated sync. It matches full or partial phone numbers with or without formatting, ISO dates, and the displayed local date/time. Text matching is case-insensitive and accepts multiple terms. Search queries are never sent to the carrier or saved in diagnostics. Searching spoken content is deferred with transcription; contact-name lookup is not implemented.

The three-dot menu on each message offers Mark as read/Mark as unread and Mark for deletion/Restore from deletion marks. Long-pressing a card opens the same menu. Filtered-out playback pauses, and search/filter state survives activity recreation, including an appearance change.

## Carrier read status

The observed carrier interface uses `PUT voicemail/flag` with a JSON `voicemails` array containing `objectId` and `flag: Seen`. Marking unread uses `DELETE voicemail/flag?objectid=…&flag=Seen`, removing only the read flag. It does not delete the recording. These request shapes were independently implemented after inspecting the vendor interface, serializer, flag model, and read/unread call paths.

The client renews expired sessions and handles one HTTP 401 reauthentication attempt. It checks response-level errors, then fetches the mailbox again and verifies the resulting flag before showing a successful carrier sync. Failed or unconfirmed updates display a retry/refresh message instead of claiming success. Read/unread changes require a working carrier connection; there is no offline mutation queue and playing a message does not automatically mark it read.

## Deletion marks

Deletion marks are local, encrypted AES-GCM records using the existing Android Keystore storage. A mark is shown only after it has been saved successfully. The Marked filter shows marked recordings and explains that none has been erased. Restore removes the mark. Marks remain visible in All and survive refresh/relaunch. No permanent voicemail-deletion endpoint or automatic purge is implemented. These marks do not free carrier storage and do not sync to other apps/devices.

## Validation

On the user's S24 Ultra, a nonmatching search returned zero results, a displayed-date search returned the actual recording, and clearing the search restored the inbox. Marking the previously read recording unread returned HTTP 200 and a fresh inbox verified its unread flag. The Unread filter showed it. Marking it read again returned HTTP 200 and verified read status; the Unread filter then had zero results. The original read state was restored.

Marking the recording for deletion produced one marked result. After force-stop/relaunch, the mark remained. Restore removed it and emptied the Marked filter. The recording itself remained present. Inspected search/actions in both light and dark themes, retained a date query through a theme change, and checked the keyboard-open layout with the large heading collapsed so the result card remained visible. Returned the app to All, an empty query, no deletion marks, and the device theme. Private screenshots and mailbox data stay outside Git.

Seventeen synthetic host checks cover formatted/partial numbers, ISO and displayed dates, multi-term/case-insensitive matching, empty/nonmatching searches, unknown callers, and read-flag interpretation. Run `python research/check_inbox_query.py` after `probe/bootstrap.py`; CI runs it for the app alongside signature verification and palette contrast checks.

This remains a development build tested with one real message on Mint. Large-inbox search performance, other locales, live network-failure/expiry injection during a read change, and cross-app read-status reconciliation need broader testing. There is no transcription search, background delivery, or permanent deletion feature in this milestone.
