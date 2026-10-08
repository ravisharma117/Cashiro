# Cashiro — 20-step implementation plan

Source requirement: [Cashiro - Expense Tracker](Cashiro%20-%20Expense%20Tracker%203ce2e44b36d1802dac0aee93b4f4f8bb.md)

Each folder holds one self-contained `plan.md`. Work them in order; a plan may start once the plans it depends on are merged into `develop`.

In every plan, `$APP` means `app/src/main/java/com/ritesh/cashiro`.

## Status

| # | Plan | Requirement § | Depends on | Branch | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | [Baseline & branching](plan%201/plan.md) | 3, 24 (Phase 0), 25 | — | `develop` | ◐ device checklist open |
| 2 | [Rebrand](plan%202/plan.md) | 3, 24 (Phase 0) | 1 | `feature/rebrand` | ☑ new default icon; Issues to enable on the fork |
| 3 | [Account types & Cash account](plan%203/plan.md) | 6 | 1 | `feature/cash-account` | ☑ derived kind, no migration |
| 4 | [Account reordering](plan%204/plan.md) | 8 | 3 | `feature/account-reorder` | ☑ |
| 5 | [App Lock PIN & Security settings](plan%205/plan.md) | 7, 24 (Phase 6) | 1 | `feature/app-lock` | ◐ built, device check open |
| 6 | [Privacy mode](plan%206/plan.md) | 7 | 5 | `feature/app-lock` (built with Plan 5) | ◐ totals only; device check open |
| 7 | [Bottom navigation & More](plan%207/plan.md) | 10, 26 | 1 | `feature/navigation` | skipped (not needed) |
| 8 | [Category transactions + analysis](plan%208/plan.md) | 10 | 7 | `feature/category-analysis` | ◐ built, device check open |
| 9 | [Home widgets completion](plan%209/plan.md) | 9 | 4, 7 | `feature/home-customization` | ☐ |
| 10 | [Unified transaction engine](plan%2010/plan.md) | 13, 14, 23 | 3 | `feature/transaction-engine` | ☐ |
| 11 | [Recurring transactions](plan%2011/plan.md) | 11 | 10 | `feature/recurring-transactions` | ☐ |
| 12 | [Bills & subscriptions](plan%2012/plan.md) | 18 | 11 | `feature/bills` | ☐ |
| 13 | [CSV import wizard](plan%2013/plan.md) | 12 | 10 | `feature/csv-import` | ☐ |
| 14 | [UPI accessibility capture](plan%2014/plan.md) | 13 | 10 | `feature/upi-accessibility` | ☐ |
| 15 | [Lending reminders & repayment detection](plan%2015/plan.md) | 15 | 10, 14 | `feature/lending-borrowing` | ☐ |
| 16 | [Startup navigator](plan%2016/plan.md) | 5 | 3, 5, 14 | `feature/startup-navigator` | ☐ |
| 17 | [Savings goals](plan%2017/plan.md) | 19 | 9 | `feature/savings-goals` | ☐ |
| 18 | [Net worth](plan%2018/plan.md) | 20 | 3, 17 | `feature/net-worth` | ☐ |
| 19 | [Monthly & yearly tracker](plan%2019/plan.md) | 16, 17 | 11, 12, 18 | `feature/trackers` | ☐ |
| 20 | [Reports, export & release hardening](plan%2020/plan.md) | 21, 22, 24 (Phase 6–7) | 19 | `feature/reports` | ☐ |

The order follows dependencies, not the requirement's phase numbers. The unified transaction engine (10) comes before the three new sources that feed it (11, 13, 14). The startup navigator (16) comes after the cash, budget, UPI and PIN features it switches on.

## Where the fork stands today

The requirement assumes upstream Cashiro. The fork already has more than that, so several phases are extensions, not new builds.

| Area | Today |
| --- | --- |
| Fork | `origin` is the fork; only `main` exists; no `develop`, no `upstream` remote |
| Branding | `applicationId` `com.ritesh.cashiro`, app name "Cashiro", 2.1.61-beta (code 94), flavors `fdroid` and `standard` |
| Onboarding | 5 steps (welcome, SMS permission, notification permission, accounts, profile) |
| Cash | Implicit: hardcoded `"Cash"` / `"wallet"` strings and an `is_wallet` flag |
| App lock | Biometric / device credential with timeout; no app PIN |
| Privacy mode | None |
| Account order | None; accounts are not a table, they are the key `(bank_name, account_last4)` |
| Home | 7 widgets with drag reorder and hide |
| Navigation | Home, Analytics, Transactions |
| Recurring | `is_recurring` flag and SMS-detected subscriptions only |
| Import | Cashew import and PDF statement import; no generic CSV |
| UPI | Notification listener for 3 bank apps; no accessibility service |
| Write paths | SMS, manual, PDF import, Cashew import each build and hash transactions separately |
| Lend / borrow | Persons, entries, due dates, partial settlement, settle-up, home card |
| Analytics | This month, last month, FY, all time, custom range |
| Goals, net-worth history, reports | None |
| Database | Room v62, manual migrations, exported schemas |

## Conventions for every plan

- **Branching:** one branch per plan off `develop`; merge after its verification passes.
- **Migrations:** the next free number after 62, taken in merge order. Manual `MIGRATION_x_y` in `$APP/data/database/CashiroDatabase.kt`, registered in the builder's migration list, schema JSON exported under `app/schemas/`. See `docs/database-migrations.md`.
- **Backup:** every new table or preference is added to `$APP/data/backup/BackupModels.kt`, `BackupExporter.kt` and `BackupImporter.kt` in the same plan, with a default so older backups still import.
- **Layers:** entity + DAO → repository → use case → ViewModel (`StateFlow`) → Compose screen. Hilt wiring in `$APP/di/DatabaseModule.kt`. Routes in `$APP/presentation/navigation/CashiroDestinations.kt` and `CashiroNavHost.kt`.
- **Strings:** English in `app/src/main/res/values/strings.xml` only; Crowdin supplies other locales.
- **Libraries already in the build:** `sh.calvin.reorderable` (drag lists), `compose-charts`, `opencsv`, `pdfbox-android`, `androidx.biometric`, `androidx.security.crypto`. Prefer these over new dependencies.
- **Reference repositories** (from the Plan 1 license check, `plan 1/baseline-report.md`): TraceLedger is "All Rights Reserved" — no code reuse, write Plans 11 and 13 independently. UPI Expense Tracker (GPL-3.0) and Expense Manager (Apache-2.0) may be reused with their copyright notices kept and the origin noted.
- **No PII** in code, comments, tests or sample data. Use placeholders such as "Person A" and "Merchant A".
- **Themes:** check every new screen in light and dark.
- **Commands:** `./gradlew :app:assembleStandardDebug`, `./gradlew :app:assembleFdroidDebug`, `./gradlew test`, `./gradlew lint`.

## Backlog (not in the 20 steps)

The requirement lists these under "Advanced"; revisit after Plan 20.

- Launcher (home-screen) widgets
- AI-generated financial insights beyond the rule-based ones in Plan 20
- Smart categorization improvements
- Performance optimization pass
