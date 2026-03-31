package com.group10.smartstudytimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StatisticsDebugParserTest {

    @Test
    fun `parseRecords parses multiple daily records`() {
        val rawValue = """
            2026-03-07,50,1,5,1,90
            2026-03-08,60,0,0,1,100
        """.trimIndent()

        val result = StatisticsDebugParser.parseRecords(rawValue)

        assertEquals(2, result.size)
        assertEquals(
            DailyStatisticsRecord(
                date = "2026-03-07",
                studyMinutes = 50,
                interruptionCount = 1,
                interruptedMinutes = 5,
                completedSessions = 1,
                focusScore = 90
            ),
            result[0]
        )
        assertEquals("2026-03-08,60,0,0,1,100", StatisticsDebugParser.formatRecords(listOf(result[1])))
    }

    @Test
    fun `parseRecords rejects malformed lines`() {
        assertThrows(IllegalArgumentException::class.java) {
            StatisticsDebugParser.parseRecords("2026-03-07,50,1")
        }
    }
}
