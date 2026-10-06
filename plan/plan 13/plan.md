# Plan 13 — CSV import wizard

## Goal

Import transactions from any bank or app CSV through a guided flow: select file → detect columns → map columns → preview → validate → duplicate detection → import.

Requirement: §12.

TraceLedger is a feature reference only. Its license is "All Rights Reserved" and forbids copying or adapting its code (see `plan/plan 1/baseline-report.md`), so this is written independently from the requirement and this project's own patterns.

## Depends on

Plan 10 (import goes through the transaction engine and its duplicate detection).

## Current state

- `$APP/data/backup/CashewImporter.kt` has a CSV path (`importFromCsv`, ~706) with its own `splitCsvLine`, fixed to the Cashew column layout.
- `$APP/data/export/CsvExporter.kt` writes the app's own CSV format (progress reporting through `ExportResult`).
- PDF statement import is the closest existing flow: analyse → review sheet with per-account and per-transaction decisions → confirm (`DataPrivacyViewModel.analyzePdfStatement` / `confirmPdfImport`, `PdfImportSheet.kt`, `PdfImportDialogs.kt`). Its decision models are a good pattern for the preview step.
- `opencsv` is already a dependency.
- Entry point for import / export today: `$APP/presentation/ui/features/settings/dataprivacy/DataPrivacyScreen.kt`.

## Changes

1. `$APP/data/importer/csv/CsvReader.kt`: reads a `Uri` with `opencsv`. Detects encoding (UTF-8 with / without BOM, fallback Windows-1252), delimiter (comma, semicolon, tab, pipe — pick the one giving the most consistent column count over the first rows), and the header row (bank exports often start with several lines of account details; choose the first row whose cells look like headers and whose following rows parse consistently). Streams rows; cap preview at a few hundred rows and file size at a stated limit.
2. `CsvColumnDetector.kt`: suggests a mapping from header names and sample values to fields. Fields: Date, Description, Amount, Debit, Credit, Debit/Credit indicator, Category, Account, Merchant, Reference number, Balance (optional). Header synonyms table (for example "Narration", "Particulars", "Txn Date", "Withdrawal Amt", "Deposit Amt", "Chq/Ref No").
3. `CsvValueParsers.kt`:
   - Dates: try a list of formats; when day / month order is ambiguous, infer it from the whole column (any value with a first part > 12 settles it) and let the user override.
   - Amounts: Indian digit grouping, currency symbols, trailing `Cr` / `Dr`, parentheses or minus for negatives.
   - Direction: from separate debit / credit columns, a signed amount, or an indicator column.
4. `CsvImportUseCase.kt`:
   - `analyze(uri)` → headers, sample rows, suggested mapping.
   - `preview(mapping, defaults)` → rows as `TransactionDraft` plus per-row status: `OK`, `INVALID(reason)`, `DUPLICATE(existingId)`, `POSSIBLE_DUPLICATE(existingId)`.
   - `import(selectedRows)` → submits each draft to `TransactionEngine` with `source = CSV_IMPORT`, in a single database transaction per batch, reporting progress.
5. Duplicate detection, in order:
   - Reference number equals an existing transaction's reference.
   - Deterministic row hash (date, amount, direction, normalised description, account) — re-importing the same file yields the same hashes, so nothing is imported twice.
   - Engine fuzzy match against transactions from other sources (same amount, direction and account on the same day) → `POSSIBLE_DUPLICATE`, unselected by default.
   - Identical rows inside one file are legitimate (two equal purchases in a day): disambiguate with a per-file occurrence index in the hash.
6. Account handling: if no account column is mapped, the user picks one account for the whole file (or creates one). If mapped, match values to existing accounts and ask for unmatched ones, like the PDF import's account decisions.
7. Category handling: mapped category names that match existing categories are used; unknown ones are either created or left for rules — the engine applies rules when the category is empty.
8. Balances: choose whether imported rows adjust the account balance. Default off for historical statements, since the current balance already reflects them.
9. UI in `$APP/presentation/ui/features/csvimport/`: a wizard with steps File, Columns (dropdown per field with live sample values), Options (account, date format, balance adjustment), Preview (list with status chips, counts of OK / invalid / duplicate, select all / none), Result (imported, skipped, failed, with a link to the imported transactions filtered by source).
10. Saved mappings: store the mapping keyed by a header-signature hash so the next file from the same bank maps automatically.
11. Entry points: Import / Export in More (Plan 7) and `DataPrivacyScreen`.
12. Undo: record an import batch id on the imported transactions (or in an `import_batches` table) so a whole import can be removed.

## Data changes

- Migration (next free number): `import_batches` table (id, file name, source, row counts, timestamp) and nullable `import_batch_id` on `transactions`.
- DataStore or a small table for saved column mappings.
- Backup: include `import_batches` and saved mappings.

## Tests

- `CsvReaderTest`: delimiters, BOM, quoted fields with embedded delimiters and newlines, leading junk rows, empty trailing rows.
- `CsvColumnDetectorTest`: common header variants; separate debit / credit columns.
- `CsvValueParsersTest`: date formats and ambiguity inference; amount formats including lakh grouping, `Cr` / `Dr`, negatives.
- `CsvImportUseCaseTest`: re-import of the same file imports zero; identical in-file rows both import; reference-number duplicates detected; invalid rows reported and not imported.
- Test fixtures use synthetic data only — no real statements.

## Verification

- Import a synthetic bank CSV with separate debit / credit columns: columns auto-mapped, preview correct, import count matches.
- Import the same file again: all rows flagged duplicate, nothing added.
- Import a file overlapping existing SMS transactions: overlaps flagged as possible duplicates.
- A file with broken rows: those rows listed with reasons, the rest import.
- Undo the import: its transactions are removed.
- Export with the app's own CSV export, then import that file: round-trips.
- Light and dark theme check; large file (several thousand rows) stays responsive.

## Done when

- [ ] All seven flow steps implemented
- [ ] All eight supported fields mappable
- [ ] Same file cannot be imported twice
- [ ] Imports go through the engine with source `CSV_IMPORT`
- [ ] Import can be undone
- [ ] Tests pass

## Open decisions

- Whether to support `.xls` / `.xlsx` statements too. Recommended: CSV only in this plan.
- Default for "adjust account balance" (recommended: off).
