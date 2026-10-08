package com.ritesh.cashiro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ritesh.cashiro.data.manager.RecurringNotifier
import com.ritesh.cashiro.data.repository.RecurringTransactionRepository
import com.ritesh.cashiro.domain.service.RecurringRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles "Add now" and "Skip" on the notification of a remind-only recurring schedule. */
class RecurringActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionEntryPoint {
        fun recurringRunner(): RecurringRunner
        fun recurringRepository(): RecurringTransactionRepository
        fun recurringNotifier(): RecurringNotifier
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val epochDay = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        if (id == -1L || epochDay == Long.MIN_VALUE) return
        val dueDate = LocalDate.ofEpochDay(epochDay)
        val action = intent.action

        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ActionEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_ADD -> entry.recurringRunner().addPending(id, dueDate)
                    ACTION_SKIP -> {
                        entry.recurringRepository().skipPending(id, dueDate)
                        entry.recurringNotifier().dismiss(id, dueDate)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Recurring action failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "RecurringActionReceiver"
        const val ACTION_ADD = "com.naxits.paisatracker.RECURRING_ADD"
        const val ACTION_SKIP = "com.naxits.paisatracker.RECURRING_SKIP"
        const val EXTRA_ID = "recurring_id"
        const val EXTRA_DATE = "recurring_due_epoch_day"
    }
}
