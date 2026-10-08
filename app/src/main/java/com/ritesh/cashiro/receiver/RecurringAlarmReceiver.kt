package com.ritesh.cashiro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ritesh.cashiro.domain.service.RecurringRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Wakes at the next due date or reminder of a recurring schedule and runs it. */
class RecurringAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecurringEntryPoint {
        fun recurringRunner(): RecurringRunner
    }

    override fun onReceive(context: Context, intent: Intent) {
        val runner = EntryPointAccessors
            .fromApplication(context.applicationContext, RecurringEntryPoint::class.java)
            .recurringRunner()

        // The work must finish before the process can be reclaimed, so hold the broadcast open.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runner.run()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Recurring run failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "RecurringAlarmReceiver"
    }
}
