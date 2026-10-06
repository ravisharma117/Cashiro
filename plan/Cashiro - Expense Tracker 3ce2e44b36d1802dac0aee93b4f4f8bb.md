# Cashiro - Expense Tracker

Owner: Ravi Sharma
Tags: Projects
Mode: Active
Status: In progress
Priority Level: 9
Verification: Verified

# Custom Expense Tracker — Master Development Plan

**Cashiro — Smart Personal Expense Tracker**

Core features:

- 💰 Daily expense & income tracking
- 🔄 Recurring transactions
- 📥 CSV transaction import
- 📱 UPI transaction import
- 📊 Spending reports & charts
- 🏷️ Categories and subcategories
- 💳 Multiple accounts/wallets
- 🔔 Bill & payment reminders
- 🔎 Search and filters
- ☁️ Backup/sync

## 1. Project Vision

Build a custom open-source Android personal finance application for the Indian market by using **Cashiro as the base application** and selectively implementing the best features and ideas from TraceLedger, UPI Expense Tracker, and Expense Manager.

### Core principle

> **Cashiro = our primary codebase**
> 
> 
> **Other projects = feature, UI/UX, and architecture references**
> 

We will not blindly merge four repositories. We will study the relevant implementations and integrate selected features cleanly into our Cashiro-based application.

