# Plan 15 — Lending reminders & repayment detection

## Goal

Finish the lending and borrowing feature: due-date reminders, and automatic "Possible repayment from …" suggestions when an incoming or outgoing transaction matches a person with an outstanding balance.

Requirement: §15, §27 (A).

## Depends on

Plan 10 (engine hook after save), Plan 14 (UPI counterparty names; SMS and manual sources also work without it).

## Current state

Most of §15 already exists.

| Requirement item | Today |
| --- | --- |
| Person | `LendBorrowPersonEntity` (name, phone, notes, color, avatar, category, archived) |
| Lent / Borrowed, amount, date | `LendBorrowTransactionEntity` with `LendBorrowType` `LENT`, `BORROWED`, `SETTLEMENT_LENT`, `SETTLEMENT_BORROWED` |
| Due date | `due_date` column |
| Partial and multiple repayments | Settlement entries; `SettleLendBorrowUseCase` |
| Notes | On person; entry has `title` |
| Settlement, history | `SettleUpSheet.kt`, `PersonDetailScreen.kt` |
| Link to a real transaction | `transaction_id`; `MarkTransactionAsLoanUseCase`, `UnmarkTransactionAsLoanUseCase` |
| Home | `LOANS` widget, `LendBorrowCard.kt`, `LoanBalanceCard.kt` |
| Reminders | **Missing** |
| Repayment detection | **Missing** |

Code: `$APP/data/repository/LendBorrowRepository.kt`, `$APP/domain/usecase/LendBorrowUseCases.kt`, `$APP/presentation/ui/features/lendborrow/`.

## Changes

### Reminders

1. Add to `LendBorrowTransactionEntity`: `reminder_days_before` (nullable) and `notes` (nullable, per entry).
2. Extend the daily reminder path (`NotificationScheduler`, `ReminderReceiver`): for unsettled entries, notify on the reminder day, on the due date, and once when overdue. Notification opens the person detail. Use a separate notification channel "Lending & borrowing" with its own switch in `NotificationScreen.kt`.
3. Person detail and list: due and overdue badges; sort option by nearest due date.
4. Add / edit entry sheet (`AddEditLendBorrowTransactionSheet.kt`): reminder picker next to the due date; notes field.

### Repayment detection

5. `$APP/domain/usecase/DetectRepaymentUseCase.kt`, called by `TransactionEngine` after a transaction is saved:
   - Incoming money (income / credit) → candidates are persons who owe the user (net `LENT` outstanding > 0).
   - Outgoing money (expense) → candidates are persons the user owes.
   - Name match between the transaction's counterparty / merchant and the person's name or saved aliases: normalised, token-based, tolerant of initials and ordering. Phone-number match when the UPI ID contains the person's number.
   - Amount is a supporting signal only: equal to the outstanding or to a single open entry raises confidence, but partial repayments are normal, so a different amount does not rule a match out. An amount greater than the outstanding lowers confidence.
   - Produce a suggestion only above a confidence threshold; never settle automatically.
6. Table `repayment_suggestions`: transaction id, person id, suggested entry id (nullable), amount, confidence, status (`PENDING`, `CONFIRMED`, `IGNORED`), timestamps. Unique on transaction id.
7. Surfacing:
   - Notification "Possible repayment from Person A" with Confirm and Ignore actions (extend `NotificationActionReceiver`).
   - Banner on the transaction's detail screen and on the person's detail screen.
   - A pending list at the top of `LendBorrowScreen`.
8. Actions:
   - **Confirm repayment:** create the settlement entry linked to the transaction (reuse `SettleLendBorrowUseCase` / `MarkTransactionAsLoanUseCase`); outstanding updates, for example 20,000 → 15,000.
   - **Ignore:** mark ignored; the same transaction is never suggested again.
   - **Assign to another:** pick a different person or a specific open entry, then confirm.
