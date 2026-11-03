package com.example.spendflow.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class MonthBoundsTest {
    @Test
    fun `October 31 at 23 59 is in October and local midnight is in November`() {
        val zone = ZoneId.of("Asia/Colombo")
        val october = MonthBounds(YearMonth.of(2026, 10), zone)
        val november = MonthBounds(YearMonth.of(2026, 11), zone)
        val lastMinuteOfOctober = Instant.parse("2026-10-31T18:29:00Z")
        val firstInstantOfNovember = Instant.parse("2026-10-31T18:30:00Z")

        assertEquals(Instant.parse("2026-09-30T18:30:00Z"), october.startInclusive)
        assertEquals(firstInstantOfNovember, october.endExclusive)
        assertEquals(october.endExclusive, november.startInclusive)
        assertTrue(lastMinuteOfOctober in october)
        assertFalse(lastMinuteOfOctober in november)
        assertFalse(firstInstantOfNovember in october)
        assertTrue(firstInstantOfNovember in november)
    }

    @Test
    fun `December ends at the first instant of January in the next year`() {
        val december = MonthBounds(YearMonth.of(2026, 12), ZoneOffset.UTC)
        val january = MonthBounds(YearMonth.of(2027, 1), ZoneOffset.UTC)
        val newYear = Instant.parse("2027-01-01T00:00:00Z")

        assertEquals(newYear, december.endExclusive)
        assertEquals(newYear, january.startInclusive)
        assertTrue(newYear.minusNanos(1) in december)
        assertFalse(newYear in december)
        assertTrue(newYear in january)
    }

    @Test
    fun `leap February includes the entire 29th and ends on March 1`() {
        val february = MonthBounds(YearMonth.of(2024, 2), ZoneOffset.UTC)
        val marchStart = Instant.parse("2024-03-01T00:00:00Z")

        assertEquals(Instant.parse("2024-02-01T00:00:00Z"), february.startInclusive)
        assertEquals(marchStart, february.endExclusive)
        assertEquals(Duration.ofDays(29), Duration.between(february.startInclusive, february.endExclusive))
        assertTrue(marchStart.minusNanos(1) in february)
        assertFalse(marchStart in february)
    }

    @Test
    fun `spring forward shortens the month by an hour`() {
        val march = MonthBounds(YearMonth.of(2026, 3), ZoneId.of("America/New_York"))

        assertEquals(Instant.parse("2026-03-01T05:00:00Z"), march.startInclusive)
        assertEquals(Instant.parse("2026-04-01T04:00:00Z"), march.endExclusive)
        assertEquals(Duration.ofDays(31).minusHours(1), Duration.between(march.startInclusive, march.endExclusive))
    }

    @Test
    fun `fall back lengthens the month and includes both occurrences of the repeated hour`() {
        val november = MonthBounds(YearMonth.of(2026, 11), ZoneId.of("America/New_York"))

        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), november.startInclusive)
        assertEquals(Instant.parse("2026-12-01T05:00:00Z"), november.endExclusive)
        assertEquals(Duration.ofDays(30).plusHours(1), Duration.between(november.startInclusive, november.endExclusive))
        assertTrue(Instant.parse("2026-11-01T05:30:00Z") in november)
        assertTrue(Instant.parse("2026-11-01T06:30:00Z") in november)
    }
}