Here is my forked repo - [https://github.com/ravisharma117/Cashiro](https://github.com/ravisharma117/Cashiro)

---

# 2. Reference Repositories

| Project | GitHub | Primary Use |
| --- | --- | --- |
| **Cashiro** | [https://github.com/ritesh-kanwar/Cashiro](https://github.com/ritesh-kanwar/Cashiro) | **Primary/base application** |
| **TraceLedger** | [https://github.com/GreenIcePhoenix/TraceLedger](https://github.com/GreenIcePhoenix/TraceLedger) | Recurring transactions + CSV import |
| **UPI Expense Tracker** | [https://github.com/xxwarwolfxx/UPI-Expense-Tracker](https://github.com/xxwarwolfxx/UPI-Expense-Tracker) | UPI Accessibility Service + UPI app detection |
| **Expense Manager** | [https://github.com/nkuppan/expensemanager](https://github.com/nkuppan/expensemanager) | UI/UX + Cash account + App Lock + Home customization |

### License note

Before copying source code from any reference project, verify its current license and compatibility with the license of the Cashiro-based application.

Use the other repositories as references where direct code reuse is not appropriate.

---

# 3. Base Application — Cashiro

## Role

Cashiro will be the starting point and primary codebase for our application.

Repository:

[https://github.com/ritesh-kanwar/Cashiro](https://github.com/ritesh-kanwar/Cashiro)

## Preserve existing Cashiro functionality

- Transactions
- Income
- Expenses
- Accounts
- Categories
- Budgets
- Analytics
- SMS transaction detection
- Bank parsers
- Transaction rules
- Subscriptions
- Backup / Restore
- Search and filtering
- Multiple currencies
- Account balances
- Credit cards
- Existing parser ecosystem

## Initial Cashiro fork tasks

- Fork/copy repository into our own GitHub organization/account
- Create our own application name
- Change Android package/application ID
- Change application icon and branding
- Build successfully
- Install and test APK
- Verify all existing Cashiro functionality before modifications
- Establish Git branching strategy
- Create a clean development baseline

---

# 4. Feature Sources

## Cashiro

Use for:

- Core application
- Transactions
- Accounts
- Categories
- Budgets
- Analytics
- SMS processing
- Bank parsers
- Rules
- Subscriptions
- Backup and restore

## TraceLedger

Repository:

[https://github.com/GreenIcePhoenix/TraceLedger](https://github.com/GreenIcePhoenix/TraceLedger)

Use/investigate:

- Recurring transactions
- CSV transaction import
- Account management ideas
- Transaction architecture
- Budget concepts
- Indian bank SMS parsing
- Historical SMS scanning
- Local database approach

## UPI Expense Tracker

Repository:

[https://github.com/xxwarwolfxx/UPI-Expense-Tracker](https://github.com/xxwarwolfxx/UPI-Expense-Tracker)

Use/investigate:

- Android Accessibility Service
- UPI transaction detection
- Google Pay
- PhonePe
- Paytm
- Amazon Pay
- Startup/onboarding navigator
- UPI transaction extraction
- Duplicate detection

## Expense Manager

Repository:

[https://github.com/nkuppan/expensemanager](https://github.com/nkuppan/expensemanager)

Use/investigate:

- Cash account
- App Lock
- Account reordering
- Advanced Home screen configuration
- Category-focused navigation
- Category transactions + analysis
- UI/UX patterns
- Android architecture patterns

---

# 5. Phase 1 — User Experience Improvements

## 5.1 Startup Navigator / Slider

Create a guided first-launch setup.

### Screen 1 — Welcome

**Your money. One simple view.**

Next

### Screen 2 — Accounts

Set up:

- Bank account
- Cash
- Credit card
- Wallet

Next

### Screen 3 — Transaction Capture

Enable:

- Bank SMS detection

Next

### Screen 4 — UPI

Enable:

- UPI transaction detection

Next

### Screen 5 — Budget

Set monthly budget.

Next

### Screen 6 — Security

Enable:

- App Lock
- Biometric

Finish

The setup must remain optional, with a Skip option.

---

# 6. Phase 2 — Cash Account

Add a proper Cash account.

Example:

Cash — ₹8,500

Cash should behave like a normal financial account.

Example transaction:

₹500 — Food — Cash

Cash balance must be included in overall account and financial calculations.

---

# 7. Phase 3 — App Lock & Privacy

## App Lock

Settings → Security → App Lock

Support:

- PIN
- Biometric authentication
- Lock timeout

## Privacy Mode

Allow sensitive amounts to be hidden.

Example:

₹85,450

can be displayed as:

₹••••••

Potentially hide:

- Account balances
- Transaction amounts
- Net worth
- Loan balances

---

# 8. Phase 4 — Account Reordering

Allow users to drag and reorder accounts.

Example:

1. HDFC Bank
2. SBI Bank
3. Cash
4. Credit Card
5. Wallet

The selected order should be used consistently in:

- Home screen
- Account selector
- Transaction entry
- Account list

---

# 9. Phase 5 — Custom Home Screen

Allow users to select and reorder Home screen sections.

## Available widgets/cards

- Total Balance
- Monthly Income
- Monthly Expenses
- Savings
- Recent Transactions
- Accounts
- Budgets
- Categories
- Subscriptions
- Recurring Transactions
- Lending / Borrowing
- Savings Goals

Example:

```
Total Balance

September
Income       ₹85,000
Expenses     ₹42,500
Savings      ₹42,500

Recent Transactions
Amazon       -₹1,250
Salary      +₹85,000
Swiggy        -₹650

Budget
Food
₹6,500 / ₹10,000
```

---

# 10. Phase 6 — Category-Centered Navigation

Use a bottom navigation structure inspired by Expense Manager.

## Bottom navigation

- Home
- Transactions
- Categories
- Analysis
- More

## Categories screen

Example:

- Food
- Shopping
- Transport
- Home
- Bills
- Work
- Entertainment
- Health

Selecting a category should show both:

### Transactions

Example:

Swiggy — ₹650

Restaurant — ₹850

Blinkit — ₹1,200

### Analysis

- Monthly total
- Daily average
- Previous-month comparison
- Spending trend
- Percentage of total spending

This makes the Category screen useful for both reviewing transactions and understanding spending.

---

# 11. Phase 7 — Recurring Transactions

Reference:

[https://github.com/GreenIcePhoenix/TraceLedger](https://github.com/GreenIcePhoenix/TraceLedger)

Support:

- Daily
- Weekly
- Monthly
- Yearly
- Custom interval
- Start date
- End date
- Automatic transaction creation
- Reminder
- Skip occurrence
- Pause recurring transaction
- Edit future occurrences

Examples:

### Netflix

₹649

Monthly

5th of every month

### House Rent

₹15,000

Monthly

1st of every month

### Insurance

₹18,000

Yearly

15 March

---

# 12. Phase 8 — CSV Transaction Import

Reference:

[https://github.com/GreenIcePhoenix/TraceLedger](https://github.com/GreenIcePhoenix/TraceLedger)

Import flow:

```
Select CSV
    ↓
Detect columns
    ↓
Map columns
    ↓
Preview
    ↓
Validate
    ↓
Duplicate detection
    ↓
Import
```

## Supported fields

- Date
- Description
- Amount
- Debit/Credit
- Category
- Account
- Merchant
- Reference number

## Important

Implement duplicate detection so the same transaction cannot accidentally be imported multiple times.

---

# 13. Phase 9 — UPI Transaction Automation

Reference:

[https://github.com/xxwarwolfxx/UPI-Expense-Tracker](https://github.com/xxwarwolfxx/UPI-Expense-Tracker)

Investigate Android Accessibility Service support for:

- Google Pay
- PhonePe
- Paytm
- Amazon Pay

## Architecture

```
Google Pay
PhonePe
Paytm
Amazon Pay
      ↓
Accessibility Service
      ↓
UPI Transaction Parser
      ↓
Normalize Transaction
      ↓
Duplicate Detection
      ↓
Cashiro Transaction Engine
      ↓
Cashiro Database
```

## Critical architecture rule

Do not create a separate UPI transaction database.

UPI transactions should enter the same transaction pipeline as:

- SMS transactions
- Manual transactions
- Imported transactions

Final flow:

```
SMS ───────────────┐
                   │
UPI Accessibility ─┤
                   ├──→ Transaction Engine
Manual Entry ──────┤
                   │
CSV Import ────────┘
                         ↓
                    Cashiro DB
```

---

# 14. Phase 10 — Transaction Processing Pipeline

Create one normalized transaction model regardless of source.

## Sources

- SMS
- UPI Accessibility Service
- Manual entry
- CSV import

## Processing

```
Transaction Source
        ↓
Parser
        ↓
Normalization
        ↓
Merchant Detection
        ↓
Category Detection
        ↓
Duplicate Detection
        ↓
Transaction Review
        ↓
Confirmed Transaction
        ↓
Cashiro Database
```

Where possible, use:

- Merchant rules
- Category rules
- Bank reference number
- UPI transaction ID
- Amount/date matching

to prevent duplicates.

---

# 15. Phase 11 — Lending & Borrowing

This will be a major custom feature.

## I Lent

Example:

Rahul

Lent: ₹20,000

Received: ₹5,000

Outstanding: ₹15,000

## I Borrowed

Example:

Amit

Borrowed: ₹30,000

Paid: ₹10,000

Outstanding: ₹20,000

## Support

- Person
- Lent / Borrowed
- Amount
- Date
- Due date
- Partial repayments
- Multiple repayments
- Notes
- Reminders
- Settlement
- Complete history

## UPI integration

If UPI detects:

₹5,000 received from Rahul

show:

**Possible repayment from Rahul**

Actions:

- Confirm repayment
- Ignore
- Assign to another transaction

After confirmation:

Rahul outstanding balance changes from ₹20,000 to ₹15,000.

---

# 16. Phase 12 — Monthly Tracker

Main monthly view:

## September 2026

| Category | Budget | Spent | Remaining |
| --- | --- | --- | --- |
| Food | ₹10,000 | ₹6,200 | ₹3,800 |
| Shopping | ₹8,000 | ₹4,500 | ₹3,500 |
| Transport | ₹5,000 | ₹2,800 | ₹2,200 |
| Entertainment | ₹3,000 | ₹1,200 | ₹1,800 |
| Bills | ₹15,000 | ₹12,500 | ₹2,500 |

Include:

- Income
- Expenses
- Savings
- Budget usage
- Category breakdown
- Account breakdown
- Lending
- Borrowing
- Recurring payments
- Subscriptions

---

# 17. Phase 13 — Yearly Tracker

## 2026 Overview

| Month | Income | Expense | Savings |
| --- | --- | --- | --- |
| Jan | ₹80K | ₹51K | ₹29K |
| Feb | ₹82K | ₹48K | ₹34K |
| Mar | ₹85K | ₹52K | ₹33K |
| … | … | … | … |
| Sep | ₹85K | ₹48K | ₹37K |

Charts:

- Monthly income
- Monthly expenses
- Monthly savings
- Category spending
- Cash flow
- Lending
- Borrowing
- Net worth

Year summary:

```
Income:       ₹XX
Expenses:     ₹XX
Savings:      ₹XX
Savings Rate: XX%
Money Lent:   ₹XX
Money Owed:   ₹XX
```

---

# 18. Phase 14 — Bills & Subscriptions

Build on Cashiro’s existing subscription functionality.

Track:

- Electricity
- Mobile
- Internet
- Insurance
- Streaming
- Software subscriptions
- Rent
- Other recurring bills

Show:

**Upcoming payments — next 30 days**

Support:

- Due date
- Amount
- Frequency
- Reminder
- Paid/unpaid
- Recurring transaction link

---

# 19. Phase 15 — Savings Goals

Allow users to create goals.

Examples:

### New Laptop

Target: ₹100,000

Saved: ₹35,000

Remaining: ₹65,000

### Emergency Fund

Target: ₹300,000

Saved: ₹150,000

Remaining: ₹150,000

Show progress and monthly contribution.

---

# 20. Phase 16 — Net Worth

Calculate:

## Assets

- Bank accounts
- Cash
- Investments
- Money lent
- Other assets

## Liabilities

- Credit cards
- Money borrowed
- Loans

### Net Worth

Assets − Liabilities

Track net worth monthly and yearly.

---

# 21. Phase 17 — Reports

## Monthly Report

- Income
- Expenses
- Savings
- Category breakdown
- Account breakdown
- Budget performance
- Loans
- Bills
- Subscriptions

## Yearly Report

- Total income
- Total expenses
- Total savings
- Savings rate
- Category trends
- Monthly trends
- Lending
- Borrowing
- Net worth

Export options can include:

- PDF
- CSV

---

# 22. Future Smart Insights

After the core application is stable, add intelligent insights.

Example:

> You spent ₹12,400 more this month than last month.
> 

Breakdown:

- Shopping +₹6,200
- Food +₹3,100
- Transport +₹2,000

Then identify major transactions:

- Amazon — ₹3,200
- Flipkart — ₹2,100
- Swiggy — ₹1,400

The goal is to make the application explain spending instead of only displaying charts.

---

# 23. Proposed Android Architecture

```
                    Android App
                        │
        ┌───────────────┼────────────────┐
        │               │                │
       SMS              UPI            Manual
        │               │                │
        ▼               ▼                ▼
 Cashiro Parser    Accessibility      Entry
        │              Service           │
        └───────────────┼────────────────┘
                        │
                        ▼
                Transaction Engine
                        │
                Duplicate Detection
                        │
                 Categorization
                        │
                        ▼
                   Cashiro DB
                        │
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
    Accounts        Categories         Budgets
        │               │                │
        ▼               ▼                ▼
    Lending         Analytics        Recurring
        │                                │
        └───────────────┬────────────────┘
                        ▼
                       Home
```

---

# 24. Development Phases

## Phase 0 — Fork & Stabilize

- Fork Cashiro
- Create our own GitHub repository
- Build
- Run
- Install
- Rename
- Package ID
- Icon
- Branding
- Verify existing features

## Phase 1 — UX

- Startup navigator
- Cash account
- App Lock
- Account reorder
- Custom Home
- Category navigation

## Phase 2 — Transactions

- Recurring transactions
- CSV import
- Import preview
- Column mapping
- Duplicate detection

## Phase 3 — UPI

- Accessibility Service
- Google Pay
- PhonePe
- Paytm
- Amazon Pay
- UPI transaction extraction
- Unified transaction pipeline

## Phase 4 — Personal Finance

- Lending
- Borrowing
- Repayments
- Due dates
- Reminders
- Savings goals
- Net worth

## Phase 5 — Bills & Reports

- Bills
- Subscriptions
- Monthly reports
- Yearly reports
- PDF/CSV export

## Phase 6 — Security

- App Lock
- PIN
- Biometric
- Privacy mode
- Encrypted backup

## Phase 7 — Advanced

- Widgets
- Smart categorization
- AI financial insights
- Advanced analytics
- Performance optimization
- UI polish

---

# 25. Git Branch Strategy

```
main
│
└── develop
    │
    ├── feature/recurring-transactions
    ├── feature/csv-import
    ├── feature/upi-accessibility
    ├── feature/cash-account
    ├── feature/app-lock
    ├── feature/account-reorder
    ├── feature/home-customization
    ├── feature/category-analysis
    ├── feature/lending-borrowing
    ├── feature/savings-goals
    └── feature/net-worth
```

Each major feature should be developed independently and merged only after testing.

---

# 26. Product Navigation

## Bottom Navigation

**Home | Transactions | Categories | Analysis | More**

## More

- Accounts
- Budgets
- Recurring Transactions
- Subscriptions
- Lending & Borrowing
- Savings Goals
- Import / Export
- Reports
- Security
- Backup
- Settings

---

# 27. Product Goal

The final application should combine:

**Cashiro**

Core expense tracker + SMS + parsers + subscriptions + analytics

- 

**TraceLedger**

Recurring transactions + CSV import

- 

**UPI Expense Tracker**

UPI Accessibility Service + Google Pay + PhonePe + Paytm + Amazon Pay

- 

**Expense Manager**

Cash account + App Lock + Account reorder + Home customization + Category UX

- 

**Our Custom Features**

Lending + Borrowing + Repayments + UPI repayment detection + advanced monthly/yearly tracking

---

# 27 (A). Lending and Borrowing Management

manage it like that..

# 28. Final Product Direction

> **One personal finance application that automatically captures transactions from SMS and UPI, keeps accounts and cash organized, tracks budgets and recurring payments, manages money lent and borrowed, and provides clear monthly and yearly financial analysis.**
> 

The application should be:

- Android-first
- Kotlin-based
- Offline-first where practical
- Privacy-focused
- Open source
- Modular
- Easy to maintain
- Designed primarily for Indian banking and UPI usage

---

# 29. Master Repository References

### Base

[https://github.com/ritesh-kanwar/Cashiro](https://github.com/ritesh-kanwar/Cashiro)

### Recurring + CSV

[https://github.com/GreenIcePhoenix/TraceLedger](https://github.com/GreenIcePhoenix/TraceLedger)

### UPI Automation

[https://github.com/xxwarwolfxx/UPI-Expense-Tracker](https://github.com/xxwarwolfxx/UPI-Expense-Tracker)

### UI/UX + Security Reference

[https://github.com/nkuppan/expensemanager](https://github.com/nkuppan/expensemanager)