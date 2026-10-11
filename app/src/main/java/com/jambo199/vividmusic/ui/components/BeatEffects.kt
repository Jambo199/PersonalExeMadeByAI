package com.jambo199.vividmusic.ui.components

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/** The phone's flashlight, flashed briefly. Null if there's no flash. */
private class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val id: String? = try {
        cameras?.cameraIdList?.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    } catch (_: Exception) {
        null
    }
    val available: Boolean get() = id != null

    fun set(on: Boolean) {
        val camera = id ?: return
        try {
            cameras?.setTorchMode(camera, on)
        } catch (_: Exception) {
            // The camera is busy (another app) or the flash went away: just skip the flash.
        }
    }
}

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

/** Whether this phone has a flashlight the app can use. */
@Composable
fun rememberHasFlashlight(): Boolean {
    val context = LocalContext.current
    return remember { Torch(context).available }
}

/**
 * Flashes the flashlight and/or buzzes the phone on each beat that [spectrum] hears, while
 * [flash] or [buzz] is on. The flashlight is always left off afterwards.
 */
@Composable
fun BeatEffects(spectrum: SpectrumState, flash: Boolean, buzz: Boolean) {
    val context = LocalContext.current
    val torch = remember { Torch(context) }
    val vibrator = remember { vibrator(context) }
    DisposableEffect(flash) {
        onDispose { torch.set(false) }
    }
    LaunchedEffect(flash, buzz) {
        if (!flash && !buzz) return@LaunchedEffect
        coroutineScope {
            var last = 0f
            var lastHit = 0L
            while (isActive) {
                val now = withFrameNanos { it }
                val beat = spectrum.beat
                // A new beat, and not more than about six a second.
                if (beat > 0.55f && last < 0.35f && now - lastHit > 160_000_000L) {
                    lastHit = now
                    if (buzz && vibrator?.hasVibrator() == true) {
                        val strength = (60 + beat * 195).toInt().coerceIn(1, 255)
                        try {
                            vibrator.vibrate(VibrationEffect.createOneShot(35, if (vibrator.hasAmplitudeControl()) strength else VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (_: Exception) {
                        }
                    }
                    if (flash) {
                        launch {
                            torch.set(true)
                            delay(70)
                            torch.set(false)
                        }
                    }
                }
                last = beat
            }
        }
    }
}
