package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachSessionTest {
    private fun drive(session: CoachSession): VehicleState {
        var s = VehicleState(timeMs = 0, speedKmh = 0f, gear = Gear.PARK, turnSignal = TurnSignal.OFF, steeringAbsDeg = 0f, parkingBrakeOn = false, seatBeltWarningOn = false)
        session.onState(s)
        s = s.copy(gear = Gear.DRIVE)
        session.onState(s)
        s = s.copy(timeMs = 5_000, speedKmh = 3f)
        session.onState(s)
        return s
    }

    @Test
    fun practiceSpeaksTheSameEventThatExamKeepsSilent() {
        val spoken = ArrayList<String>()
        val practice = CoachSession(CoachMode.PRACTICE, say = { spoken += it })
        val exam = CoachSession(CoachMode.EXAM, say = { spoken += "EXAM:$it" })
        val a = drive(practice).copy(openDoors = setOf("ROW_2_LEFT"))
        val b = drive(exam).copy(openDoors = setOf("ROW_2_LEFT"))
        practice.onState(a)
        exam.onState(b)
        assertEquals(listOf(ExamItem.DOOR.sentence), spoken)
        assertEquals(practice.result, exam.result)
        assertEquals(listOf(ExamItem.DOOR), exam.result.deductions)
    }

    @Test
    fun practiceDoesNotRepeatSentenceWithinGapButStillRecords() {
        val spoken = ArrayList<String>()
        val session = CoachSession(CoachMode.PRACTICE, say = { spoken += it })
        var s = drive(session)
        s = s.copy(timeMs = 6_000, openDoors = setOf("ROW_2_LEFT")); session.onState(s)
        s = s.copy(timeMs = 7_000, openDoors = emptySet()); session.onState(s)
        s = s.copy(timeMs = 20_000, openDoors = setOf("ROW_2_LEFT")); session.onState(s)
        assertEquals(1, spoken.size)
        assertEquals(2, session.result.deductions.size)
        s = s.copy(timeMs = 21_000, openDoors = emptySet()); session.onState(s)
        s = s.copy(timeMs = 70_000, openDoors = setOf("ROW_2_LEFT")); session.onState(s)
        assertEquals(2, spoken.size)
    }

    @Test
    fun stopsScoringAfterEnd() {
        val session = CoachSession(CoachMode.EXAM, say = {})
        var s = drive(session)
        s = s.copy(timeMs = 6_000, speedKmh = 0f); session.onState(s)
        s = s.copy(timeMs = 7_000, openDoors = setOf(ExamRules.DRIVER_DOOR)); session.onState(s)
        assertTrue(session.ended)
        val count = session.result.deductions.size
        s = s.copy(timeMs = 8_000, openDoors = emptySet(), gear = Gear.PARK); session.onState(s)
        s = s.copy(timeMs = 9_000, gear = Gear.DRIVE); session.onState(s)
        s = s.copy(timeMs = 10_000, speedKmh = 2f, parkingBrakeOn = true); session.onState(s)
        assertEquals(count, session.result.deductions.size)
    }
}
