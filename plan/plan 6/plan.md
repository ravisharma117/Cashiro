# Plan 6 — Privacy mode

## Added requirement (2026-10-07): reveal with the proximity sensor

Privacy mode gets a second, optional setting: **Reveal with proximity sensor**. With privacy mode and this setting both on, amounts stay hidden until the user covers the phone's proximity sensor with a finger; they show while it is covered and hide again when the finger is removed.

Design:

- Setting `PRIVACY_REVEAL_ON_PROXIMITY` (default off), shown under the privacy toggle only when the phone has a proximity sensor.
- A small class `ProximityReveal` wraps `SensorManager` and `Sensor.TYPE_PROXIMITY` and exposes `isNear: Flow<Boolean>`. "Near" is a reading below the sensor's maximum range (many phones report only two values, near and far).
- It registers only while the app is in the foreground and both settings are on, and unregisters otherwise, to avoid battery use. No permission is needed.
- `PrivacyState` gains `revealed`, so `maskedAmount` shows real amounts when privacy mode is on but `revealed` is true. The reveal never changes the stored setting.
- Hidden on lock: when the app locks (Plan 5) the reveal resets.
- Edge cases to test: phones without the sensor (control is hidden), the sensor being covered by a case or screen protector, a phone call (the system turns the screen off anyway), and leaving the app while covered.
- Limit to state in the setting's description: the sensor sits near the top of the phone, so the user has to find it; and anyone looking over the shoulder sees amounts while it is covered.

## Goal

One toggle that hides sensitive amounts across the app, so `₹85,450` shows as `₹••••••`.

Requirement: §7 (Privacy Mode: account balances, transaction amounts, net worth, loan balances).

## Depends on

Plan 5 (the Security settings screen that hosts the toggle).

## Current state

- No masking exists anywhere.
- Amounts are formatted through `$APP/utils/CurrencyFormatter.kt` (`formatCurrency`, `formatAmount`), called from 34 files. Because it is a plain object, callers cannot observe a setting.
- Amount-bearing components include `BalanceCard.kt`, `AccountCard.kt`, `AccountCarousel.kt`, `TransactionItem.kt`, `TransactionTotalsCard.kt`, `BudgetCard.kt`, `LendBorrowCard.kt`, `LoanBalanceCard.kt`, `BalanceChart.kt` (all in `$APP/presentation/ui/components/`), plus analytics, budgets, profile and lend / borrow screens.
- Icons `Iconax.Eye` and `Iconax.EyeSlash` already exist.

## Changes

1. Preferences in `UserPreferencesRepository`: `PRIVACY_MODE_ENABLED` and a set `PRIVACY_MODE_SCOPE` with members `BALANCES`, `TRANSACTIONS`, `NET_WORTH`, `LOANS` (all on by default when the mode is on).
2. `$APP/presentation/common/PrivacyMode.kt`:
   - `data class PrivacyState(enabled, scope)`
   - `val LocalPrivacyState = staticCompositionLocalOf { PrivacyState.Off }`
   - `enum class AmountKind { BALANCE, TRANSACTION, NET_WORTH, LOAN }`
   - `@Composable fun maskedAmount(amount, currency, kind): String` which returns the formatter's output, or the currency symbol followed by a fixed number of bullets when that kind is hidden. A fixed bullet count avoids leaking magnitude.
3. Provide `LocalPrivacyState` once at the top of the tree in `CashiroApp.kt` from the preference flow.
4. Replace direct `CurrencyFormatter.formatCurrency(...)` calls in composables with `maskedAmount(...)`, choosing the right `AmountKind`. Work through the components listed above first (they cover most screens), then grep for remaining calls in `presentation/`. Non-UI uses (CSV export, notifications text, backup) stay unmasked except notifications, see step 7.
5. Charts: when hidden, keep the shape but remove axis labels, value labels and tooltips in `BalanceChart.kt` and `analytics/AnalyticsCharts.kt`.
6. Controls: a toggle in the Security screen with the four scope checkboxes, and an eye icon on the Home balance card for quick on / off.
7. Notifications: when privacy mode is on, transaction notifications built in `SmsBroadcastReceiver.kt` and `ReminderReceiver.kt` omit the amount.
8. Edit fields are not masked: when the user opens an amount to edit it, they see it.

## Data changes

- No Room change.
- Two DataStore keys; add both to the preferences section of the backup.

## Tests

- `PrivacyMaskTest` on a pure helper under `maskedAmount`: off returns the formatted amount; on returns symbol plus bullets; scope controls which kinds are hidden; bullet count is constant for different magnitudes.

## Verification

- Turn privacy mode on: Home balance, account cards, transaction list, transaction detail, budgets, analytics totals, lend / borrow balances and Profile net worth all show bullets.
- Uncheck "Transactions" in scope: transaction amounts return, balances stay hidden.
- Tap the eye icon on Home: amounts toggle immediately without restarting.
- Charts show no numbers while hidden.
- A new transaction notification shows no amount while hidden.
- CSV export still contains real amounts.
- Light and dark theme check.

## Done when

- [ ] Toggle and scope persisted
- [ ] No composable in `presentation/` formats a displayed amount without going through `maskedAmount`
- [ ] Charts and notifications respect the mode
- [ ] Tests pass

## Open decisions

- Whether privacy mode should turn on automatically each time the app locks. Recommended: no; keep it an explicit choice.

## Outcome (2026-10-07)

Built as a first slice, on the `feature/app-lock` branch together with Plan 5.

- Setting: Settings, Security, "Amounts": "Hide total amounts" and, when the phone has a proximity sensor, "Show while the proximity sensor is covered".
- Masking goes through `CurrencyFormatter.formatTotal`, backed by Compose state in `PrivacyGate`, so screens redraw by themselves. A hidden total shows the currency symbol and a fixed-length mask.
- Masked (narrowed on request): balances (Home balance card and chart axis, account cards and account lists), cash account, net worth, and the income, expense and net totals for this month and this year (Home, Transactions totals card, Profile).
- Everything else shows normally: budgets, loan and person balances, analytics figures, category screens, credit-card limits, subscriptions, individual transactions. Exports and backups keep real amounts.
- The proximity listener runs only while the app is in the foreground and both switches are on.
- Both switches are saved in backups.
- 12 unit tests in `PrivacyGateTest`. Not yet checked on a phone.
- Still open from this plan: a quick eye toggle on Home, masking transaction rows.
