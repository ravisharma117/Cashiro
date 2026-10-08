package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.dao.LendBorrowDao
import com.ritesh.cashiro.data.database.entity.LendBorrowTransactionEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowType
import java.time.LocalDate
import java.time.LocalDateTime

enum class LendReminderStage { BEFORE, DUE, OVERDUE }

data class LendReminder(
    val entry: LendBorrowTransactionEntity,
    val personName: String,
    val stage: LendReminderStage,
    val dueDate: LocalDate
)

/**
 * Due-date reminders for money lent or borrowed: [BEFORE_DAYS] days ahead, on the due date, and
 * once when it is overdue. Each stage goes out once per due date, from 08:00 on. If the phone was
 * off, only the latest stage that applies is sent and the earlier ones count as done.
 *
 * Nothing is sent for an entry whose person no longer owes (or is owed) anything in that
 * direction, and nothing at all while the user has the switch off.
 */
class LendReminderProcessor(private val dao: LendBorrowDao) {

    suspend fun process(now: LocalDateTime, enabled: Boolean): List<LendReminder> {
        if (!enabled || now.toLocalTime().isBefore(RecurringProcessor.RUN_TIME)) return emptyList()
        val today = now.toLocalDate()
        val persons = dao.getActivePersonsList().associateBy { it.id }
        val entries = dao.getUnsettledWithDueDate().filter { it.personId in persons }
        if (entries.isEmpty()) return emptyList()

        val allEntries = dao.getAllTransactionsList()
        val reminders = mutableListOf<LendReminder>()

        for (entry in entries) {
            if (entry.type != LendBorrowType.LENT && entry.type != LendBorrowType.BORROWED) continue
            val person = persons.getValue(entry.personId)
            val ledger = LedgerBuilder.build(listOf(person), allEntries, entry.currency).single()
            val stillOutstanding =
                if (entry.type == LendBorrowType.LENT) ledger.owedToUser.signum() > 0 else ledger.userOwes.signum() > 0
            val due = entry.dueDate!!.toLocalDate()
            if (!stillOutstanding) {
                // Already repaid in full: close every stage for this due date so nothing waits on it
                dao.updateReminderMarks(entry.id, pre = due, due = due, overdue = due)
                continue
            }
            val stage = stageFor(entry, due, today) ?: continue
            // Sending a later stage also settles the earlier ones for this due date
            dao.updateReminderMarks(
                entry.id,
                pre = due,
                due = if (stage != LendReminderStage.BEFORE) due else entry.dueRemindedFor,
                overdue = if (stage == LendReminderStage.OVERDUE) due else entry.overdueRemindedFor
            )
            reminders += LendReminder(entry, person.name, stage, due)
        }
        return reminders
    }

    companion object {
        const val BEFORE_DAYS = 3L

        /** The stage that should go out now for [entry], or null when nothing is due. */
        fun stageFor(entry: LendBorrowTransactionEntity, due: LocalDate, today: LocalDate): LendReminderStage? = when {
            today.isAfter(due) -> LendReminderStage.OVERDUE.takeIf { entry.overdueRemindedFor != due }
            today == due -> LendReminderStage.DUE.takeIf { entry.dueRemindedFor != due && entry.overdueRemindedFor != due }
            !today.isBefore(due.minusDays(BEFORE_DAYS)) ->
                LendReminderStage.BEFORE.takeIf { entry.preRemindedFor != due && entry.dueRemindedFor != due }
            else -> null
        }

        /** The next moment a reminder could go out; null when nothing is waiting. */
        fun nextWake(
            now: LocalDateTime,
            entries: List<LendBorrowTransactionEntity>,
            activePersonIds: Set<Long>,
            enabled: Boolean
        ): LocalDateTime? {
            if (!enabled) return null
            // Same entries [process] looks at, so the alarm never waits on something it will skip
            val moments = entries.filter { !it.isSettled && it.dueDate != null && it.personId in activePersonIds &&
                (it.type == LendBorrowType.LENT || it.type == LendBorrowType.BORROWED) }.flatMap { entry ->
                val due = entry.dueDate!!.toLocalDate()
                buildList {
                    if (entry.preRemindedFor != due && entry.dueRemindedFor != due) add(due.minusDays(BEFORE_DAYS).atTime(RecurringProcessor.RUN_TIME))
                    if (entry.dueRemindedFor != due && entry.overdueRemindedFor != due) add(due.atTime(RecurringProcessor.RUN_TIME))
                    if (entry.overdueRemindedFor != due) add(due.plusDays(1).atTime(RecurringProcessor.RUN_TIME))
                }
            }
            val earliest = moments.minOrNull() ?: return null
            return RecurringProcessor.actionableAt(earliest, now)
        }
    }
}
