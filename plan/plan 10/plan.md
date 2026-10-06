# Plan 10 — Unified transaction engine

## Goal

One entry point through which every transaction enters the database, whatever its source, so normalisation, merchant mapping, category rules, duplicate detection and balance updates behave the same for SMS, manual entry, imports and (later) UPI and recurring.

Requirement: §13 (critical architecture rule), §14, §23.

## Depends on

Plan 3 (account type; cash account key).

## Current state

Four separate write paths, each building a `TransactionEntity` and its hash differently:

| Source | Code | Hash | Processing |
| --- | --- | --- | --- |
| SMS / notification | `$APP/data/manager/SmsTransactionProcessor.kt` `saveParsedTransaction` | from parser (`ParsedTransactionMapper.kt`) | Hash dedup, deleted-hash check, UPI-reference dedup, merchant mapping, rules, subscription match, balance |
| Manual | `$APP/domain/usecase/AddTransactionUseCase.kt` | `generateManualTransactionHash(amount, merchant, date)` | Insert, balance, optional subscription. No rules, no dedup |
| PDF statement | `DataPrivacyViewModel.confirmPdfImport` (~277–400) | own `generateHash`, `extractUtr` | Logic lives in a ViewModel |
| Cashew import | `$APP/data/backup/CashewImporter.kt` | source primary key | Direct inserts |

- `$APP/data/manager/TransactionDeduplication.kt`: hash check, 12-digit UPI reference match within 3 minutes, replace rules.
- `TransactionEntity` has `reference`, `sms_body`, `sms_sender` but no explicit source; "manual" is inferred from `sms_body == null`.
- Pending-review behaviour exists only for notifications (`NotificationActionReceiver` confirm / delete actions).
- `LendBorrowRepository` also inserts transactions (random UUID hash).

## Changes

1. `$APP/domain/model/TransactionSource.kt`: `enum class TransactionSource { SMS, NOTIFICATION, UPI, MANUAL, CSV_IMPORT, PDF_IMPORT, BACKUP_IMPORT, RECURRING }`.
2. `$APP/domain/model/TransactionDraft.kt`: the normalised model all sources produce — amount, type, date-time, merchant, category / subcategory (optional), account key, currency, reference, description, raw text, source, optional precomputed hash.
3. `$APP/domain/service/TransactionEngine.kt` (`@Singleton`), `suspend fun submit(draft, options): EngineResult`, running in order:
   1. **Normalise:** trim and case-fold merchant, scale amount, clean reference.
   2. **Merchant detection:** `MerchantMappingRepository`.
   3. **Category detection:** `RuleEngine` / `RuleRepository`, only when the draft has no explicit category.
   4. **Duplicate detection:** see step 4.
   5. **Review gate:** `options.requireReview` returns the draft for confirmation instead of saving.
   6. **Save:** insert, record rule applications.
   7. **After save:** balance update (`AccountBalanceRepository.insertTransactionBalance`, `BalanceUpdateProcessor`), subscription match, webhook trigger.
   
   `EngineResult`: `Saved(id)`, `Duplicate(existingId, reason)`, `Replaced(id)`, `PreviouslyDeleted`, `NeedsReview(draft)`, `Rejected(reason)`.
4. Extend `TransactionDeduplication` into a layered check, strongest first:
   1. Same hash (and previously-deleted hash).
   2. Same bank reference / UPI transaction ID and amount.
   3. Fuzzy: same amount, type and account within a time window, with similar merchant. Fuzzy matches across *different* sources return `Duplicate` for automatic sources and a warning for manual entry and imports.
5. Add `source` to `TransactionEntity`. One shared hash function per source kind in the engine, replacing the three private generators.
6. Move callers onto the engine, one at a time, keeping their public signatures:
   - `SmsTransactionProcessor.saveParsedTransaction` → builds a draft, calls `submit`. Parsing stays where it is.
   - `AddTransactionUseCase.execute` → draft with `MANUAL`; transfer handling stays in the use case or moves into the engine as a transfer option.
   - PDF import: move the save loop out of `DataPrivacyViewModel` into a `PdfImportUseCase` that calls the engine.
   - `CashewImporter`: leave on bulk insert (it restores a foreign backup with its own keys) but set `source = BACKUP_IMPORT`.
   - `LendBorrowRepository`: set `source`; route through the engine if it does not disturb its transaction boundaries.
7. Show the source on `TransactionDetailScreen` and add a source filter to `TransactionsFilterBottomSheet.kt`.

## Data changes

- Migration (next free number): `ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'SMS'`, then `UPDATE transactions SET source='MANUAL' WHERE sms_body IS NULL AND sms_sender IS NULL`. Add an index on `reference`.
- Backup: field default for older backups; `SanitizationUtils.kt` mapping.

## Tests

- `TransactionEngineTest` with fake repositories: each pipeline step runs in order; explicit category is not overridden by rules; each `EngineResult` variant.
- `TransactionDeduplicationTest`: hash, reference, fuzzy window boundaries, cross-source cases (SMS then UPI for the same payment; CSV row matching an SMS transaction).
- Keep `AddTransactionUseCaseTest` and `CreditBalanceCalculationTest` green unchanged — they pin current behaviour.
- Parser tests in `parser-core` are untouched.

## Verification

- Rescan SMS on a populated database: no new duplicates, transaction count unchanged.
- Manual add now applies category rules when no category is chosen, and balances update as before.
- PDF statement import over a period already covered by SMS reports duplicates instead of doubling.
- Migration from v62 data marks manual entries `MANUAL`.
- Source visible on transaction detail; source filter works.

## Done when

- [ ] `TransactionEngine` is the only place that inserts new SMS, manual and PDF transactions
- [ ] Three private hash generators replaced by one
- [ ] `source` column migrated and populated
- [ ] Existing behaviour tests unchanged and passing; new engine and dedup tests pass

## Open decisions

- Fuzzy-duplicate window (recommended: 3 minutes for automatic sources, same calendar day for imports).
- Whether manual entry should block on a likely duplicate or only warn (recommended: warn and allow).
