# SIM carrier label in version 0.10

The inbox's top-left carrier name now comes from the SIM selected for default mobile data. It uses Android's SIM service-provider name, rather than the currently visited network's name, and refreshes when returning to the inbox. This matches the default-data subscription used by the existing Mint authentication flow.

The header retains its uppercase styling. A long name stays on one line and truncates before the Settings button; its full name remains available to accessibility services. If the subscription is invalid, the SIM is not ready, the provider name is empty, or the device cannot expose the information, the label falls back to Mint Mobile.

This is a display change. Carrier mailbox connectivity is still implemented for Mint only. It does not select a different mailbox, switch SIMs, change authentication endpoints, or add another carrier backend. No additional permission, subscription enumeration, phone-number lookup, SIM identifier storage, or diagnostic field is added.

## Validation

Built and signature-verified version 0.10 and installed it on the S24 Ultra. A read-only check running under the app's identity confirmed that Android returned the provider name **Mint**, the helper returned the same name, and READ_PHONE_STATE was not granted. The inbox UI then displayed **MINT**, with the accessible description **Carrier: Mint**. The temporary check package was removed.

No SIM/default-data settings or carrier messages were changed during validation. Physical SIM removal, changing the default-data SIM, unsupported devices, and very long provider names still need broader device coverage. The fallback paths remain defensive behavior rather than live SIM-removal test evidence.

Android APIs: [default-data subscription](https://developer.android.com/reference/android/telephony/SubscriptionManager#getDefaultDataSubscriptionId()), [subscription-scoped telephony manager](https://developer.android.com/reference/android/telephony/TelephonyManager#createForSubscriptionId(int)), and [SIM provider name](https://developer.android.com/reference/android/telephony/TelephonyManager#getSimOperatorName()).
