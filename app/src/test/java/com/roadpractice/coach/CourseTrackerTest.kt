package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTrackerTest {
    /** 북쪽으로 19칸(약 22m씩) 갔다가 같은 길로 돌아오는 왕복 코스. */
    private val lat = DoubleArray(39) { k -> 37.0 + STEP * (if (k <= 19) k else 38 - k) }
    private val lng = DoubleArray(39) { 127.0 }

    private fun course(vararg cps: Checkpoint) = Course("t", "시험 코스", lat, lng, cps.toList())

    private var time = 0L
    private fun at(k: Int, kmh: Float) = VehicleState(timeMs = (time++) * 1000, speedKmh = kmh, latitude = lat[k], longitude = lng[k])

    /** 0번부터 끝까지 지정한 속도로 지나갑니다. stopAt이면 그 점에서 한 번 멈춥니다. */
    private fun drive(tracker: CourseTracker, kmh: Float, stopAt: Int = -1): List<CourseEvent> {
        val out = ArrayList<CourseEvent>()
        for (k in lat.indices) {
            if (k == stopAt) out += tracker.onState(at(k, 0f))
            out += tracker.onState(at(k, kmh))
        }
        return out
    }

    private val crosswalk = Checkpoint(CheckpointType.CROSSWALK, "횡단보도", 200.0, 30.0)

    @Test
    fun stoppingBeforeCrosswalkPasses() {
        val events = drive(CourseTracker(course(crosswalk)), 20f, stopAt = 8)
        val passed = events.filterIsInstance<CourseEvent.Passed>().single()
        assertTrue(passed.ok)
    }

    @Test
    fun rollingThroughCrosswalkFails() {
        val passed = drive(CourseTracker(course(crosswalk)), 20f).filterIsInstance<CourseEvent.Passed>().single()
        assertFalse(passed.ok)
        assertEquals(20f, passed.speedKmh)
    }

    @Test
    fun roundTripJudgesCheckpointOnce() {
        // 같은 좌표를 돌아오는 길에 다시 지나도 한 번만 판정합니다(11/5 프로브에서는 3번 판정됨).
        val events = drive(CourseTracker(course(crosswalk)), 20f)
        assertEquals(1, events.count { it is CourseEvent.Passed })
        assertTrue(events.last() is CourseEvent.Done)
    }

    @Test
    fun progressReachesEndOnReturnLeg() {
        val tracker = CourseTracker(course())
        drive(tracker, 30f)
        assertTrue(tracker.done)
        assertTrue(tracker.fraction >= 0.98)
    }

    @Test
    fun slowZoneUsesTopSpeed() {
        val slow = Checkpoint(CheckpointType.SLOW, "서행", 200.0, 40.0)
        assertTrue(drive(CourseTracker(course(slow)), 15f).filterIsInstance<CourseEvent.Passed>().single().ok)
        assertFalse(drive(CourseTracker(course(slow)), 25f).filterIsInstance<CourseEvent.Passed>().single().ok)
    }

    @Test
    fun announcesCheckpointOnceBeforeIt() {
        val events = drive(CourseTracker(course(crosswalk)), 20f)
        assertEquals(1, events.count { it is CourseEvent.Ahead })
        val aheadAt = events.indexOfFirst { it is CourseEvent.Ahead }
        val passedAt = events.indexOfFirst { it is CourseEvent.Passed }
        assertTrue(aheadAt < passedAt)
    }

    @Test
    fun uturnIsGuidanceOnly() {
        val uturn = Checkpoint(CheckpointType.UTURN, "유턴", 420.0, 40.0)
        val events = drive(CourseTracker(course(uturn)), 20f)
        assertEquals(1, events.count { it is CourseEvent.Ahead })
        assertEquals(0, events.count { it is CourseEvent.Passed })
    }

    @Test
    fun offRouteAndBack() {
        val tracker = CourseTracker(course())
        tracker.onState(at(0, 10f))
        tracker.onState(at(1, 10f))
        val far = VehicleState(timeMs = 99_000, speedKmh = 10f, latitude = 37.01, longitude = 127.01)
        assertTrue(tracker.onState(far).isEmpty())
        assertEquals(listOf<CourseEvent>(CourseEvent.OffRoute), tracker.onState(far.copy(timeMs = 100_000)))
        assertTrue(tracker.onState(at(2, 10f)).contains(CourseEvent.BackOnRoute))
    }

    @Test
    fun waitsUntilNearStart() {
        val tracker = CourseTracker(course(crosswalk))
        // 코스 중간(약 330m)에서 시작하면 따라가지 않고 한 번만 알립니다.
        assertEquals(listOf<CourseEvent>(CourseEvent.NotAtStart), tracker.onState(at(15, 0f)))
        assertTrue(tracker.onState(at(15, 0f)).isEmpty())
        assertEquals(0.0, tracker.alongM, 0.0)
        val events = drive(tracker, 20f)
        assertEquals(1, events.count { it is CourseEvent.Passed })
    }

    @Test
    fun bigJumpSkipsCheckpointsWithoutJudging() {
        val tracker = CourseTracker(course(crosswalk))
        tracker.onState(at(0, 0f))
        tracker.onState(at(1, 10f))
        // 약 300m를 한 번에 건너뜀(위치 오류): 횡단보도(200m)를 판정하지 않습니다.
        val events = tracker.onState(at(14, 0f))
        assertTrue(events.none { it is CourseEvent.Passed || it is CourseEvent.Ahead })
    }

    @Test
    fun noLocationNoEvents() {
        val tracker = CourseTracker(course(crosswalk))
        assertTrue(tracker.onState(VehicleState(timeMs = 1, speedKmh = 30f)).isEmpty())
    }

    @Test
    fun sessionScoresCourseAndSpeaksByMode() {
        fun run(mode: CoachMode): Pair<CoachSession, List<String>> {
            val said = ArrayList<String>()
            val session = CoachSession(mode, say = { said += it }, course = CourseTracker(course(crosswalk)))
            time = 0
            // 코스 사건은 시험이 시작된 뒤(P에서 D)만 봅니다.
            session.onState(at(0, 0f).copy(gear = Gear.PARK))
            session.onState(at(0, 0f).copy(gear = Gear.DRIVE))
            for (k in 1..12) session.onState(at(k, 20f).copy(gear = Gear.DRIVE))
            return session to said
        }
        val (exam, examSaid) = run(CoachMode.EXAM)
        assertEquals(listOf(ExamItem.CROSSWALK_STOP), exam.result.deductions.filter { it == ExamItem.CROSSWALK_STOP })
        assertTrue(examSaid.none { it == CheckpointType.CROSSWALK.ahead })
        val (practice, practiceSaid) = run(CoachMode.PRACTICE)
        assertTrue(ExamItem.CROSSWALK_STOP in practice.result.deductions)
        assertTrue(CheckpointType.CROSSWALK.ahead in practiceSaid)
        assertTrue(ExamItem.CROSSWALK_STOP.sentence in practiceSaid)
    }

    @Test
    fun sessionIgnoresCourseBeforeStart() {
        val session = CoachSession(CoachMode.EXAM, say = {}, course = CourseTracker(course(crosswalk)))
        time = 0
        for (k in 0..12) session.onState(at(k, 20f).copy(gear = Gear.PARK))
        assertTrue(session.result.deductions.isEmpty())
    }

    private companion object {
        const val STEP = 0.0002
    }
}
