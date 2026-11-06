package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamRulesTest {
    private val rules = ExamRules()
    private val items = ArrayList<ExamItem>()
    private val others = ArrayList<ExamEvent>()
    private var s = VehicleState(
        timeMs = 0,
        speedKmh = 0f,
        gear = Gear.PARK,
        turnSignal = TurnSignal.OFF,
        steeringAbsDeg = 0f,
        braking = true,
        parkingBrakeOn = false,
        seatBeltWarningOn = false,
    )

    private fun put(state: VehicleState) {
        s = state
        rules.onState(s).forEach { if (it is ExamEvent.Deduction) items += it.item else others += it }
    }

    private fun change(f: (VehicleState) -> VehicleState) = put(f(s))

    /** 시간을 흘립니다. 속도를 그대로 두고 거리를 적분합니다. */
    private fun run(ms: Long, stepMs: Long = 100) {
        var left = ms
        while (left > 0) {
            val d = SignalHub.distanceMeters(s.speedKmh ?: 0f, s.speedKmh ?: 0f, stepMs)
            put(s.copy(timeMs = s.timeMs + stepMs, distanceM = s.distanceM + d))
            left -= stepMs
        }
    }

    private fun startDriving(kmh: Float = 20f) {
        put(s)
        change { it.copy(gear = Gear.DRIVE, braking = false) }
        // 느리게 가속해 급출발에 걸리지 않게 합니다.
        var v = 0f
        while (v < kmh) {
            v += 1f
            change { it.copy(timeMs = it.timeMs + 500, speedKmh = v) }
        }
    }

    /** 출발 단계를 끝내고 일반 주행으로 넘깁니다. */
    private fun passEdge() {
        change { it.copy(turnSignal = TurnSignal.LEFT) }
        run(500)
        change { it.copy(steeringAbsDeg = 60f) }
        run(1000)
        change { it.copy(steeringAbsDeg = 0f) }
        run(100)
        change { it.copy(turnSignal = TurnSignal.OFF) }
        run(3000)
        items.clear()
    }

    private fun stop() {
        var v = s.speedKmh ?: 0f
        while (v > 0f) {
            v = (v - 1f).coerceAtLeast(0f)
            change { it.copy(timeMs = it.timeMs + 500, speedKmh = v) }
        }
    }

    @Test
    fun nothingBeforeLeavingPark() {
        put(s)
        change { it.copy(parkingBrakeOn = true, openDoors = setOf("ROW_1_RIGHT")) }
        run(20_000)
        assertTrue(items.isEmpty())
        assertFalse(rules.isActive)
    }

    @Test
    fun startsWhenLeavingPark() {
        put(s)
        change { it.copy(gear = Gear.DRIVE) }
        assertTrue(rules.isActive)
        assertTrue(others.first() is ExamEvent.Started)
    }

    @Test
    fun cleanDriveScoresNothing() {
        startDriving()
        passEdge()
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(10_000)
        change { it.copy(steeringAbsDeg = 60f) }
        run(1000)
        change { it.copy(steeringAbsDeg = 0f) }
        run(100)
        change { it.copy(turnSignal = TurnSignal.OFF) }
        run(5000)
        stop()
        change { it.copy(braking = true) }
        run(2000)
        change { it.copy(gear = Gear.PARK, parkingBrakeOn = true) }
        change { it.copy(openDoors = setOf(ExamRules.DRIVER_DOOR)) }
        assertTrue(items.toString(), items.isEmpty())
        assertTrue(others.last() is ExamEvent.Ended)
    }

    @Test
    fun doorOpenWhileMoving() {
        startDriving()
        change { it.copy(openDoors = setOf("ROW_2_LEFT")) }
        assertEquals(listOf(ExamItem.DOOR), items)
    }

    @Test
    fun doorOpenWhenPullingAway() {
        put(s.copy(openDoors = setOf("ROW_2_LEFT")))
        change { it.copy(gear = Gear.DRIVE) }
        assertTrue(items.isEmpty())
        change { it.copy(timeMs = it.timeMs + 500, speedKmh = 2f) }
        assertEquals(listOf(ExamItem.DOOR), items)
    }

    @Test
    fun parkingBrakeWhileMoving() {
        put(s.copy(parkingBrakeOn = true))
        change { it.copy(gear = Gear.DRIVE) }
        assertTrue(items.isEmpty())
        change { it.copy(timeMs = it.timeMs + 500, speedKmh = 2f) }
        assertEquals(listOf(ExamItem.PARKING_BRAKE), items)
    }

    @Test
    fun unknownParkingBrakeIsNotScored() {
        put(s.copy(parkingBrakeOn = null))
        change { it.copy(gear = Gear.DRIVE) }
        change { it.copy(timeMs = it.timeMs + 500, speedKmh = 2f) }
        assertTrue(items.isEmpty())
    }

    @Test
    fun stopTenSecondsInDrive() {
        startDriving()
        stop()
        change { it.copy(braking = true) }
        run(9_800)
        assertFalse(ExamItem.STOP_NOT_NEUTRAL in items)
        run(400)
        assertTrue(ExamItem.STOP_NOT_NEUTRAL in items)
    }

    @Test
    fun neutralAtStopIsFine() {
        startDriving()
        stop()
        change { it.copy(gear = Gear.NEUTRAL, braking = true) }
        run(15_000)
        assertFalse(ExamItem.STOP_NOT_NEUTRAL in items)
    }

    @Test
    fun coastingInNeutral() {
        startDriving()
        change { it.copy(gear = Gear.NEUTRAL) }
        assertEquals(listOf(ExamItem.COAST_NEUTRAL), items)
    }

    @Test
    fun stopWithoutBrakeAfterMoving() {
        startDriving()
        stop()
        run(3_200)
        assertTrue(ExamItem.STOP_NO_BRAKE in items)
    }

    @Test
    fun noBrakeBeforeFirstMoveIsNotScored() {
        put(s)
        change { it.copy(gear = Gear.DRIVE, braking = false) }
        run(5_000)
        assertFalse(ExamItem.STOP_NO_BRAKE in items)
    }

    @Test
    fun brakingAtStopIsFine() {
        startDriving()
        stop()
        change { it.copy(braking = true) }
        run(5_000)
        assertFalse(ExamItem.STOP_NO_BRAKE in items)
    }

    @Test
    fun hardStart() {
        put(s)
        change { it.copy(gear = Gear.DRIVE) }
        change { it.copy(timeMs = it.timeMs + 600, speedKmh = 6f) }
        assertEquals(listOf(ExamItem.HARD_START), items)
    }

    @Test
    fun gentleStartIsFine() {
        startDriving(30f)
        assertFalse(ExamItem.HARD_START in items)
    }

    @Test
    fun hardBrake() {
        startDriving(40f)
        change { it.copy(timeMs = it.timeMs + 800, speedKmh = 25f) }
        assertTrue(ExamItem.HARD_BRAKE in items)
    }

    @Test
    fun consoleStyleBrakeIsNotHard() {
        // 콘솔 BRAKE의 최대 감속은 약 3.7km/h/s였습니다(실측).
        startDriving(10f)
        var v = 10f
        while (v > 0f) {
            v = (v - 2f).coerceAtLeast(0f)
            change { it.copy(timeMs = it.timeMs + 540, speedKmh = v) }
        }
        assertFalse(ExamItem.HARD_BRAKE in items)
    }

    @Test
    fun edgeNoSignal() {
        startDriving()
        change { it.copy(steeringAbsDeg = 60f) }
        assertEquals(listOf(ExamItem.EDGE_NO_SIGNAL), items)
    }

    @Test
    fun edgeSignalOffDuringTurn() {
        startDriving()
        change { it.copy(turnSignal = TurnSignal.LEFT) }
        change { it.copy(steeringAbsDeg = 60f) }
        change { it.copy(turnSignal = TurnSignal.OFF) }
        assertEquals(listOf(ExamItem.EDGE_SIGNAL_OFF), items)
    }

    @Test
    fun edgeSignalKeptAfterTurn() {
        startDriving()
        change { it.copy(turnSignal = TurnSignal.LEFT) }
        change { it.copy(steeringAbsDeg = 60f) }
        change { it.copy(steeringAbsDeg = 0f) }
        run(3_000) // 20km/h로 약 16m
        assertEquals(listOf(ExamItem.EDGE_SIGNAL_KEPT), items)
    }

    @Test
    fun turnWithoutSignal() {
        startDriving()
        passEdge()
        change { it.copy(steeringAbsDeg = 60f) }
        assertEquals(listOf(ExamItem.TURN_NO_SIGNAL), items)
    }

    @Test
    fun repeatedSteerResendIsOneEvent() {
        startDriving()
        passEdge()
        change { it.copy(steeringAbsDeg = 60f) }
        run(3_000)
        change { it.copy(steeringAbsDeg = 60f) }
        assertEquals(1, items.count { it == ExamItem.TURN_NO_SIGNAL })
    }

    @Test
    fun slowSteerIsNotScored() {
        startDriving(4f)
        passEdge()
        change { it.copy(steeringAbsDeg = 60f) }
        assertTrue(items.isEmpty())
    }

    @Test
    fun signalTooLate() {
        startDriving()
        passEdge()
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(2_000) // 약 11m
        change { it.copy(steeringAbsDeg = 60f) }
        assertEquals(listOf(ExamItem.SIGNAL_LATE), items)
    }

    @Test
    fun signalEarlyEnough() {
        startDriving()
        passEdge()
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(6_000) // 약 33m
        change { it.copy(steeringAbsDeg = 60f) }
        assertTrue(items.isEmpty())
    }

    @Test
    fun signalOffDuringTurn() {
        startDriving()
        passEdge()
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(6_000)
        change { it.copy(steeringAbsDeg = 60f) }
        change { it.copy(turnSignal = TurnSignal.OFF) }
        assertEquals(listOf(ExamItem.SIGNAL_NOT_KEPT), items)
    }

    @Test
    fun signalNotCanceledAfterTurn() {
        startDriving()
        passEdge()
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(6_000)
        change { it.copy(steeringAbsDeg = 60f) }
        change { it.copy(steeringAbsDeg = 0f) }
        run(1_000)
        assertTrue(items.isEmpty())
        run(1_500)
        assertEquals(listOf(ExamItem.SIGNAL_NOT_CANCELED), items)
    }

    @Test
    fun signalTurnedOnAfterTurnIsNotCanceledFault() {
        startDriving()
        passEdge()
        change { it.copy(steeringAbsDeg = 60f) }
        change { it.copy(steeringAbsDeg = 0f) }
        items.clear()
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(3_000)
        assertFalse(ExamItem.SIGNAL_NOT_CANCELED in items)
    }

    /** 11/6 에뮬레이터 확인에서 찾은 오탐: 출발 진로를 바르게 마치고 10m 안에 다른 깜빡이를 켜면 33번이 나던 것. */
    @Test
    fun newSignalSoonAfterProperEdgeIsNotCanceledFault() {
        startDriving()
        change { it.copy(turnSignal = TurnSignal.LEFT) }
        run(500)
        change { it.copy(steeringAbsDeg = 60f) }
        run(1000)
        change { it.copy(steeringAbsDeg = 0f) }
        run(100)
        change { it.copy(turnSignal = TurnSignal.OFF) }
        run(300) // 약 1.7m: 출발 진로 끝 10m 안
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(6_000)
        assertTrue(items.isEmpty())
    }

    /** 일반 회전도 같습니다: 돈 뒤 끄고 10m 안에 다른 깜빡이를 켜도 33번이 아닙니다. */
    @Test
    fun newSignalSoonAfterCanceledTurnIsNotCanceledFault() {
        startDriving()
        passEdge()
        change { it.copy(turnSignal = TurnSignal.RIGHT) }
        run(6_000)
        change { it.copy(steeringAbsDeg = 60f) }
        change { it.copy(steeringAbsDeg = 0f) }
        run(100)
        change { it.copy(turnSignal = TurnSignal.OFF) }
        run(300)
        change { it.copy(turnSignal = TurnSignal.LEFT) }
        run(6_000)
        assertFalse(ExamItem.SIGNAL_NOT_CANCELED in items)
    }

    @Test
    fun unknownSignalIsNotScored() {
        put(s.copy(turnSignal = TurnSignal.UNKNOWN))
        change { it.copy(gear = Gear.DRIVE) }
        change { it.copy(timeMs = it.timeMs + 500, speedKmh = 3f) }
        change { it.copy(steeringAbsDeg = 60f) }
        assertTrue(items.isEmpty())
    }

    @Test
    fun endInDriveWithoutParkingBrake() {
        startDriving()
        stop()
        change { it.copy(openDoors = setOf(ExamRules.DRIVER_DOOR)) }
        assertTrue(ExamItem.END_NOT_PARK in items)
        assertTrue(ExamItem.END_NO_PARKING_BRAKE in items)
        assertFalse(rules.isActive)
    }

    @Test
    fun driverDoorWhileMovingDoesNotEnd() {
        startDriving()
        change { it.copy(openDoors = setOf(ExamRules.DRIVER_DOOR)) }
        assertTrue(rules.isActive)
        assertEquals(listOf(ExamItem.DOOR), items)
    }

    @Test
    fun seatBeltDisqualifiesOnce() {
        startDriving()
        change { it.copy(seatBeltWarningOn = true) }
        change { it.copy(timeMs = it.timeMs + 100) }
        assertEquals(1, others.count { it is ExamEvent.Disqualified })
    }

    @Test
    fun sameItemCanBeScoredAgainAfterGap() {
        startDriving()
        change { it.copy(openDoors = setOf("ROW_2_LEFT")) }
        change { it.copy(openDoors = emptySet()) }
        change { it.copy(openDoors = setOf("ROW_2_LEFT")) }
        assertEquals(1, items.count { it == ExamItem.DOOR })
        change { it.copy(openDoors = emptySet()) }
        run(9_000)
        change { it.copy(openDoors = setOf("ROW_2_LEFT")) }
        assertEquals(2, items.count { it == ExamItem.DOOR })
    }

    @Test
    fun newExamClearsPreviousTurnState() {
        startDriving()
        change { it.copy(turnSignal = TurnSignal.LEFT, steeringAbsDeg = 60f) }
        change { it.copy(steeringAbsDeg = 0f) }
        stop()
        change { it.copy(gear = Gear.PARK, parkingBrakeOn = true) }
        change { it.copy(openDoors = setOf(ExamRules.DRIVER_DOOR)) }
        change { it.copy(openDoors = emptySet(), turnSignal = TurnSignal.OFF, parkingBrakeOn = false) }
        items.clear()
        change { it.copy(gear = Gear.DRIVE) }
        change { it.copy(timeMs = it.timeMs + 800, speedKmh = 5f) }
        change { it.copy(steeringAbsDeg = 60f) }
        assertEquals(listOf(ExamItem.EDGE_NO_SIGNAL), items)
    }

    @Test
    fun resultScoreAndPassLine() {
        val r = ExamResult(listOf(ExamItem.TURN_NO_SIGNAL, ExamItem.PARKING_BRAKE), emptyList())
        assertEquals(83, r.score)
        assertTrue(r.passedLine)
        assertFalse(ExamResult(listOf(ExamItem.DOOR), listOf(Disqualification.SEAT_BELT)).passedLine)
    }

    @Test
    fun resultPassLineIsSeventy() {
        val ten = List(3) { ExamItem.PARKING_BRAKE }
        assertEquals(70, ExamResult(ten, emptyList()).score)
        assertTrue(ExamResult(ten, emptyList()).passedLine)
        assertFalse(ExamResult(ten + ExamItem.DOOR, emptyList()).passedLine)
    }

    @Test
    fun scoreDoesNotGoBelowZero() {
        assertEquals(0, ExamResult(List(20) { ExamItem.PARKING_BRAKE }, emptyList()).score)
    }
}
