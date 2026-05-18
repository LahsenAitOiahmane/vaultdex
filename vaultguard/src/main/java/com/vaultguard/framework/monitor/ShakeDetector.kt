package com.vaultguard.framework.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Shake-to-Launch detector using the device accelerometer.
 *
 * Monitors acceleration events and detects a "shake" gesture when the device
 * experiences significant acceleration across multiple events within a short window.
 *
 * Detection algorithm:
 *  1. Calculate acceleration magnitude: sqrt(x² + y² + z²) - gravity
 *  2. If magnitude exceeds SHAKE_THRESHOLD, record a "shake event"
 *  3. If SHAKE_COUNT_THRESHOLD events occur within SHAKE_TIME_WINDOW_MS,
 *     trigger the onShake callback
 *  4. Cooldown period prevents repeated triggers
 */
class ShakeDetector(context: Context) : SensorEventListener {

    companion object {
        private const val SHAKE_THRESHOLD = 12.0f      // m/s² above gravity
        private const val SHAKE_COUNT_THRESHOLD = 3     // events needed
        private const val SHAKE_TIME_WINDOW_MS = 500L   // within this window
        private const val COOLDOWN_MS = 2000L           // min time between triggers
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onShakeCallback: (() -> Unit)? = null
    private var isListening = false

    private var shakeCount = 0
    private var firstShakeTime = 0L
    private var lastTriggerTime = 0L

    /**
     * Starts listening for shake gestures.
     *
     * @param onShake Callback invoked when a shake gesture is detected
     */
    fun startListening(onShake: () -> Unit) {
        if (isListening || accelerometer == null) return
        onShakeCallback = onShake
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        isListening = true
    }

    /**
     * Stops the shake detector and unregisters the sensor listener.
     */
    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        onShakeCallback = null
        shakeCount = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate acceleration magnitude minus gravity (~9.81 m/s²)
        val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (magnitude > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()

            // Cooldown check
            if (now - lastTriggerTime < COOLDOWN_MS) return

            if (shakeCount == 0) {
                firstShakeTime = now
            }

            shakeCount++

            // Check if we have enough shakes within the time window
            if (shakeCount >= SHAKE_COUNT_THRESHOLD) {
                if (now - firstShakeTime <= SHAKE_TIME_WINDOW_MS) {
                    // Shake detected!
                    lastTriggerTime = now
                    shakeCount = 0
                    onShakeCallback?.invoke()
                } else {
                    // Events spread too far apart — reset
                    shakeCount = 1
                    firstShakeTime = now
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for shake detection
    }
}
