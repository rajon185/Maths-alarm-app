package com.mathsalarm.app

import com.mathsalarm.app.alarm.nextTrigger
import com.mathsalarm.app.alarm.skipNext
import com.mathsalarm.app.alarm.skippedOccurrence
import com.mathsalarm.app.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerTest {
    private val zone = ZoneId.of("Europe/London")
    // Wednesday 18 Nov 2026, 08:00
    private val now = ZonedDateTime.of(2026, 11, 18, 8, 0, 0, 0, zone)

    @Test
    fun oneTimeLaterToday() {
        val t = nextTrigger(Alarm(id = 1, hour = 9, minute = 30), now)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 9, 30, 0, 0, zone), t)
    }

    @Test
    fun oneTimeAlreadyPassedGoesToTomorrow() {
        val t = nextTrigger(Alarm(id = 1, hour = 8, minute = 0), now)
        assertEquals(ZonedDateTime.of(2026, 11, 19, 8, 0, 0, 0, zone), t)
    }

    @Test
    fun repeatingPicksNextSelectedDay() {
        // Mondays only -> next Monday 23 Nov
        val t = nextTrigger(Alarm(id = 1, hour = 7, minute = 0, days = setOf(DayOfWeek.MONDAY.value)), now)
        assertEquals(ZonedDateTime.of(2026, 11, 23, 7, 0, 0, 0, zone), t)
    }

    @Test
    fun repeatingTodayIfStillAhead() {
        val t = nextTrigger(Alarm(id = 1, hour = 22, minute = 15, days = setOf(DayOfWeek.WEDNESDAY.value)), now)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 22, 15, 0, 0, zone), t)
    }

    @Test
    fun repeatingSameDayPassedGoesToNextWeek() {
        val t = nextTrigger(Alarm(id = 1, hour = 6, minute = 0, days = setOf(DayOfWeek.WEDNESDAY.value)), now)
        assertEquals(ZonedDateTime.of(2026, 11, 25, 6, 0, 0, 0, zone), t)
    }

    @Test
    fun skipNextMovesRepeatingAlarmToFollowingOccurrence() {
        // Weekdays at 07:00; next would be Thu 19 Nov, skipped -> Fri 20 Nov.
        val alarm = skipNext(Alarm(id = 1, hour = 7, minute = 0, days = setOf(1, 2, 3, 4, 5)), now)
        assertEquals(ZonedDateTime.of(2026, 11, 19, 7, 0, 0, 0, zone), skippedOccurrence(alarm, now))
        assertEquals(ZonedDateTime.of(2026, 11, 20, 7, 0, 0, 0, zone), nextTrigger(alarm, now))
    }

    @Test
    fun skipOnlyAppliesOnce() {
        val alarm = skipNext(Alarm(id = 1, hour = 7, minute = 0, days = setOf(1, 2, 3, 4, 5)), now)
        // After the skipped Thursday has passed, the skip is no longer in effect.
        val friday = ZonedDateTime.of(2026, 11, 20, 6, 0, 0, 0, zone)
        assertNull(skippedOccurrence(alarm, friday))
        assertEquals(ZonedDateTime.of(2026, 11, 20, 7, 0, 0, 0, zone), nextTrigger(alarm, friday))
    }

    @Test
    fun skipNextTurnsOffOneTimeAlarm() {
        val alarm = skipNext(Alarm(id = 1, hour = 9, minute = 0), now)
        assertFalse(alarm.enabled)
    }
}
