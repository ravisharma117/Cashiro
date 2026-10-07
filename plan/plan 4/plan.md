# Plan 4 — Account reordering

## Outcome (2026-10-07)

Implemented as planned, with the preference-list storage (no migration). Differences from the steps below:

- The ordering logic is `AccountOrdering` (pure, tested) and `AccountOrderUseCase` (ordered flow, save, rename and remove), instead of a new method on `AccountBalanceRepository`. This avoids changing the repository constructor that its existing tests build.
- Accounts not in the stored order keep the order they arrive in (balance, highest first) and go after the listed ones. With no stored order nothing changes, so users who never reorder see no difference. This replaces the "bank, cash, card, wallet" default in step 2.
- Reordering happens in a bottom sheet opened from a button in the Manage Accounts top bar, not inline in the list. Order is saved when a drag ends.
- Switched to the ordered source: Home (carousel and refresh paths), Manage Accounts, Add transaction, transaction detail, budgets, lend / borrow and person detail. Screens that only compute totals or build maps (analytics, profile, webhooks, AI context, onboarding) were left on the plain query.
- Rename keeps the account's position; delete and merge remove it from the order.
- Backup: `account_order` is exported and restored; older backups without it import normally.
- Not done: the transaction filter's account chips were not checked for ordering.
- Found, not fixed: backups export the Home widget layout but the importer never restores it.

Tests: `AccountOrderingTest` (11).

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
