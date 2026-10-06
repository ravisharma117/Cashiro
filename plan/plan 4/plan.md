# Plan 4 — Account reordering

## Goal

Let the user drag accounts into a preferred order and apply that order everywhere accounts are listed.

Requirement: §8.

## Depends on

Plan 3 (account type and the cash key).

## Current state

- Accounts have no order. Lists come from `AccountBalanceDao.getAllLatestBalances()` and are sorted ad hoc in each ViewModel.
- An account's identity is `(bank_name, account_last4)`; there is no single row per account to hold a sort column.
- A working persisted-order pattern exists for home widgets: `HOME_WIDGETS_ORDER` in `$APP/data/preferences/UserPreferencesRepository.kt`, drag UI in `$APP/presentation/ui/features/home/EditWidgetsSheet.kt` using `sh.calvin.reorderable`. Backup carries it in `BackupModels.kt` (`order` / `hidden`).
- Places that list accounts:
  - Home: `$APP/presentation/ui/components/AccountCarousel.kt` via `HomeViewModel`
  - Selector: `$APP/presentation/ui/components/AccountSelectionSheet.kt`, `AccountFilterChip.kt`
  - Transaction entry: `$APP/presentation/ui/features/add/AddViewModel.kt`
  - Account list: `$APP/presentation/ui/features/accounts/ManageAccountsScreen.kt` / `ManageAccountsViewModel.kt`
  - Budgets account picker: `$APP/presentation/ui/features/budgets/BudgetSelectionSheets.kt`

## Changes

1. Preference `ACCOUNT_ORDER` (string) in `UserPreferencesRepository`: an ordered list of account keys `"bankName|last4"`, with `accountOrder: Flow<List<String>>` and `setAccountOrder(list)`.
2. `$APP/domain/model/AccountOrdering.kt`: one pure function `sortAccounts(accounts, order)`. Accounts in the list come first in list order; accounts not in it (newly detected) follow in a stable default order: bank, cash, credit card, wallet, then name.
3. Expose ordered data once: add `getOrderedLatestBalances(): Flow<List<AccountBalanceEntity>>` to `AccountBalanceRepository`, combining the DAO flow with the preference. Switch all five call sites above to it and delete their local sorting.
4. Manage Accounts: a reorder mode with drag handles using `rememberReorderableLazyListState`, following `EditWidgetsSheet`. Save on drop.
5. Keep the order valid when accounts change: update the stored key on rename (`updateAccountBankName`) and merge (`MergeAccountsDialogs.kt`), remove it on `deleteAccount`.
6. Decide whether bank accounts and credit cards are one list or two sections (see Open decisions) and make Home follow the same grouping.

## Data changes

- No schema change. One new DataStore key.
- Backup: add `accountOrder: List<String> = emptyList()` to the preferences section of `BackupModels.kt`, export and import it.

## Tests

- `AccountOrderingTest`: listed accounts keep list order; unknown accounts are appended in default order; stale keys are ignored; empty order gives the default order.
- Repository test: rename, merge and delete keep the stored order consistent.

## Verification

- Set the order HDFC, SBI, Cash, Credit Card, Wallet (bank names as examples) in Manage Accounts. The same order appears in the Home carousel, the account selector, the Add Transaction account picker and the budgets picker.
- Kill and reopen the app: order persists.
- A newly detected SMS account appears at the end, not in the middle.
- Export and import a backup: order restored.
- Light and dark theme check of reorder mode.

## Done when

- [ ] One ordering function used by every account list
- [ ] Drag reorder works and persists
- [ ] Order survives rename, merge, delete and backup restore
- [ ] Tests pass

## Open decisions

- **Storage:** a preference list of keys (recommended; small, no migration, matches the widget pattern) versus introducing a real `accounts` table with a `display_order` column. The table is the cleaner long-term model but means migrating every account reference in the app.
- **Grouping:** one mixed list as in the requirement's example (recommended), or separate reorderable sections for accounts and credit cards.
