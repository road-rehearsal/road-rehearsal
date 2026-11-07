package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5번: 출발 지시(P에서 뺌) 뒤 20초 안에 출발하지 않음. 처음 출발만 봅니다. */
class NoStartTest {
    private fun stateAt(sec: Int, kmh: Float, gear: Gear = Gear.DRIVE) = VehicleState(timeMs = sec * 1000L, speedKmh = kmh, gear = gear)

    private fun items(rules: ExamRules, states: List<VehicleState>) =
        states.flatMap { rules.onState(it) }.filterIsInstance<ExamEvent.Deduction>().map { it.item }

    @Test
    fun waitingOver20sAfterStartIsDeductedOnce() {
        val s = listOf(stateAt(0, 0f, Gear.PARK)) + (1..30).map { stateAt(it, 0f) } + (31..40).map { stateAt(it, 20f) }
        assertEquals(1, items(ExamRules(), s).count { it == ExamItem.NO_START })
    }

    @Test
    fun startingWithin20sIsFine() {
        val s = listOf(stateAt(0, 0f, Gear.PARK)) + (1..15).map { stateAt(it, 0f) } + (16..40).map { stateAt(it, 20f) }
        assertTrue(ExamItem.NO_START !in items(ExamRules(), s))
    }

    @Test
    fun laterLongStopIsNotNoStart() {
        // 한 번 출발한 뒤 신호 대기 40초는 5번이 아님(4번 판정 대상)
        val s = listOf(stateAt(0, 0f, Gear.PARK)) + (1..10).map { stateAt(it, 20f) } + (11..60).map { stateAt(it, 0f) }
        assertTrue(ExamItem.NO_START !in items(ExamRules(), s))
    }
}
