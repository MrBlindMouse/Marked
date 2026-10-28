package com.bmdstudios.marked.model

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampFormatTest {

    @Test
    fun HHMMSS_zeroPadsHoursMinutesAndSeconds() {
        assertEquals("09:05:07", TimestampFormat.HH_MM_SS.format(LocalTime.of(9, 5, 7)))
    }

    @Test
    fun HHMMSS_handlesLateNightAndEarlyMorning() {
        assertEquals("23:59:59", TimestampFormat.HH_MM_SS.format(LocalTime.of(23, 59, 59)))
        assertEquals("00:00:00", TimestampFormat.HH_MM_SS.format(LocalTime.MIDNIGHT))
    }

    @Test
    fun HHMM_formatsHoursAndMinutesOnly() {
        assertEquals("14:32", TimestampFormat.HH_MM.format(LocalTime.of(14, 32, 11)))
    }

    @Test
    fun MM_SS_formatsMinutesAndSecondsOnly() {
        assertEquals("42:08", TimestampFormat.MM_SS.format(LocalTime.of(3, 42, 8)))
    }

    @Test
    fun allFormats_produceExpectedSegmentCount() {
        assertEquals(3, TimestampFormat.HH_MM_SS.format(LocalTime.of(1, 2, 3)).split(":").size)
        assertEquals(2, TimestampFormat.HH_MM.format(LocalTime.of(1, 2, 3)).split(":").size)
        assertEquals(2, TimestampFormat.MM_SS.format(LocalTime.of(1, 2, 3)).split(":").size)
    }
}
