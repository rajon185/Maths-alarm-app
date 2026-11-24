package com.mathsalarm.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

enum class ProblemType(val label: String, val description: String) {
    ARITHMETIC("Arithmetic", "+  −  ×  ÷"),
    ALGEBRA("Equations", "Solve for x"),
    SEQUENCE("Sequences", "Find the next number"),
    MEMORY("Memory", "Memorise a number"),
    TYPING("Typing", "Retype random text"),
}

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
}

/**
 * A single alarm and all of its settings.
 *
 * [days] holds [DayOfWeek] values (1 = Monday … 7 = Sunday). An empty set means
 * the alarm rings once at the next occurrence of its time and then turns itself off.
 */
data class Alarm(
    val id: Int,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val problemTypes: Set<ProblemType> = setOf(ProblemType.ARITHMETIC),
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val problemCount: Int = 3,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    /** 0 means unlimited snoozes. */
    val maxSnoozes: Int = 3,
    /** How long the alarm goes quiet while you solve, in seconds. */
    val silenceSeconds: Int = 30,
    val vibrate: Boolean = true,
    /** Turn the phone's alarm volume to maximum while ringing (and restore it afterwards). */
    val raiseVolume: Boolean = true,
    /** null = system default alarm sound. */
    val ringtoneUri: String? = null,
    /**
     * Epoch millis of one upcoming occurrence the user chose to skip, or null. Only that exact
     * occurrence is skipped; later ones ring as normal.
     */
    val skippedTrigger: Long? = null,
) {
    val isRepeating: Boolean get() = days.isNotEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("days", JSONArray(days.sorted()))
        put("enabled", enabled)
        put("problemTypes", JSONArray(problemTypes.map { it.name }))
        put("difficulty", difficulty.name)
        put("problemCount", problemCount)
        put("snoozeEnabled", snoozeEnabled)
        put("snoozeMinutes", snoozeMinutes)
        put("maxSnoozes", maxSnoozes)
        put("silenceSeconds", silenceSeconds)
        put("vibrate", vibrate)
        put("raiseVolume", raiseVolume)
        if (ringtoneUri != null) put("ringtoneUri", ringtoneUri)
        if (skippedTrigger != null) put("skippedTrigger", skippedTrigger)
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val days = o.optJSONArray("days")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() } ?: emptySet()
            val types = o.optJSONArray("problemTypes")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> ProblemType.entries.firstOrNull { it.name == arr.getString(i) } }.toSet()
            }.orEmpty().ifEmpty { setOf(ProblemType.ARITHMETIC) }
            return Alarm(
                id = o.getInt("id"),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                label = o.optString("label", ""),
                days = days,
                enabled = o.optBoolean("enabled", true),
                problemTypes = types,
                difficulty = Difficulty.entries.firstOrNull { it.name == o.optString("difficulty") } ?: Difficulty.MEDIUM,
                problemCount = o.optInt("problemCount", 3),
                snoozeEnabled = o.optBoolean("snoozeEnabled", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                maxSnoozes = o.optInt("maxSnoozes", 3),
                silenceSeconds = o.optInt("silenceSeconds", 30),
                vibrate = o.optBoolean("vibrate", true),
                raiseVolume = o.optBoolean("raiseVolume", true),
                ringtoneUri = if (o.has("ringtoneUri")) o.getString("ringtoneUri") else null,
                skippedTrigger = if (o.has("skippedTrigger")) o.getLong("skippedTrigger") else null,
            )
        }
    }
}

fun Set<Int>.daysSummary(): String {
    if (isEmpty()) return "Once"
    if (size == 7) return "Every day"
    if (this == setOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (this == setOf(6, 7)) return "Weekends"
    return sorted().joinToString(" ") { DayOfWeek.of(it).shortName() }
}

fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
