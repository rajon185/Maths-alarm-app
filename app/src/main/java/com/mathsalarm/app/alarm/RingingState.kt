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

    internal fun setRinging(id: Int?) { _ringingAlarmId.value = id }
    internal fun setSilencedUntil(t: Long) { _silencedUntil.value = t }
}
