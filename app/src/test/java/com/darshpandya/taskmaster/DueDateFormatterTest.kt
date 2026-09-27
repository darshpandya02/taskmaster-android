package com.darshpandya.taskmaster

import com.darshpandya.taskmaster.ui.DueDateFormatter
import com.darshpandya.taskmaster.ui.Event
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DueDateFormatterTest {
    private val zone = ZoneId.of("America/New_York")
    private val fmt = DueDateFormatter(zone, Locale.US, use24Hour = true)
    private val now = at(2026, 9, 26, 10, 0)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test fun relativeDays() {
        assertEquals("Today 14:05", fmt.format(at(2026, 9, 26, 14, 5), now))
        assertEquals("Tomorrow 09:00", fmt.format(at(2026, 9, 27, 9, 0), now))
        assertEquals("Yesterday 23:59", fmt.format(at(2026, 9, 25, 23, 59), now))
    }

    @Test fun laterThisYearAndOtherYears() {
        assertEquals("Wed, Sep 30 08:30", fmt.format(at(2026, 9, 30, 8, 30), now))
        assertEquals("Jan 4, 2027 12:00", fmt.format(at(2027, 1, 4, 12, 0), now))
    }

    @Test fun twelveHourClock() {
        val f12 = DueDateFormatter(zone, Locale.US, use24Hour = false)
        assertEquals("Today 2:05 PM", f12.format(at(2026, 9, 26, 14, 5), now))
    }

    @Test fun eventIsConsumedOnce() {
        val e = Event("hello")
        assertEquals("hello", e.consume())
        assertNull(e.consume())
        assertEquals("hello", e.peek())
    }
}
