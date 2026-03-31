package com.group10.smartstudytimer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class StatisticsAggregatorTest {

    @Test
    fun `buildStatisticsSnapshot aggregates today week and month data`() {
        val today = LocalDate.of(2026, 3, 30)
        val month = YearMonth.of(2026, 3)
        val zoneId = ZoneId.systemDefault()

        val sessions = listOf(
            StudySessionRecord(
                endedAtEpochMillis = today.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli(),
                studyMinutes = 60,
                interruptionCount = 1,
                interruptedMinutes = 5,
                completedSessions = 1,
                status = StudySessionStatus.COMPLETED
            ),
            StudySessionRecord(
                endedAtEpochMillis = today.minusDays(1).atTime(11, 0).atZone(zoneId).toInstant().toEpochMilli(),
                studyMinutes = 45,
                interruptionCount = 0,
                interruptedMinutes = 0,
                completedSessions = 1,
                status = StudySessionStatus.COMPLETED
            ),
            StudySessionRecord(
                endedAtEpochMillis = today.atTime(14, 0).atZone(zoneId).toInstant().toEpochMilli(),
                studyMinutes = 15,
                interruptionCount = 2,
                interruptedMinutes = 10,
                completedSessions = 0,
                status = StudySessionStatus.FAILED
            )
        )

        val snapshot = StatisticsAggregator.buildStatisticsSnapshot(
            sessions = sessions,
            snapshotDate = today,
            month = month
        )

        assertEquals("2026-03-30", snapshot.snapshotDate)
        assertEquals(75, snapshot.todayStudyMinutes)
        assertEquals(3, snapshot.todayInterruptionCount)
        assertEquals(15, snapshot.todayInterruptedMinutes)
        assertEquals(2, snapshot.totalCompletedSessions)
        assertEquals(2, snapshot.thisWeekCompletedSessions)
        assertEquals(2, snapshot.records.size)
    }

    @Test
    fun `buildDailyStatistics returns zero record when no sessions exist`() {
        val record = StatisticsAggregator.buildDailyStatistics(
            sessions = emptyList(),
            date = LocalDate.of(2026, 3, 1)
        )

        assertEquals("2026-03-01", record.date)
        assertEquals(0, record.studyMinutes)
        assertEquals(0, record.interruptionCount)
        assertEquals(0, record.interruptedMinutes)
        assertEquals(0, record.completedSessions)
        assertEquals(0, record.focusScore)
    }
}
