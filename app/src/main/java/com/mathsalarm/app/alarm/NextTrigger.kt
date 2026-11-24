package com.mathsalarm.app.alarm

import com.mathsalarm.app.model.Alarm
import java.time.ZonedDateTime

/**
 * Pure calculation of the next time [alarm] should ring after [now], honouring a skipped
 * occurrence ([Alarm.skippedTrigger]).
 */
fun nextTrigger(alarm: Alarm, now: ZonedDateTime): ZonedDateTime {
    val next = nextOccurrence(alarm, now)
    return if (alarm.skippedTrigger == next.toInstant().toEpochMilli()) nextOccurrence(alarm, next) else next
}

/** The occurrence that is currently being skipped, or null if none (or it has already passed). */
fun skippedOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
    val skipped = alarm.skippedTrigger ?: return null
    val next = nextOccurrence(alarm, now)
    return if (next.toInstant().toEpochMilli() == skipped) next else null
}

/**
 * Returns [alarm] with its next occurrence skipped. A one-time alarm has nothing after its next
 * occurrence, so skipping it simply turns it off.
 */
fun skipNext(alarm: Alarm, now: ZonedDateTime): Alarm =
    if (alarm.isRepeating) {
        alarm.copy(skippedTrigger = nextOccurrence(alarm, now).toInstant().toEpochMilli())
    } else {
        alarm.copy(enabled = false, skippedTrigger = null)
    }

/** The next time [alarm]'s schedule matches after [now], ignoring any skip. */
fun nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime {
    var candidate = now.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)
    if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
    if (alarm.days.isEmpty()) return candidate
    repeat(7) {
        if (candidate.dayOfWeek.value in alarm.days) return candidate
        candidate = candidate.plusDays(1)
    }
    return candidate
}

/** "Rings in 7 h 5 min" style text. */
fun timeUntilText(now: ZonedDateTime, trigger: ZonedDateTime): String {
    val totalMinutes = ((java.time.Duration.between(now, trigger).seconds + 59) / 60).coerceAtLeast(0)
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (days > 0) add("$days d")
        if (hours > 0) add("$hours h")
        if (minutes > 0 || (days == 0L && hours == 0L)) add("$minutes min")
    }
    return "Rings in " + parts.joinToString(" ")
}
