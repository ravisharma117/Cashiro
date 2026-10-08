# Plan 12 — Bills & subscriptions

## Goal

Extend the existing subscriptions feature to cover bills (electricity, mobile, internet, insurance, rent and others) with paid / unpaid status, reminders, a link to recurring schedules, and an "Upcoming payments — next 30 days" view.

Requirement: §18.

## Depends on

Plan 11 (recurring schedules to link to).

## Current state

- `SubscriptionEntity`: merchant name, amount, `next_payment_date`, `state` (`ACTIVE` / `HIDDEN`), bank name, `umn` (mandate number), category, subcategory, currency, `billing_cycle` (string), `last_paid_date`.
- `$APP/data/repository/SubscriptionRepository.kt`: `getUpcomingSubscriptions(daysAhead = 7)`, mandate-based creation for several banks, `matchTransactionToSubscription`, `updateNextPaymentDateAfterCharge`, `updatePaymentStatus`.
- UI: `$APP/presentation/ui/features/subscriptions/SubscriptionsScreen.kt` (960 lines), `SubscriptionsViewModel.kt`; Home widget `UPCOMING_SUBSCRIPTIONS`; add via `SubscriptionTabContent.kt`.
- Notifications: `UPCOMING_NOTIFICATIONS_ENABLED` and per-subscription disable set (`DISABLED_SUBSCRIPTION_NOTIFICATION_IDS`), sent from the daily `ReminderReceiver`.
- No bill type, no explicit paid / unpaid per cycle, no payment history, no link to a schedule.

## Changes

1. Extend `SubscriptionEntity`:
   - `kind`: `SUBSCRIPTION` or `BILL`
   - `bill_type`: `ELECTRICITY`, `MOBILE`, `INTERNET`, `INSURANCE`, `STREAMING`, `SOFTWARE`, `RENT`, `OTHER`
   - `is_variable_amount`: bills such as electricity change each cycle; `amount` is then the last or expected amount
   - `reminder_days_before` (nullable; null = use the global setting)
   - `recurring_id` (nullable link to a Plan 11 schedule)
   - `account key` to pay from (optional)
2. New table `bill_payments`: subscription id, due date, amount, status (`UNPAID`, `PAID`, `SKIPPED`), paid date, transaction id. Unique on `(subscription_id, due_date)`. This gives paid / unpaid per cycle and a history; `last_paid_date` stays as a denormalised convenience.
3. `BillCycleUseCase`: ensures a `bill_payments` row exists for the current cycle; advances to the next due date when paid or skipped, reusing `SubscriptionUtils` cycle arithmetic and Plan 11's `RecurrenceCalculator` for month-end rules.
4. Mark as paid:
   - Manual: "Mark paid" on the bill. Asks whether to also create the expense transaction (through `TransactionEngine`) or link an existing one.
   - Automatic: when `matchTransactionToSubscription` matches an incoming transaction, mark the current cycle paid and store the transaction id. For variable bills, match on merchant within the due window, ignoring amount.
5. Recurring link: a bill can be created from a recurring schedule and vice versa. When linked and the schedule auto-creates the transaction, the cycle is marked paid by that transaction — never create two transactions for one payment.
6. Upcoming payments view: `getUpcomingPayments(days = 30)` returning bills and subscriptions due in the window plus overdue unpaid ones, sorted by date, with a total. Show it as the default section of the screen, grouped Overdue / This week / Later.
7. `SubscriptionsScreen`: rename the screen title to "Bills & Subscriptions", add a filter for kind and bill type, status chips (paid, due in N days, overdue), and a payment-history section on the detail.
8. Add / edit form: kind selector, bill type, variable amount switch, due date, frequency, reminder, pay-from account, "create recurring transaction" switch.
9. Reminders: per-bill reminder days before due date plus an overdue reminder, through the existing daily reminder path. Respect the existing global and per-item switches.
10. Home widget `UPCOMING_SUBSCRIPTIONS`: use the 30-day query and show unpaid count; rename its label to "Upcoming payments".

## Data changes

- Migration (next free number): add the columns to `subscriptions` with defaults (`kind = 'SUBSCRIPTION'`, `bill_type = 'OTHER'`), create `bill_payments`. Back-fill one `PAID` row per subscription that has a `last_paid_date`.
- Backup: new columns default for older files; add `billPayments` list.

## Tests

- `BillCycleUseCaseTest`: cycle creation, advance on paid and on skipped, overdue detection, variable-amount update.
- Upcoming query: window boundaries (day 0, day 30, day 31), overdue included, hidden excluded.
- Matching: fixed bill matches on amount and merchant; variable bill matches on merchant only; a linked recurring schedule does not produce a duplicate transaction.

## Verification

- Existing subscriptions appear unchanged after upgrade, typed as subscriptions.
- Add an electricity bill due in 10 days with variable amount: appears in Upcoming; mark paid with a new amount creates the expense and moves the due date to next month.
- A matching SMS transaction marks the current cycle paid automatically.
- An unpaid bill past its due date shows as overdue and triggers the overdue reminder.
- A bill linked to a recurring schedule produces exactly one transaction per cycle.
- Light and dark theme check.

## Done when

- [ ] Bills and subscriptions share one screen with kind and type
- [ ] Paid / unpaid tracked per cycle with history
- [ ] Upcoming payments for the next 30 days, including overdue
- [ ] Per-bill reminders
- [ ] Recurring link without double transactions
- [ ] Backup updated; tests pass

## Open decisions

- Keep the table name `subscriptions` and add `kind` (recommended; no data move) versus renaming to a neutral name.
- Whether rent belongs here or only under recurring transactions. Recommended: here as a bill type, linked to a schedule when the user wants the transaction created automatically.

## Outcome (2026-10-08)

Built without Plans 9 and 10, so the Home widget is unchanged and "Mark paid" creates the expense through the existing add path.

- Database version 64: new columns on `subscriptions` (kind, bill type, variable amount, reminder days, recurring link, pay-from account, reminder marks) and the `bill_payments` table (unique per subscription and due date). Existing rows stay subscriptions; a paid row is back-filled from each last paid date. Table name `subscriptions` kept. Backups include `bill_payments`; a merge import drops the recurring link, because schedule ids change.
- Screen renamed "Bills & Subscriptions", with All / Bills / Subscriptions filter, an Upcoming payments card (next 30 days, overdue first, with total), a Bill tag, and a payment history plus "Skip this payment" in the detail sheet.
- "Mark paid" asks: add the expense (amount editable, a variable bill remembers the real amount) or only record the payment. It is idempotent per cycle.
- An SMS that matches a bill marks the cycle paid and stores the transaction id; a variable bill matches on merchant alone.
- Recurring link: the form switch "Add the transaction for me" creates a recurring schedule from the billing cycle. That schedule's transaction settles the cycle; marking paid by hand steps the schedule over so nothing is added twice; a cycle paid in advance is left alone.
- Reminders: bills 3 days ahead by default, per-bill choice, one overdue reminder per due date, on their own channel. The global switch and per-item switches are respected. Uses the same exact alarm as recurring transactions.
- A new bill is entered with the date it is next due and logs nothing until paid. A subscription keeps the old behaviour.
- Also fixed: the Plan 11 alarm could wake every minute overnight when a reminder time had already passed.
- Not done: Home widget "Upcoming payments", account choice shown in the bill form beyond the existing account picker, bill-type filter chips.
- 31 new tests. Not yet checked on a phone.