9. Aliases: add an `aliases` column on the person (list of names as they appear in bank / UPI text). Confirming a suggestion whose counterparty text differs from the person's name offers to remember it.
10. Avoid double counting: a transaction confirmed as a repayment is typed so that it is not counted as ordinary income or expense in analytics, consistent with how `LENT` / `BORROWED` types are handled today.
11. Privacy: person names are user data — no logging of names or counterparty text; apply Plan 6 masking to loan balances.

## Data changes

- Migration (next free number): add `reminder_days_before`, `notes` to `lend_borrow_transactions`; `aliases` to `lend_borrow_persons`; create `repayment_suggestions`.
- Backup: new columns default for older files; add `repaymentSuggestions` (pending only is enough).

## Tests

- `DetectRepaymentUseCaseTest` with placeholder names: exact name, reordered tokens, initials, alias, phone in UPI ID; no outstanding → no suggestion; wrong direction → no suggestion; two candidates → the higher confidence wins or both below threshold yields none; partial amount still suggests.
- Confirm flow: outstanding reduces by the amount; transaction linked; analytics type handling.
- Ignore: never re-suggested for that transaction.
- Reminder scheduling: reminder day, due day, overdue once; settled entries excluded.

## Verification

- Lend ₹20,000 to a test person. Add (or receive by SMS / UPI) an incoming ₹5,000 from a matching name: the suggestion appears as a notification and on the transaction; Confirm changes the outstanding to ₹15,000 and the entry appears in the person's history.
- Ignore on another matching transaction leaves the balance unchanged and does not reappear.
- "Assign to another" moves it to the chosen person.
- Borrowed case works in the outgoing direction.
- An entry with a due date fires its reminder and shows as overdue afterwards.
- Light and dark theme check.

## Done when

- [ ] Due-date and overdue reminders with their own switch
- [ ] Suggestions for incoming and outgoing matches from any source
- [ ] Confirm, Ignore, Assign to another all work
- [ ] Nothing is settled without user confirmation
- [ ] Backup updated; tests pass

## Open decisions

- Confidence threshold and whether low-confidence matches appear silently in the pending list without a notification (recommended: yes).
- Whether to read device contacts to improve matching. Recommended: no; aliases are enough and avoid a new permission.

## Outcome (2026-10-08)

Built without Plans 10 and 14, so detection is called after a transaction is saved from a new SMS, an SMS scan, or a manual entry (not for imports or recurring or bill payments, and only for the last 3 days). Decisions: strong matches may notify but notifications are off until the user turns them on; weaker matches only wait in the pending list; contacts are read, but only on request.

- Database version 65: `repayment_suggestions` (one row per transaction, so an ignored one never returns), `aliases` on persons, reminder marks on entries.
- `NameMatcher`, `PhoneNumbers`, `LedgerBuilder`, `RepaymentMatcher` (plain Kotlin): exact, reordered, extra-word, initial and single-word names, saved aliases, phone numbers, UPI addresses. The amount only nudges the score (equal to what is owed up, more than owed down). Direction must fit. Two near-equal candidates are never "strong". Nothing is ever settled without Confirm.
- Lend/Borrow screen: a "Possible repayments" section with Confirm, Ignore and "Someone else". Confirm adds a settlement entry linked to the transaction; the name as written is remembered as an alias when it differs. Notification buttons do the same.
- Reminders: 3 days before, on the day and once when overdue, for unsettled entries with a due date, not for people who have repaid in full or are archived. Same exact alarm as recurring transactions and bills.
- Settings, Notifications, "Lending and borrowing": three switches, all off by default: due-date reminders, possible repayment alerts, use my contacts (asks for the contacts permission; one number at a time; names never stored or logged). PRIVACY.md updated.
- Editing a person no longer wipes learned names (and keeps the created date).
- Backups: aliases travel with persons; pending suggestions are included for full exports and restored on a replace import.
- Not done: per-entry reminder days and notes, an alias editor on the person sheet, banners on transaction detail, `Assign` offering open entries (it picks the person only). Transactions confirmed as repayments stay ordinary income or expense in analytics, as loan entries do today.
- 48 new tests. Not yet checked on a phone.
