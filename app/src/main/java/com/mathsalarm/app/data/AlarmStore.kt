package com.mathsalarm.app.data

import android.content.Context
import android.content.SharedPreferences
import com.mathsalarm.app.model.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** Persists alarms as JSON in SharedPreferences and exposes them as a flow for the UI. */
object AlarmStore {
    private const val PREFS = "alarms"
    private const val KEY_ALARMS = "alarms_json"
    private const val KEY_NEXT_ID = "next_id"

    private lateinit var prefs: SharedPreferences
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _alarms.value = load()
    }

    private fun load(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        prefs.edit().putString(KEY_ALARMS, JSONArray(sorted.map { it.toJson() }).toString()).apply()
        _alarms.value = sorted
    }

    fun all(): List<Alarm> = _alarms.value

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(alarm: Alarm) {
        persist(_alarms.value.filterNot { it.id == alarm.id } + alarm)
    }

    @Synchronized
    fun delete(id: Int) {
        persist(_alarms.value.filterNot { it.id == id })
        prefs.edit().remove(snoozeKey(id)).apply()
    }

    @Synchronized
    fun newId(): Int {
        val existingMax = _alarms.value.maxOfOrNull { it.id } ?: 0
        val id = maxOf(prefs.getInt(KEY_NEXT_ID, 1), existingMax + 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    private fun snoozeKey(id: Int) = "snooze_count_$id"

    fun snoozeCount(id: Int): Int = prefs.getInt(snoozeKey(id), 0)

    fun setSnoozeCount(id: Int, count: Int) {
        prefs.edit().putInt(snoozeKey(id), count).apply()
    }
}
