# Plan 11 — Recurring transactions

## Goal

User-defined recurring transactions that create real transactions automatically on schedule, with reminders, skip, pause and "edit future occurrences".

Requirement: §11.

TraceLedger is a feature reference only. Its license is "All Rights Reserved" and forbids copying or adapting its code (see `plan/plan 1/baseline-report.md`), so this is written independently from the requirement and this project's own patterns.

## Depends on

Plan 10 (generated transactions go through the engine with source `RECURRING`).

## Current state

- `TransactionEntity.is_recurring` is only a flag; nothing generates transactions.
- `SubscriptionEntity` (`subscriptions`) tracks detected or manually added subscriptions: `next_payment_date`, `billing_cycle` (string), `last_paid_date`. Helpers in `$APP/utils/SubscriptionUtils.kt` (billing-cycle formatting, monthly equivalent). Subscriptions predict a charge; they do not create one.
- Scheduling infrastructure: `$APP/data/manager/NotificationScheduler.kt` (one daily AlarmManager alarm), `$APP/receiver/ReminderReceiver.kt`, `$APP/receiver/BootReceiver.kt`, WorkManager workers in `$APP/worker/` with Hilt (`WorkManagerInitializer.kt`).
- Add screen has two tabs: `TransactionTabContent.kt`, `SubscriptionTabContent.kt`, with `CustomBillingCycleCard.kt` for custom cycles.

## Changes

1. Entity `RecurringTransactionEntity` (`recurring_transactions`): id, title / merchant, amount, currency, transaction type, category, subcategory, account key (bank name + last4), target account key for transfers, frequency (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`, `CUSTOM`), interval count, interval unit for custom, anchor day (day of month / weekday / month-day), start date, end date (nullable), next run date, last run date, `auto_create` (create automatically vs remind only), reminder days before, state (`ACTIVE`, `PAUSED`, `ENDED`), notes, timestamps, `is_sample`.
2. Entity `RecurringOccurrenceEntity` (`recurring_occurrences`): recurring id, due date, status (`CREATED`, `SKIPPED`, `PENDING`), transaction id (nullable). Unique on `(recurring_id, due_date)` — this is what makes generation idempotent.
3. `$APP/domain/service/RecurrenceCalculator.kt`: pure function `nextDate(rule, after)`. Month-end rule: a schedule on the 31st runs on the last day of shorter months and returns to the 31st afterwards; 29 February yearly runs on 28 February in non-leap years.
4. `RecurringTransactionDao`, `RecurringTransactionRepository`, Hilt wiring in `DatabaseModule.kt`.
5. `$APP/worker/RecurringTransactionWorker.kt`: daily periodic work plus a run on app start and after boot. For each active schedule, for every due date up to today that has no occurrence row (catch-up after the phone was off): insert the occurrence, and if `auto_create`, submit a draft to `TransactionEngine` with `source = RECURRING`; store the transaction id. End the schedule when past its end date.
6. Reminders: when `reminder days before` is set, post a notification on that day through the existing notification channel pattern; for remind-only schedules the notification offers "Add now" and "Skip".
7. Actions in `RecurringTransactionRepository`:
   - **Skip next occurrence:** write a `SKIPPED` occurrence and advance `next run date`.
   - **Pause / resume:** state change; resume recalculates the next date from today, without back-filling the paused period.
   - **Edit future occurrences:** changes apply from the next run; already created transactions are untouched.
   - **Delete:** asks whether to keep the transactions already created (default keep).
8. UI in `$APP/presentation/ui/features/recurring/`: list screen (active, paused, ended; next date and amount), add / edit sheet, detail with occurrence history. Destination `RecurringTransactions`; add the row to More (Plan 7).
9. Add screen: a "Repeat" option on the transaction tab that creates a schedule from the entered transaction. Transaction detail: "Make recurring".
10. Mark generated transactions `is_recurring = true` and link back to the schedule from transaction detail.
11. Home `RECURRING` widget (registered in Plan 9): next 3 upcoming occurrences; flip its availability flag.
12. Relationship with subscriptions: keep both for now. Subscriptions = detected from SMS; recurring = user-scheduled. Plan 12 links them.

## Data changes

- Migration (next free number): create both tables with indices on `next_run_date`, `state`, `(recurring_id, due_date)` unique.
- Backup: add both lists to `BackupModels`, export, import (default empty for older backups).

## Tests

- `RecurrenceCalculatorTest`: each frequency; custom every N days / weeks / months; 31st across February; leap day; end date reached; start date in the future.
- Worker / use-case test with a fake clock: catch-up creates one transaction per missed date; a second run creates none; skipped date creates none; paused creates none; resume does not back-fill.
- "Edit future" leaves existing transactions unchanged.

## Verification

- Create a monthly ₹649 schedule on the 5th: on that day a transaction appears with the right account and category, the account balance changes, and the next date moves a month ahead.
- Create one dated three days ago with daily frequency: three transactions appear once, and reopening the app adds none.
- Skip, pause, resume, edit amount, end date behave as described.
- Reboot the phone: the schedule still runs.
- Yearly schedule shows the right next date.
- Light and dark theme check.

## Done when

- [ ] Daily, weekly, monthly, yearly and custom schedules with start and end dates
- [ ] Automatic creation through the engine, idempotent, with catch-up
- [ ] Reminder, skip, pause, edit-future all work
- [ ] Listed under More and on the Home widget
- [ ] Tables in backup; tests pass

## Open decisions

- Catch-up limit after a long gap (recommended: create up to 12 missed occurrences, then ask).
- Exact-time alarms versus a daily worker. Recommended: daily worker; day-level accuracy is enough and avoids the exact-alarm permission.
