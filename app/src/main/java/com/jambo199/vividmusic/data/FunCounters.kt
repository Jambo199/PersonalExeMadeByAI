package com.jambo199.vividmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How often you've used the fun features (party mode, the alarm, Discover…), for badges. */
class FunCounters(context: Context) {
    private val prefs = context.getSharedPreferences("fun", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Map<String, Int>> = _state.asStateFlow()

    @Synchronized
    fun add(key: String, n: Int = 1) {
        val next = (_state.value[key] ?: 0) + n
        prefs.edit().putInt(key, next).apply()
        _state.value = _state.value + (key to next)
    }

    private fun read(): Map<String, Int> = prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()

    companion object {
        const val PARTY = "party"
        const val ALARM = "alarm"
        const val DISCOVER = "discover"
        const val FOCUS = "focus"
    }
}
