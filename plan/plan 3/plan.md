# Plan 3 — Account types & Cash account

## Goal

Make Cash a proper account that behaves like any other, and give every account an explicit type (bank, cash, credit card, wallet) that later plans can rely on.

Requirement: §6.

## Depends on

Plan 1.

## Current state

- There is no accounts table. `$APP/data/database/entity/AccountBalanceEntity.kt` (`account_balances`) stores balance-history rows; an account is the pair `(bank_name, account_last4)` and its current state is the latest row.
- Type is implied by two booleans: `is_credit_card` and `is_wallet`.
- Cash is the account `bankName = "Cash"`, `accountLast4 = "wallet"`, and those literals are repeated in:
  - `$APP/presentation/ui/features/accounts/ManageAccountsViewModel.kt` (lines ~119, 124, 195)
  - `$APP/presentation/ui/features/accounts/EditAccountSheet.kt` (~281)
  - `$APP/presentation/ui/features/onboarding/OnBoardingViewModel.kt` (~235, 306)
  - `$APP/presentation/ui/features/settings/SettingsViewModel.kt` (~743)
- Totals come from `AccountBalanceDao.getTotalBalance()` and `getAllLatestBalances()`; `HomeViewModel` splits `accountBalances` and `creditCards`.
- Manual transactions pick an account in `$APP/presentation/ui/components/AccountSelectionSheet.kt` and are saved by `$APP/domain/usecase/AddTransactionUseCase.kt`, which updates balances only when both `bankName` and `accountLast4` are set.

## Changes

1. Add `$APP/domain/model/AccountType.kt`: `enum class AccountType { BANK, CASH, CREDIT_CARD, WALLET }`, plus a `CashAccount` object holding the single definition of the cash key (`BANK_NAME`, `LAST4`) and an `isCash(bankName, last4)` helper.
2. Add column `account_type` (TEXT, default `'BANK'`) to `AccountBalanceEntity`, with a type converter in `$APP/data/database/converter/Converters.kt`. Keep `is_credit_card` and `is_wallet` for now and keep them in sync on write, so existing queries keep working.
3. Add an extension `AccountBalanceEntity.type` in `$APP/utils/EntityExtensions.kt` and use it in UI code instead of reading the booleans.
4. Replace every `"Cash"` / `"wallet"` literal listed above with `CashAccount`.
5. Add Account flow (`AddAccountScreen.kt`, `EditAccountSheet.kt`): a type selector with the four types. Cash hides the last-4 field and uses the fixed key; only one cash account per currency.
6. Create the cash account on demand: `AccountBalanceRepository.ensureCashAccount(currency)` returning the existing or a new zero-balance row. Call it from the Add Account flow and from onboarding.
7. Account selector and Add Transaction: cash is always offered. Verify an expense paid from Cash reduces the cash balance and an income into Cash increases it, through the existing `insertTransactionBalance` path.
8. Totals: confirm cash is included in `getTotalBalance()`, the Home net-worth card and Profile net worth; add it to the account carousel with a distinct cash icon (`Iconax.WalletMoney`).
9. Account detail for cash: hide bank-only actions (card linking, SMS source), keep balance history and edit balance.

## Data changes

- Migration 62 → 63 (next free number):
  - `ALTER TABLE account_balances ADD COLUMN account_type TEXT NOT NULL DEFAULT 'BANK'`
  - `UPDATE … SET account_type='CREDIT_CARD' WHERE is_credit_card=1`
  - `UPDATE … SET account_type='CASH' WHERE bank_name='Cash' AND account_last4='wallet'`
  - `UPDATE … SET account_type='WALLET' WHERE is_wallet=1 AND account_type='BANK'`
- Backup: `AccountBalanceEntity` is already in `BackupModels`; the new field needs a default so older backups import. Check `SanitizationUtils.kt` maps it.

## Tests

- `AccountTypeMigrationTest`: rows with each flag combination map to the right type.
- Extend `AccountBalanceRepositoryTest`: `ensureCashAccount` is idempotent; cash expense and income adjust the balance.
- Extend `AddTransactionUseCaseTest`: transaction against the cash account writes a balance row.

## Verification

- Upgrade install from a v62 database keeps all accounts with correct types.
- Add a cash account with ₹8,500; add a ₹500 Food expense paid from Cash; balance shows ₹8,000 and the transaction shows Cash as its account.
- Total balance on Home includes cash.
- Transfer bank → cash moves the amount between both.
- Light and dark theme check of Add Account and the account carousel.

## Done when

- [ ] `AccountType` and `CashAccount` exist; no `"Cash"` / `"wallet"` literals remain outside them
- [ ] Migration written, registered, schema 63 exported
- [ ] Cash can be created, selected, edited and appears in totals
- [ ] Tests pass

## Open decisions

- One cash account per currency (recommended) or several named cash accounts (for example "Home cash", "Travel cash"). Several would need a generated key in place of the fixed `"wallet"`.
