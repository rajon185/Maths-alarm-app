package com.mathsalarm.app.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-process state shared between the ringing service and the ringing screen. */
object RingingState {
    private val _ringingAlarmId = MutableStateFlow<Int?>(null)
    val ringingAlarmId: StateFlow<Int?> = _ringingAlarmId

    /** Epoch millis until which the sound is paused; 0 when ringing loudly. */
    private val _silencedUntil = MutableStateFlow(0L)
    val silencedUntil: StateFlow<Long> = _silencedUntil

    /** True when the ringing alarm was started with "Save & test" rather than by the schedule. */
    private val _isTest = MutableStateFlow(false)
    val isTest: StateFlow<Boolean> = _isTest

    internal fun setRinging(id: Int?, test: Boolean = false) {
        _ringingAlarmId.value = id
        _isTest.value = id != null && test
    }
    internal fun setSilencedUntil(t: Long) { _silencedUntil.value = t }
}
