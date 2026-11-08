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

    /** 횡단보도 바로 뒤(20m)에서 도는 우회전. 횡단보도 예고는 이런 곳에서만 합니다(11/8 사용자 결정). */
    private val turnAtCrosswalk = Checkpoint(CheckpointType.RIGHT, "우회전", 220.0, 15.0)

    @Test
    fun announcesCheckpointOnceBeforeIt() {
        val events = drive(CourseTracker(course(crosswalk, turnAtCrosswalk)), 20f)
        val cw = events.filterIsInstance<CourseEvent.Ahead>().filter { it.checkpoint == crosswalk }
        assertEquals(1, cw.size)
        val aheadAt = events.indexOf(cw.single())
        val passedAt = events.indexOfFirst { it is CourseEvent.Passed }
        assertTrue(aheadAt < passedAt)
    }

    @Test
    fun crosswalkOnStraightIsNotAnnounced() {
        // 직진 중 지나는 횡단보도는 예고하지 않습니다(신호대로 지나면 됨). 지날 때 속도는 결과표에 참고로 남습니다.
        val tr = CourseTracker(course(crosswalk))
        val events = drive(tr, 20f)
        assertTrue(events.none { it is CourseEvent.Ahead })
        assertEquals(1, tr.pointResults.count { it.type == CheckpointType.CROSSWALK })
    }

    @Test
    fun turnNotSlowDeducts37InSession() {
        val right = Checkpoint(CheckpointType.RIGHT, "우회전", 300.0, 15.0)
        val s = CoachSession(CoachMode.EXAM, say = { }, course = CourseTracker(course(right)))
        time = 0
        s.onState(at(0, 0f).copy(gear = Gear.PARK))
        s.onState(at(0, 0f).copy(gear = Gear.DRIVE))
        for (k in 1..19) s.onState(at(k, 30f).copy(gear = Gear.DRIVE))
        assertEquals(1, s.result.deductions.count { it == ExamItem.SLOW_DOWN })
    }

    @Test
    fun speedOverIsToldAtTheMomentInPracticeOnly() {
        // 제한속도 30 구간에서 38km/h(넘음) → 45km/h(10 넘게): 넘는 순간 단계별로 한 번씩, 실격 문장은 구간을 나갈 때 다시 말하지 않음
        val zone = Checkpoint(CheckpointType.SPEED, "제한속도 30", 200.0, 120.0, 30f)
        fun run(mode: CoachMode): List<String> {
            val said = ArrayList<String>()
            val s = CoachSession(mode, say = { said += it }, course = CourseTracker(course(zone)))
            time = 0
            s.onState(at(0, 0f).copy(gear = Gear.PARK))
            s.onState(at(0, 0f).copy(gear = Gear.DRIVE))
            for (k in 1..19) {
                val kmh = when { k < 5 -> 25f; k < 7 -> 38f; k < 13 -> 45f; else -> 25f }
                s.onState(at(k, kmh).copy(gear = Gear.DRIVE))
            }
            assertTrue(s.result.disqualified.contains(Disqualification.SPEED_OVER))
            return said
        }
        val practice = run(CoachMode.PRACTICE)
        assertEquals(1, practice.count { it == "제한속도 30을 넘었어요. 속도를 줄여요" })
        assertEquals(1, practice.count { it.startsWith("제한속도보다 10km/h 넘게") })
        assertTrue(practice.none { it == Disqualification.SPEED_OVER.sentence })
        assertTrue(run(CoachMode.EXAM).none { it.contains("넘었어요") || it.contains("넘게") })
    }

    @Test
    fun uturnIsGuidanceOnly() {
        val uturn = Checkpoint(CheckpointType.UTURN, "유턴", 420.0, 40.0)
        val events = drive(CourseTracker(course(uturn)), 20f)
        // 먼(300m)·가까운(20km/h면 80m 안)·지금 단계마다 한 번씩
        val ahead = events.filterIsInstance<CourseEvent.Ahead>()
        assertEquals(listOf(TurnStage.FAR, TurnStage.NEAR, TurnStage.NOW), ahead.map { it.stage })
        assertEquals(300, ahead.first().distanceM)
        assertTrue(ahead[1].distanceM <= 80)
        assertEquals(0, events.count { it is CourseEvent.Passed })
    }

    @Test
    fun turnGuidanceSkipsFarNoticeWhenAlreadyClose() {
        // 출발하자마자 70m 앞에서 도는 코스: 300m 안내 없이 가까운 안내부터
        val right = Checkpoint(CheckpointType.RIGHT, "우회전", 70.0, 15.0)
        val ahead = drive(CourseTracker(course(right)), 20f).filterIsInstance<CourseEvent.Ahead>().map { it.stage }
        assertEquals(listOf(TurnStage.NEAR, TurnStage.NOW), ahead)
        assertEquals("지금 우회전해요", turnSentence(CheckpointType.RIGHT, 0, TurnStage.NOW))
        assertEquals("100미터 앞에서 우회전해요", turnSentence(CheckpointType.RIGHT, 100, TurnStage.NEAR))
        assertEquals("60미터 앞에서 우회전 뒤 바로 좌회전해요", turnSentence(CheckpointType.RIGHT, 60, TurnStage.NEAR, CheckpointType.LEFT))
    }

    @Test
    fun schoolZoneOverLimitIsReportedOnce() {
        val school = Checkpoint(CheckpointType.SCHOOL, "어린이보호구역", 200.0, 60.0, 30f)
        val over = drive(CourseTracker(course(school)), 40f).filterIsInstance<CourseEvent.ZoneOver>()
        assertEquals(1, over.size)
        assertEquals(40f, over.single().maxKmh)
        assertTrue(drive(CourseTracker(course(school)), 25f).none { it is CourseEvent.ZoneOver })
    }

    @Test
    fun speedZoneAllowsUpToTenOver() {
        val zone = Checkpoint(CheckpointType.SPEED, "제한속도 30", 200.0, 60.0, 30f)
        assertTrue(drive(CourseTracker(course(zone)), 40f).none { it is CourseEvent.ZoneOver })
        assertEquals(1, drive(CourseTracker(course(zone)), 41f).count { it is CourseEvent.ZoneOver })
    }

    @Test
    fun crosswalkInsideSpeedZoneIsJudgedToo() {
        // 구간이 겹쳐도 둘 다 판정(11/7 전에는 한 번에 한 지점만 셌음)
        val zone = Checkpoint(CheckpointType.SPEED, "제한속도 30", 200.0, 100.0, 30f)
        val events = drive(CourseTracker(course(zone, crosswalk)), 45f)
        assertEquals(1, events.count { it is CourseEvent.ZoneOver })
        assertEquals(1, events.count { it is CourseEvent.Passed })
    }

    @Test
    fun sessionTurnsSchoolZoneIntoDisqualification() {
        val school = Checkpoint(CheckpointType.SCHOOL, "어린이보호구역", 200.0, 60.0, 30f)
        val said = ArrayList<String>()
        val session = CoachSession(CoachMode.EXAM, say = { said += it }, course = CourseTracker(course(school)))
        time = 0
        session.onState(at(0, 0f).copy(gear = Gear.PARK))
        session.onState(at(0, 0f).copy(gear = Gear.DRIVE))
        for (k in 1..15) session.onState(at(k, 45f).copy(gear = Gear.DRIVE))
        assertEquals(listOf(Disqualification.SCHOOL_ZONE_SPEED), session.result.disqualified)
        assertTrue(said.isEmpty()) // 시험 모드는 말하지 않음
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
    fun crosswalkIsGuidanceOnlyAndSpeaksByMode() {
        fun run(mode: CoachMode): Pair<CoachSession, List<String>> {
            val said = ArrayList<String>()
            val session = CoachSession(mode, say = { said += it }, course = CourseTracker(course(crosswalk, turnAtCrosswalk)))
            time = 0
            // 코스 사건은 시험이 시작된 뒤(P에서 D)만 봅니다.
            session.onState(at(0, 0f).copy(gear = Gear.PARK))
            session.onState(at(0, 0f).copy(gear = Gear.DRIVE))
            for (k in 1..12) session.onState(at(k, 20f).copy(gear = Gear.DRIVE))
            return session to said
        }
        // 11/7부터 횡단보도는 감점하지 않습니다(43번은 동승자 체크). 연습 모드에서만 "보행자 확인" 안내.
        val (exam, examSaid) = run(CoachMode.EXAM)
        assertTrue(exam.result.deductions.isEmpty() || exam.result.deductions.none { it.number == 43 })
        assertTrue(examSaid.none { it == CheckpointType.CROSSWALK.ahead })
        val (practice, practiceSaid) = run(CoachMode.PRACTICE)
        assertTrue(practice.result.deductions.none { it.number == 43 })
        assertTrue(CheckpointType.CROSSWALK.ahead in practiceSaid)
        assertEquals(null, practice.course!!.pointResults.single { it.type == CheckpointType.CROSSWALK }.ok)
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
