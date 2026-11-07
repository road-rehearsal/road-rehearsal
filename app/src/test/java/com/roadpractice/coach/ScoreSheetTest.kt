package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreSheetTest {
    @Test
    fun passengerItemsMatchSchedule26() {
        assertEquals(25, PassengerItem.entries.size)
        // 차가 보는 20개(코스 지점 37·38·43, 11/7부터 5번 포함), 동승자 25개, 다루지 않는 17번 하나로 46개
        val all = ExamItem.entries.map { it.number } + PassengerItem.entries.map { it.number } + 17
        assertEquals((1..46).toList(), all.sorted())
    }

    @Test
    fun combinesCarAndPassenger() {
        val sheet = ScoreSheet.build(listOf(ExamItem.TURN_NO_SIGNAL), emptySet(), listOf(PassengerItem.LANE_KEEP), emptyList(), emptyList())
        assertEquals(7, sheet.carDeducted)
        assertEquals(5, sheet.passengerDeducted)
        assertEquals(88, sheet.score)
        assertTrue(sheet.passedLine)
    }

    @Test
    fun excludedCarLineDoesNotCount() {
        val car = listOf(ExamItem.HARD_BRAKE, ExamItem.HARD_BRAKE)
        val sheet = ScoreSheet.build(car, setOf(1), emptyList(), emptyList(), emptyList())
        assertEquals(7, sheet.carDeducted)
        assertEquals(93, sheet.score)
    }

    @Test
    fun disqualificationFailsPassLine() {
        val sheet = ScoreSheet.build(emptyList(), emptySet(), emptyList(), emptyList(), listOf(PassengerDisqualification.SIGNAL))
        assertEquals(100, sheet.score)
        assertFalse(sheet.passedLine)
    }

    @Test
    fun groupsRepeatedLines() {
        val sheet = ScoreSheet.build(List(3) { ExamItem.TURN_NO_SIGNAL }, setOf(0), emptyList(), emptyList(), emptyList())
        val groups = sheet.grouped()
        assertEquals(2, groups.size)
        val counted = groups.first { !it.first.excluded }
        assertEquals(2, counted.second)
        assertEquals(14, counted.third)
    }
}
