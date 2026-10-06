# Plan 18 — Net worth

## Goal

Calculate net worth as assets minus liabilities, including items that are not bank accounts, and track it monthly and yearly.

Requirement: §20.

## Depends on

Plan 3 (account types; cash as an asset), Plan 17 (to settle how goal savings relate to assets). Lending data already exists.

## Current state

- Home widget `NETWORTH_SUMMARY` renders a balance card from `HomeUiState.totalBalance`, `totalAvailableCredit`, `balanceHistory` (`$APP/presentation/ui/features/home/HomeViewModel.kt`, `HomeScreen.kt` ~438).
- `ProfileViewModel` (~123) computes a separate `netWorth` shown on `ProfileScreen`. Two calculations for the same concept.
- Inputs available: latest account balances (`AccountBalanceDao.getAllLatestBalances`, `getBalanceHistory`), credit cards (`is_credit_card`, `credit_limit`), lend / borrow summary (`GetLendBorrowSummaryUseCase`), multi-currency conversion (`$APP/data/currency/CurrencyConversionService.kt`).
- `TransactionType.INVESTMENT` exists as a flow of money, but there is no record of investment *holdings* or their value.
- No table of other assets or loans, and no stored history of net worth.

## Changes

1. `$APP/domain/usecase/CalculateNetWorthUseCase.kt` — the single calculation, returning a breakdown in the base currency:
   - **Assets:** bank accounts, cash, wallets (positive balances); investments; money lent (outstanding); other assets.
   - **Liabilities:** credit card outstanding; money borrowed (outstanding); loans; any negative account balance (overdraft).
   - **Net worth** = assets − liabilities.
   
   Replace the calculations in `HomeViewModel` and `ProfileViewModel` with it.
2. Credit card outstanding: confirm from `CreditBalanceCalculationTest` and `AccountBalanceRepository` how a card's stored balance relates to amount owed versus available credit, and derive the liability from that — do not assume.
3. Entity `NetWorthItemEntity` (`net_worth_items`) for manual holdings: id, name, kind (`INVESTMENT`, `OTHER_ASSET`, `LOAN`, `OTHER_LIABILITY`), sub-type (for example mutual fund, fixed deposit, gold, property, vehicle, home loan, personal loan), current value, currency, as-of date, notes, state, timestamps. Entity `NetWorthItemValueEntity` for value history (item id, value, date) so updating a value keeps the old one.
4. Entity `NetWorthSnapshotEntity` (`net_worth_snapshots`): year-month, total assets, total liabilities, net worth, a JSON breakdown by group, base currency, captured-at. Unique on year-month.
5. Snapshots:
   - A monthly worker (or the existing daily one) writes the snapshot for a month once it has ended, and refreshes the current month on app open.
   - Back-fill on first run: reconstruct past months for accounts from `getBalanceHistory` and for lending from entry dates; manual items contribute from their value-history dates. Mark back-filled snapshots as estimated.
6. Do not double count:
   - Savings goals (Plan 17) are earmarks inside accounts, so they are shown for information but not added.
   - A lend / borrow entry linked to a transaction already moved the account balance; the receivable or payable is added once.
   - Transfers between own accounts net to zero.
7. UI in `$APP/presentation/ui/features/networth/`: `NetWorthScreen` with the headline figure, change versus last month and versus a year ago, assets and liabilities lists grouped as above with subtotals, a trend chart switchable between monthly (last 12 months) and yearly; add / edit sheet for manual items with "update value". Destination `NetWorth`; opened from the Home card and from More.
8. Home card: keep the look, feed it from the use case, tap opens `NetWorthScreen`.
9. Apply Plan 6 masking (`NET_WORTH` kind) to all figures here.

## Data changes

- Migration (next free number): create `net_worth_items`, `net_worth_item_values`, `net_worth_snapshots`.
- Backup: add the items and value history. Snapshots can be included or rebuilt; include them, since back-filled values are estimates and real ones are worth keeping.

## Tests

- `CalculateNetWorthUseCaseTest`: each asset and liability group; overdraft counted as liability; credit card outstanding; lent and borrowed outstanding; multi-currency conversion; goals not added; linked lend entry not double counted.
- Snapshot tests: one per month, idempotent rewrite of the current month, back-fill from balance history.

## Verification

- With two bank accounts, cash, one credit card with an outstanding amount, money lent and money borrowed: the screen's totals equal a hand calculation.
- Add a manual investment and a loan: assets, liabilities and net worth move accordingly; updating the investment value keeps history.
- Home card and Profile show the same number as the Net Worth screen.
- Monthly trend shows past months; yearly view aggregates them.
- Privacy mode hides every figure.
- Backup round-trip keeps manual items.
- Light and dark theme check.

## Done when

- [ ] One net-worth calculation used everywhere
- [ ] All asset and liability groups in the requirement covered
- [ ] Manual investments, other assets and loans with value history
- [ ] Monthly and yearly tracking from stored snapshots
- [ ] Backup updated; tests pass

## Open decisions

- Whether investments are entered as manual holdings with a value (recommended for this plan) or derived from `INVESTMENT` transactions. Derived totals show money put in, not current value.
- Whether loans need a repayment schedule (EMI) here. Recommended: balance only; an EMI can be a recurring transaction from Plan 11.
