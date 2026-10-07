package com.ritesh.cashiro.data.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.utils.PrivacyGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Decides what a proximity sensor reading means. Pure, so it can be tested without a phone. */
object ProximityReveal {
    /**
     * Near means a finger is on the sensor. Many phones report only two values, the maximum
     * range (far) and zero (near); others report centimetres. Under half the range counts as
     * near for both kinds.
     */
    fun isNear(value: Float, maximumRange: Float): Boolean =
        value < maxOf(maximumRange, 1f) / 2f
}

/**
 * Keeps [PrivacyGate] in step with the settings, and listens to the proximity sensor while it
 * is needed: only with the app on screen and both the hide setting and the reveal setting on.
 * It stops listening, and totals hide again, the moment the app goes to the background.
 */
@Singleton
class PrivacyController @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: UserPreferencesRepository
) : DefaultLifecycleObserver {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximity: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    /** Whether this phone has a proximity sensor, so the reveal option makes sense. */
    val hasProximitySensor: Boolean = proximity != null

    private var inForeground = false
    private var hideTotals = false
    private var revealOnProximity = false
    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val sensor = proximity ?: return
            PrivacyGate.setRevealed(ProximityReveal.isNear(event.values[0], sensor.maximumRange))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Call once from the activity. */
    fun bind(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(this)
        owner.lifecycleScope.launch {
            combine(preferences.hideTotalAmounts, preferences.revealOnProximity) { hide, reveal ->
                hide to reveal
            }.collect { (hide, reveal) ->
                hideTotals = hide
                revealOnProximity = reveal
                PrivacyGate.setHideTotals(hide)
                updateListening()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        inForeground = true
        updateListening()
    }

    override fun onStop(owner: LifecycleOwner) {
        inForeground = false
        updateListening()
    }

    private fun updateListening() {
        val manager = sensorManager
        val sensor = proximity
        val wanted = hideTotals && revealOnProximity && inForeground && manager != null && sensor != null

        if (wanted && !listening) {
            listening = manager!!.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (!wanted) {
            if (listening) {
                manager?.unregisterListener(listener)
                listening = false
            }
            // Whenever the sensor is not in charge, totals follow the setting alone.
            PrivacyGate.setRevealed(false)
        }
    }
}
