package com.ritesh.cashiro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ritesh.cashiro.domain.service.RepaymentService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Confirm" and "Ignore" on a possible-repayment notification. */
class RepaymentActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepaymentEntryPoint {
        fun repaymentService(): RepaymentService
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id == -1L) return
        val action = intent.action
        val service = EntryPointAccessors
            .fromApplication(context.applicationContext, RepaymentEntryPoint::class.java)
            .repaymentService()

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_CONFIRM -> service.confirm(id)
                    ACTION_IGNORE -> service.ignore(id)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Repayment action failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "RepaymentActionReceiver"
        const val ACTION_CONFIRM = "com.naxits.paisatracker.REPAYMENT_CONFIRM"
        const val ACTION_IGNORE = "com.naxits.paisatracker.REPAYMENT_IGNORE"
        const val EXTRA_ID = "repayment_suggestion_id"
    }
}
