# Plan 14 — UPI accessibility capture

## Goal

Detect completed UPI payments in Google Pay, PhonePe, Paytm and Amazon Pay with an opt-in Accessibility Service, and feed them into the same transaction engine as every other source.

Requirement: §13. Reference for ideas: UPI Expense Tracker.

## Depends on

Plan 10 (engine, `TransactionSource.UPI`, cross-source duplicate detection).

## Current state

- No accessibility service.
- `$APP/receiver/BankNotificationListenerService.kt` + `BankNotificationConfig.kt`: a `NotificationListenerService` allow-listing 3 bank apps, turning notification text into a pseudo-SMS for the parsers. `BankNotificationRetryWorker` and `BankNotificationEntity` back it.
- `TransactionDeduplication` already treats a 12-digit reference as a UPI reference and dedups within 3 minutes — bank SMS for a UPI payment carries the same reference.
- `parser-core` holds bank SMS parsers (pure Kotlin, no Android dependencies) and has shared test helpers (`ParserTestUtils`, `docs/parser-test-standards.md`).
- Flavors `fdroid` and `standard` each have their own source set.

## Policy constraint

Google Play restricts Accessibility Service use to apps whose purpose is assisting users with disabilities, unless a declared, approved use applies; reading other apps' screens for expense tracking is likely to be rejected. Plan for:

- A prominent in-app disclosure and explicit consent before sending the user to system settings.
- The feature compiled out of the Play (`standard`) build if review requires it, and present in `fdroid` / direct builds.
- A notification-based fallback for builds without the service.

## Changes

1. `parser-core`: package `com.ritesh.parser.core.upi` with `UpiScreenParser` interface — input is a flattened list of visible text nodes plus the package name; output is `ParsedUpiPayment?` (amount, direction, counterparty name, UPI ID if shown, UPI transaction ID / reference, status, timestamp). One implementation per app:
   - Google Pay — `com.google.android.apps.nbu.paisa.user`
   - PhonePe — `com.phonepe.app`
   - Paytm — `net.one97.paytm`
   - Amazon Pay — inside `in.amazon.mShop.android.shopping`
   
   Parsers match on the *success / receipt* screen only (presence of a success marker plus an amount plus a transaction ID). Keeping them in `parser-core` makes them unit-testable without a device.
2. `$APP/service/UpiAccessibilityService.kt`:
   - `res/xml/upi_accessibility_config.xml` restricts `packageNames` to the four packages, event types to window state / content changes, with `canRetrieveWindowContent`.
   - On an event, walk the node tree to collect text, debounce per window, hand to the matching parser.
   - Do nothing on screens that are not a receipt. Never read input fields, never log captured text, hold nothing in memory after parsing. Recycle nodes.
3. Hand-off: a parsed payment becomes a `TransactionDraft` with `source = UPI`, `reference` = UPI transaction ID, and is submitted to `TransactionEngine`.
4. Duplicate handling, which is the main risk because the bank SMS for the same payment usually arrives seconds later (or earlier):
   - Same reference → the engine merges. Keep one transaction; prefer the SMS for account and balance, and take the counterparty name from UPI when the SMS merchant is only a UPI ID.
   - No shared reference → fuzzy match on amount, direction and a short window.
   - Repeated events for the same receipt screen → de-duplicated in the service by transaction ID before reaching the engine.
5. Account attribution: the receipt often shows the paying bank and last digits; map to an existing account, else leave unassigned and let the later SMS fill it.
6. Review: UPI-captured transactions that did not merge with an SMS are saved with a "review" marker and listed for confirm / edit / delete, reusing the notification confirm / delete actions in `NotificationActionReceiver`.
7. Settings: `$APP/presentation/ui/features/settings/upi/UpiCaptureScreen.kt` — disclosure text, per-app toggles, service status (enabled / disabled, deep link to Accessibility settings), a count of captured transactions, and "turn off". Preference keys for per-app toggles.
8. Flavor handling: put the service, its manifest entry and config in a source set that can be excluded (for example `fdroid` and a new `direct` build, or a Gradle flag), with a no-op stub elsewhere so shared code compiles.
9. Fallback where the service is unavailable: add the four UPI apps to the notification listener path (`BankNotificationConfig`) with notification-text parsers. Coverage is lower (not every app posts a payment notification) and is stated as such in the UI.
10. Resilience: app UI text changes with updates. Parsers fail closed (return null), a per-app parser version is recorded, and a "capture not working?" row links to a debug view showing whether events are arriving (no captured content).

## Data changes

- None required beyond Plan 10's `source` column and `reference` index.
- Optional: `needs_review` boolean on `transactions` (next free migration number) if no existing marker suits.
- DataStore: per-app capture toggles, disclosure-accepted flag.

## Tests

- In `parser-core`, following `docs/parser-test-standards.md`: for each app, synthetic node-text fixtures for a paid receipt, a received receipt, a failed payment (must return null), a pending payment (null), and unrelated screens (null). No real names, UPI IDs or reference numbers in fixtures.
- Engine tests: UPI then SMS with the same reference → one transaction; SMS then UPI → one transaction; same receipt event three times → one transaction.

## Verification

- With the service enabled, make a small payment in each of the four apps: one transaction appears with correct amount, direction, counterparty and reference.
- When the bank SMS arrives, the count stays at one and the transaction has the account from the SMS.
- A failed or cancelled payment creates nothing.
- Browsing the apps without paying creates nothing.
- Disabling a per-app toggle stops capture for that app; disabling the service stops everything.
- A build with the service excluded compiles and shows the fallback.
- `adb logcat` shows no captured screen text.

## Done when

- [ ] Four app parsers with tests in `parser-core`
- [ ] Opt-in service with disclosure, per-app toggles and off switch
- [ ] UPI transactions enter through `TransactionEngine`; no separate UPI storage
- [ ] One transaction per payment when SMS also arrives
- [ ] Build variant without the service works

## Open decisions

- **Distribution:** will the app be published on Google Play? If yes, decide now whether the Play build excludes the service (recommended) or a permission declaration is attempted.
- Whether unmerged UPI captures are saved immediately (recommended) or held until the user confirms.
- Device and app versions available for testing all four apps.
