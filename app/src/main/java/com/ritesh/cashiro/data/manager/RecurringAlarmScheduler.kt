package com.ritesh.cashiro.data.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ritesh.cashiro.data.database.dao.RecurringTransactionDao
import com.ritesh.cashiro.domain.service.RecurringProcessor
import com.ritesh.cashiro.receiver.RecurringAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one exact alarm set for the next moment a recurring schedule needs attention: a due
 * date, or a reminder before it. With nothing to wait for, no alarm is set.
 */
@Singleton
class RecurringAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: RecurringTransactionDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun reschedule(now: LocalDateTime = LocalDateTime.now()) {
        val wake = RecurringProcessor.nextWake(now, dao.getActive())
        if (wake == null) {
            cancel()
            return
        }
        val triggerAt = wake.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Log.d(TAG, "Next recurring wake-up at $wake")
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(create = true)!!)
        } catch (e: SecurityException) {
            // Exact alarms not allowed: the day-level accuracy of an inexact alarm still works.
            Log.w(TAG, "Exact alarm not permitted, using an inexact one", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(create = true)!!)
        }
    }

    fun cancel() {
        pendingIntent(create = false)?.let { alarmManager.cancel(it) }
    }

    private fun pendingIntent(create: Boolean): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RecurringAlarmReceiver::class.java),
        (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val TAG = "RecurringAlarm"
        private const val REQUEST_CODE = 201
    }
}
