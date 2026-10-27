# Protected sessions and offline playback

The app now keeps its mailbox session and inbox in authenticated AES-GCM records. The encryption key is generated and retained by Android Keystore, and each write receives a fresh IV. Atomic writes protect the cache against interrupted updates. A missing, corrupt, or undecryptable record is treated as unavailable rather than parsed as valid data. Tokens and subscriber numbers are excluded from diagnostics and backups are disabled.

The carrier's token expiration is checked before refresh, and an HTTP 401 triggers one reauthentication attempt. Full mailbox pages are fetched with offset/count pagination and deduplicated by object ID. A size/progress bound prevents an endless pagination loop.

Recordings remain in the app's private files directory. Saved audio is checked before any audio network request. This lets the user reopen the saved inbox and play downloaded voicemail without retrieving it again.

## Live validation

On the user's S24 Ultra, the app reported successful encrypted session and inbox writes after a real mailbox sync. After a force-stop and relaunch, the saved inbox appeared immediately. Playback reported `session_restored: true`, `audio_from_cache: true`, 29,606 bytes and an 18,500 ms duration, without performing a new identification or token exchange. A subsequent mailbox refresh used the restored session successfully.

This verifies the saved-data path on this phone. Token-expiry renewal, encrypted-record tampering, large-mailbox pagination, and behavior after device key invalidation have not been exercised with live edge-case inputs. The app remains a development build, with no background-notification or transcription claim.
