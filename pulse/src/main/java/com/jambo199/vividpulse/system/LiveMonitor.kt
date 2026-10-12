package com.jambo199.vividpulse.system

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Everything the live pages show, with recent history for the charts. */
data class LiveState(
    /** SystemClock.uptimeMillis() when this was read; charts glide between readings with it. */
    val sampledAt: Long,
    val intervalMs: Long,
    val cpu: CpuSample,
    val cpuHistory: List<Float>,
    val coreHistory: List<List<Float>>,
    val memory: MemorySample,
    val memoryHistory: List<Float>,
    val gpu: GpuSample,
    val gpuHistory: List<Float>,
    val battery: BatterySample,
    /** Watts in (+) or out (−) of the battery. */
    val powerHistory: List<Float>,
    val batteryTempHistory: List<Float>,
    val thermal: ThermalSample,
    val headroomHistory: List<Float>,
    val net: NetSample,
    val rxHistory: List<Float>,
    val txHistory: List<Float>,
    val storage: StorageSample,
)

/**
 * Reads the phone's vitals every [intervalMs] while [run] is collecting (the app is on screen),
 * keeping the last [HISTORY] readings of each for the charts.
 */
class LiveMonitor(context: Context) {
    private val app = context.applicationContext
    val cpu by lazy { CpuReader() }
    val gpu by lazy { GpuReader() }
    val thermal by lazy { ThermalReader(app) }
    private val memory by lazy { MemoryReader(app) }
    val battery by lazy { BatteryReader(app) }
    private val network by lazy { NetworkReader(app) }
    private val storage by lazy { StorageReader(app) }

    private val _state = MutableStateFlow<LiveState?>(null)
    val state: StateFlow<LiveState?> = _state.asStateFlow()

    suspend fun run(intervalMs: () -> Long) = withContext(Dispatchers.IO) {
        var tick = 0L
        var storageSample = _state.value?.storage ?: StorageSample.EMPTY
        while (isActive) {
            val started = SystemClock.uptimeMillis()
            if (tick % 15 == 0L) storageSample = storage.sample()
            _state.value = next(_state.value, intervalMs(), storageSample)
            tick++
            delay((intervalMs() - (SystemClock.uptimeMillis() - started)).coerceAtLeast(50))
        }
    }

    private fun next(prev: LiveState?, interval: Long, storageSample: StorageSample): LiveState {
        val c = cpu.sample()
        val m = memory.sample()
        val g = gpu.sample()
        val b = battery.sample()
        val t = thermal.sample()
        val n = network.sample()
        val cores = c.coreLoad.indices.map { i ->
            (prev?.coreHistory?.getOrNull(i) ?: emptyList()).pushed(c.coreLoad[i] ?: 0f)
        }
        return LiveState(
            sampledAt = SystemClock.uptimeMillis(),
            intervalMs = interval,
            cpu = c,
            cpuHistory = prev?.cpuHistory.orEmpty().pushed(c.load ?: 0f),
            coreHistory = cores,
            memory = m,
            memoryHistory = prev?.memoryHistory.orEmpty().pushed(m.usedFraction),
            gpu = g,
            gpuHistory = prev?.gpuHistory.orEmpty().pushed(g.load ?: 0f),
            battery = b,
            powerHistory = prev?.powerHistory.orEmpty().pushed(b.watts ?: 0f),
            batteryTempHistory = prev?.batteryTempHistory.orEmpty().pushed(b.temperatureC ?: 0f),
            thermal = t,
            headroomHistory = prev?.headroomHistory.orEmpty().pushed(t.headroom ?: 0f),
            net = n,
            rxHistory = prev?.rxHistory.orEmpty().pushed(n.rxPerSec.toFloat()),
            txHistory = prev?.txHistory.orEmpty().pushed(n.txPerSec.toFloat()),
            storage = storageSample,
        )
    }

    companion object {
        const val HISTORY = 90

        fun List<Float>.pushed(value: Float): List<Float> =
            (if (size >= HISTORY) subList(size - HISTORY + 1, size) else this) + value
    }
}
