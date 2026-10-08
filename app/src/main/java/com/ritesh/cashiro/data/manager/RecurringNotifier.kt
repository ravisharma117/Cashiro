package com.ritesh.cashiro.data.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ritesh.cashiro.MainActivity
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.LendBorrowType
import com.ritesh.cashiro.data.database.entity.RepaymentSuggestionEntity
import com.ritesh.cashiro.domain.service.BillReminder
import com.ritesh.cashiro.domain.service.LendReminder
import com.ritesh.cashiro.domain.service.LendReminderStage
import com.ritesh.cashiro.receiver.RepaymentActionReceiver
import com.ritesh.cashiro.domain.service.RecurringEvent
import com.ritesh.cashiro.domain.service.RecurringResult
import com.ritesh.cashiro.receiver.RecurringActionReceiver
import com.ritesh.cashiro.utils.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the notifications for recurring schedules. */
@Singleton
class RecurringNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun show(result: RecurringResult) {
        result.created.forEach { notifyCreated(it) }
        result.pending.forEach { notifyDue(it) }
        result.reminders.forEach { notifyReminder(it) }
    }

    fun showBills(reminders: List<BillReminder>) {
        reminders.forEach { reminder ->
            val sub = reminder.subscription
            val title = when {
                reminder.overdue -> context.getString(R.string.bill_notif_overdue_title, sub.merchantName)
                reminder.daysUntil == 0L -> context.getString(R.string.bill_notif_due_today_title, sub.merchantName)
                else -> context.resources.getQuantityString(
                    R.plurals.bill_notif_due_in_title,
                    reminder.daysUntil.toInt(),
                    sub.merchantName,
                    reminder.daysUntil.toInt()
                )
            }
            post(
                id = BILL_NOTIFICATION_BASE + (sub.id.toInt() and 0xFFFFFF),
                title = title,
                text = context.getString(
                    R.string.bill_notif_text,
                    CurrencyFormatter.formatCurrency(sub.amount, sub.currency),
                    reminder.dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                ),
                channelId = BILLS_CHANNEL_ID
            )
        }
    }

    fun showRepayment(suggestion: RepaymentSuggestionEntity, personName: String) {
        post(
            id = REPAYMENT_NOTIFICATION_BASE + (suggestion.id.toInt() and 0xFFFFFF),
            title = context.getString(
                if (suggestion.isIncoming) R.string.repayment_notif_title_from else R.string.repayment_notif_title_to,
                personName
            ),
            text = context.getString(
                R.string.repayment_notif_text,
                CurrencyFormatter.formatCurrency(suggestion.amount, suggestion.currency)
            ),
            actions = listOf(
                repaymentAction(R.string.repayment_action_confirm, RepaymentActionReceiver.ACTION_CONFIRM, suggestion.id),
                repaymentAction(R.string.repayment_action_ignore, RepaymentActionReceiver.ACTION_IGNORE, suggestion.id)
            ),
            channelId = LEND_CHANNEL_ID
        )
    }

    fun dismissRepayment(suggestionId: Long) {
        manager.cancel(REPAYMENT_NOTIFICATION_BASE + (suggestionId.toInt() and 0xFFFFFF))
    }

    fun showLendReminders(reminders: List<LendReminder>) {
        reminders.forEach { reminder ->
            val entry = reminder.entry
            val title = context.getString(
                when (reminder.stage) {
                    LendReminderStage.BEFORE -> R.string.lend_notif_before_title
                    LendReminderStage.DUE -> R.string.lend_notif_due_title
                    LendReminderStage.OVERDUE -> R.string.lend_notif_overdue_title
                },
                reminder.personName
            )
            val amount = CurrencyFormatter.formatCurrency(entry.amount, entry.currency)
            val date = reminder.dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            post(
                id = LEND_NOTIFICATION_BASE + (entry.id.toInt() and 0xFFFFFF),
                title = title,
                text = context.getString(
                    if (entry.type == LendBorrowType.LENT) R.string.lend_notif_owed_text else R.string.lend_notif_owe_text,
                    amount,
                    date
                ),
                channelId = LEND_CHANNEL_ID
            )
        }
    }

    private fun repaymentAction(label: Int, action: String, suggestionId: Long): NotificationCompat.Action {
        val intent = Intent(context, RepaymentActionReceiver::class.java).apply {
            this.action = action
            putExtra(RepaymentActionReceiver.EXTRA_ID, suggestionId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (suggestionId.toInt() and 0xFFFFFF) * 2 + (if (action == RepaymentActionReceiver.ACTION_CONFIRM) 0 else 1),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), pending).build()
    }

    /** Clears a due-date notification once the user answered it. */
    fun dismiss(scheduleId: Long, dueDate: LocalDate) {
        manager.cancel(notificationId(scheduleId, dueDate))
    }

    private fun notifyCreated(event: RecurringEvent) {
        val s = event.schedule
        post(
            id = notificationId(s.id, event.dueDate),
            title = context.getString(R.string.recurring_notif_added_title, s.title),
            text = context.getString(
                R.string.recurring_notif_added_text,
                CurrencyFormatter.formatCurrency(s.amount, s.currency)
            )
        )
    }

    private fun notifyDue(event: RecurringEvent) {
        val s = event.schedule
        post(
            id = notificationId(s.id, event.dueDate),
            title = context.getString(R.string.recurring_notif_due_title, s.title),
            text = context.getString(
                R.string.recurring_notif_due_text,
                CurrencyFormatter.formatCurrency(s.amount, s.currency)
            ),
            actions = listOf(
                action(R.string.recurring_action_add, RecurringActionReceiver.ACTION_ADD, event),
                action(R.string.recurring_action_skip, RecurringActionReceiver.ACTION_SKIP, event)
            )
        )
    }

    private fun notifyReminder(event: RecurringEvent) {
        val s = event.schedule
        post(
            id = notificationId(s.id, event.dueDate) + 1,
            title = context.getString(R.string.recurring_notif_reminder_title, s.title),
            text = context.getString(
                R.string.recurring_notif_reminder_text,
                CurrencyFormatter.formatCurrency(s.amount, s.currency),
                event.dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            )
        )
    }

    private fun post(
        id: Int,
        title: String,
        text: String,
        actions: List<NotificationCompat.Action> = emptyList(),
        channelId: String = CHANNEL_ID
    ) {
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.cashiro)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
        actions.forEach { builder.addAction(it) }
        manager.notify(id, builder.build())
    }

    private fun action(label: Int, action: String, event: RecurringEvent): NotificationCompat.Action {
        val intent = Intent(context, RecurringActionReceiver::class.java).apply {
            this.action = action
            putExtra(RecurringActionReceiver.EXTRA_ID, event.schedule.id)
            putExtra(RecurringActionReceiver.EXTRA_DATE, event.dueDate.toEpochDay())
        }
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(event.schedule.id, event.dueDate) + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), pending).build()
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recurring_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                BILLS_CHANNEL_ID,
                context.getString(R.string.bills_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LEND_CHANNEL_ID,
                context.getString(R.string.lend_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    /** Stable per schedule and date, even numbers only, so reminders can use the odd one next to it. */
    private fun notificationId(scheduleId: Long, dueDate: LocalDate): Int =
        (31 * scheduleId.hashCode() + dueDate.toEpochDay().hashCode()) and 0x7FFFFFFE

    companion object {
        const val CHANNEL_ID = "recurring_transactions"
        const val BILLS_CHANNEL_ID = "bill_reminders"
        const val LEND_CHANNEL_ID = "lend_borrow"
        private const val REPAYMENT_NOTIFICATION_BASE = 1_600_000_000
        private const val LEND_NOTIFICATION_BASE = 1_700_000_000
        private const val BILL_NOTIFICATION_BASE = 1_500_000_000
    }
}
