package com.iptv.app

import org.junit.Assert.*
import org.junit.Test

class GuideLayoutTest {
    @Test fun nowBoundaryMovesToNextProgramme() {
        val programmes = listOf(Programme("First", 0, 1800), Programme("Next", 1800, 3600))
        assertEquals("First", GuideLayout.current(programmes, 1799)?.title)
        assertEquals("Next", GuideLayout.current(programmes, 1800)?.title)
        assertNull(GuideLayout.current(programmes, 3600))
    }
    @Test fun progressClampsAndRejectsInvalidIntervals() {
        val programme = Programme("News", 100, 200)
        assertEquals(0f, GuideLayout.progress(programme, 0))
        assertEquals(.5f, GuideLayout.progress(programme, 150))
        assertEquals(1f, GuideLayout.progress(programme, 400))
        assertEquals(0f, GuideLayout.progress(programme.copy(end = 100), 150))
    }
    @Test fun windowAlignsToHalfHoursAndKeepsGapsProportional() {
        val window = GuideLayout.window(listOf(Programme("A", 3600, 5400), Programme("B", 7200, 10800)), 4000)
        assertEquals(3600L, window.start)
        assertEquals(10800L, window.end)
        assertEquals(.25f, window.fraction(5400))
        assertEquals(.5f, window.fraction(7200))
        assertEquals(0f, window.fraction(0))
        assertEquals(1f, window.fraction(20000))
    }
    @Test fun malformedAndEmptySchedulesHaveBoundedGeometry() {
        val window = GuideLayout.window(listOf(Programme("Corrupt", 10, Long.MAX_VALUE), Programme("Reversed", 200, 100)), 100)
        assertEquals(86400L, window.duration)
        val empty = GuideLayout.window(emptyList(), 4000)
        assertEquals(3600L, empty.start)
        assertEquals(7200L, empty.duration)
    }
    @Test fun epochGeometryRemainsStableAcrossDaylightSavingTransitions() {
        // Two adjacent one-hour programmes span the repeated local hour during a DST change.
        val first = Programme("Before clock change", 1793511000, 1793514600)
        val second = Programme("After clock change", first.end, first.end + 3600)
        assertEquals(second, GuideLayout.current(listOf(first, second), first.end))
        assertEquals(.5f, GuideLayout.progress(second, second.start + 1800))
    }
}
