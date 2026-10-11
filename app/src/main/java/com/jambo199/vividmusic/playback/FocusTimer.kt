package com.jambo199.vividmusic.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A focus session (the Pomodoro way): music while you work, then it fades out for a short break
 * and comes back by itself, for a few rounds. The playback service runs the clock; an alarm
 * brings the music back after each break, even with the screen off.
 */
object FocusTimer {
    data class Plan(val focusMinutes: Int, val breakMinutes: Int, val rounds: Int)

    sealed interface Mode {
        data object Off : Mode

        /** Music until [endsAt] (wall-clock time), in round [round] of the plan. */
        data class Focus(val endsAt: Long, val round: Int, val plan: Plan, val startedAt: Long) : Mode

        /** Quiet until [endsAt], after round [round]. */
        data class Break(val endsAt: Long, val round: Int, val plan: Plan, val startedAt: Long) : Mode
    }

    val PLANS = listOf(Plan(25, 5, 4), Plan(50, 10, 2), Plan(15, 3, 4))

    private val _mode = MutableStateFlow<Mode>(Mode.Off)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    fun start(plan: Plan, now: Long = System.currentTimeMillis()) {
        _mode.value = Mode.Focus(now + plan.focusMinutes * MINUTE, 1, plan, now)
    }

    fun stop() {
        _mode.value = Mode.Off
    }

    /** Moves on from the current part (focus to break, break to the next round). */
    fun advance(now: Long = System.currentTimeMillis()): Mode = next(_mode.value, now).also { _mode.value = it }

    /** Back to work after the break that followed [round] (also when the app had been closed meanwhile). */
    fun resumeAfterBreak(plan: Plan, round: Int, now: Long = System.currentTimeMillis()): Mode =
        Mode.Focus(now + plan.focusMinutes * MINUTE, round + 1, plan, now).also { _mode.value = it }

    /** What follows [mode]: the last round's focus ends the session. */
    fun next(mode: Mode, now: Long): Mode = when (mode) {
        is Mode.Focus -> if (mode.round >= mode.plan.rounds) Mode.Off
        else Mode.Break(now + mode.plan.breakMinutes * MINUTE, mode.round, mode.plan, now)
        is Mode.Break -> Mode.Focus(now + mode.plan.focusMinutes * MINUTE, mode.round + 1, mode.plan, now)
        Mode.Off -> Mode.Off
    }

    private const val MINUTE = 60_000L
}
