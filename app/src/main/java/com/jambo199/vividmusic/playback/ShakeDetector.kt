package com.jambo199.vividmusic.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Calls [onShake] when the phone is given a firm shake: two strong jolts close together, so
 * walking or putting the phone down doesn't count.
 */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val gesture = Gesture()
    private var listening = false

    fun start() {
        if (listening) return
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listening = sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!listening) return
        sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gesture.onForce(g, SystemClock.elapsedRealtime())) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** The shake rule on its own: [onForce] gets the force in g and returns true on a shake. */
    class Gesture(
        private val threshold: Float = 2.4f,
        private val windowMs: Long = 600,
        private val cooldownMs: Long = 1_500,
    ) {
        private var jolts = 0
        private var firstJoltAt = -1L
        private var lastShakeAt = -1L
        private var inJolt = false

        fun onForce(g: Float, now: Long): Boolean {
            if (g < threshold) {
                inJolt = false
                return false
            }
            // One jolt lasts several readings; count it once.
            if (inJolt) return false
            inJolt = true
            if (lastShakeAt >= 0 && now - lastShakeAt < cooldownMs) return false
            if (firstJoltAt < 0 || now - firstJoltAt > windowMs) {
                firstJoltAt = now
                jolts = 0
            }
            jolts++
            if (jolts < 2) return false
            jolts = 0
            firstJoltAt = -1
            lastShakeAt = now
            return true
        }
    }
}
