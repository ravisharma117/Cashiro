# Plan 20 — Reports, export & release hardening

## Goal

Monthly and yearly reports exportable as PDF and CSV, plain-language spending insights, and a closing pass that makes sure every feature added in Plans 2–19 is covered by backup, security and regression checks.

Requirement: §21 (reports), §22 (smart insights), §24 Phase 6 (encrypted backup) and Phase 7 (polish).

## Depends on

Plan 19 (period summaries the reports are built from). All earlier plans for the audit.

## Current state

- `$APP/data/export/CsvExporter.kt`: transaction CSV export with progress (`ExportResult`), old-file cleanup, shared through the FileProvider. UI: `transactions/ExportTransactionsDialog.kt`, `ExportViewModel.kt`.
- `pdfbox-android` is a dependency, used for *reading* statements (`data/parser/pdf`, `PdfStatementParserTest`). No PDF is generated anywhere.
- Backup: `$APP/data/backup/BackupExporter.kt`, `BackupImporter.kt`, `BackupModels.kt`; cloud backup with `$APP/data/cloud/security/BackupEncryptionEngine.kt`, providers for Google Drive and WebDAV, `CloudBackupWorker`.
- On-device LLM chat exists (`features/chat`, `LlmRepository`, `AiContextRepository`); `$APP/utils/PiiRedactor.kt` redacts sensitive text.
- No report screen, no insights.

## Changes

### Reports

1. `$APP/domain/model/Report.kt`: `MonthlyReport` and `YearlyReport` data models assembled by `BuildReportUseCase` from Plan 19's `GetPeriodSummaryUseCase` and the feature repositories.
   - Monthly: income, expenses, savings, category breakdown, account breakdown, budget performance, loans (lent, borrowed, repaid), bills, subscriptions.
   - Yearly: total income, expenses, savings, savings rate, category trends, monthly trends, lending, borrowing, net worth.
2. `$APP/presentation/ui/features/reports/ReportsScreen.kt`: choose Monthly or Yearly and the period, preview the report on screen, export. Destination `Reports`; row in More.
3. PDF export — `$APP/data/export/PdfReportExporter.kt` using Android's built-in `android.graphics.pdf.PdfDocument` (no new dependency; drawing tables and text on a canvas). Pages: title and period, summary figures, tables with page breaks and repeated headers, simple bar charts drawn on the canvas. Always rendered in a light, print-friendly style regardless of app theme. Use an embedded font that has the ₹ glyph.
4. CSV export — `CsvReportExporter.kt` alongside the existing exporter: one file per report with labelled sections, or a zip of one CSV per table (summary, categories, accounts, months).
5. Sharing and saving: reuse the existing FileProvider share flow and the Storage Access Framework "save to" flow used by backup. Generated files go to the same cleaned-up export directory.
6. Exports contain real amounts even when privacy mode is on; show a one-line notice before exporting in that case.

### Smart insights (rule-based)

7. `$APP/domain/usecase/GenerateInsightsUseCase.kt` — deterministic, no model required:
   - Month-over-month change: "You spent ₹X more this month than last month."
   - Breakdown: the categories contributing most to the change, with their deltas.
   - Major transactions: the largest transactions inside those categories.
   - Also: categories over budget, unusually large single transaction, new recurring merchant.
   
   Compare like with like — month to date against the same number of days last month — so early-month figures do not mislead.
8. Show insights as a card on the monthly tracker and optionally on Home (a new `INSIGHTS` widget, hidden by default), and include the top insight lines in the monthly report.
9. The existing chat may be given the computed insight data as context later; anything model-generated is backlog.

### Backup and security audit

10. For every table and preference introduced in Plans 3–19, confirm export and import, and that a backup made before each plan still imports (defaults for missing fields). Checklist of additions: `account_type`, account order, lock method and privacy preferences (never the PIN hash), `source`, recurring tables, bill columns and `bill_payments`, `import_batches` and saved CSV mappings, UPI toggles, lend / borrow reminder columns and aliases, `repayment_suggestions`, goals and contributions, net-worth items / values / snapshots, year basis.
11. Bump the backup format version and write a round-trip test that exports a fully populated database and imports it into an empty one.
12. Encrypted local backup: offer password-based encryption for the local backup file by reusing `BackupEncryptionEngine` (today used for cloud). Import detects an encrypted file and asks for the password. Wrong password fails cleanly without touching existing data.
13. Review logs added in Plans 3–19 for amounts, names, references and captured UPI text; route anything necessary through `PiiRedactor` or remove it.

### Release

14. Migration chain test: open a v62 database fixture and migrate to the final version with data intact; also test each intermediate schema.
15. Walk the Plan 1 feature checklist again plus one check per new feature; fix regressions.
16. Update `README.md`, `docs/architecture.md` (the transaction engine and sources), `docs/database-migrations.md`, `CLAUDE.md`, `PRIVACY.md` (accessibility capture, what stays on device) and store metadata.
17. Version: a minor or major bump under the SemVer rules in `CLAUDE.md`; build both flavors in release; tag and merge `develop` → `main`.

## Data changes

- No new tables.
- Backup format version bump.

## Tests

- `BuildReportUseCaseTest`: monthly and yearly content against fixed data; totals equal Plan 19's tracker figures.
- `GenerateInsightsUseCaseTest`: increase, decrease and no-change wording inputs; top contributing categories; same-days comparison; no insight when last month is empty.
- PDF exporter: produces a non-empty multi-page document for a large report; smoke test that it opens.
- CSV report: parses back with `opencsv`; amounts unformatted and locale-independent.
- Backup round-trip and encrypted backup tests; migration chain test.

## Verification

- Export a monthly report as PDF: opens in a PDF viewer, figures match the monthly tracker, ₹ renders, long category tables break across pages correctly.
- Export a yearly report as CSV: opens in a spreadsheet, month totals match the yearly tracker.
- Insights for a month with higher spending name the right categories and the largest transactions.
- Full backup on one install restores on a clean install with every feature's data present; encrypted backup needs the password.
- Upgrade from the Plan 1 baseline APK's data (via backup import, since the application ID changed in Plan 2) keeps all data.
- `./gradlew test lint` clean relative to the Plan 1 baseline; both release flavors build.

## Done when

- [ ] Monthly and yearly reports with all listed contents
- [ ] PDF and CSV export
- [ ] Rule-based insights explaining month-over-month change
- [ ] Every new table and preference in backup; round-trip test passes
- [ ] Optional password encryption for local backups
- [ ] Migration chain test passes
- [ ] Docs updated; release built and tagged

## Open decisions

- PDF branding (logo, colours) — depends on the Plan 2 identity.
- Whether scheduled reports (for example a monthly report notification on the 1st) are wanted. Recommended: backlog.
