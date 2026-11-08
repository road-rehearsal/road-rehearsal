package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 회전 놓침, 화면용 다음 회전·구간 제한속도, 지점 결과(11/7 추가). */
class CourseGuideTest {
    // 북쪽으로 10칸(약 22m씩) 간 뒤 동쪽으로 10칸: 모퉁이(약 222m)에서 우회전
    private val step = 0.0002
    private val lat = DoubleArray(21) { k -> 37.0 + step * minOf(k, 10) }
    private val lng = DoubleArray(21) { k -> 127.0 + step * 1.25 * maxOf(0, k - 10) }
    private val corner = Course("l", "L", lat, lng, emptyList()).cumM[10]
    private val right = Checkpoint(CheckpointType.RIGHT, "우회전", corner, 15.0)
    private val course = Course("l", "L 코스", lat, lng, listOf(right))

    private var time = 0L
    private fun at(la: Double, lo: Double, kmh: Float = 20f) = VehicleState(timeMs = (time++) * 1000, speedKmh = kmh, latitude = la, longitude = lo)

    @Test
    fun followingTheTurnRecordsItAsDone() {
        val tr = CourseTracker(course)
        for (k in lat.indices) tr.onState(at(lat[k], lng[k]))
        val r = tr.pointResults.single()
        assertEquals(true, r.ok)
        assertTrue(r.detail.startsWith("안내"))
    }

    @Test
    fun goingStraightPastTheCornerIsMissedTurn() {
        val tr = CourseTracker(course)
        val events = ArrayList<CourseEvent>()
        for (k in 0..10) events += tr.onState(at(lat[k], lng[k]))
        // 모퉁이에서 돌지 않고 북쪽으로 계속(22m씩)
        for (j in 1..4) events += tr.onState(at(lat[10] + step * j, lng[10]))
        assertEquals(1, events.count { it is CourseEvent.MissedTurn })
        assertEquals(false, tr.pointResults.single().ok)
        // 코스로 돌아오면 "돌아왔어요"
        val back = tr.onState(at(lat[12], lng[12]))
        assertTrue(CourseEvent.BackOnRoute in back)
    }

    @Test
    fun nextTurnShowsOnlyWithin300m() {
        val tr = CourseTracker(course)
        tr.onState(at(lat[0], lng[0]))
        val first = tr.nextTurn()
        assertNotNull(first) // 모퉁이까지 약 222m
        assertEquals(CheckpointType.RIGHT, first!!.type)
        for (k in 1..12) tr.onState(at(lat[k], lng[k]))
        assertNull(tr.nextTurn()) // 지난 뒤에는 없음
    }

    @Test
    fun straightShownWhenNoTurnWithin300m() {
        // 회전이 500m 앞에 있는 긴 직선 코스
        val far = Checkpoint(CheckpointType.LEFT, "좌회전", 500.0, 15.0)
        val lat2 = DoubleArray(40) { k -> 37.0 + step * k }
        val lng2 = DoubleArray(40) { 127.0 }
        val tr = CourseTracker(Course("s", "S", lat2, lng2, listOf(far)))
        tr.onState(at(lat2[0], lng2[0]))
        val s = tr.straightAhead()
        assertNotNull(s)
        assertEquals(CheckpointType.LEFT, s!!.second)
        assertTrue(s.first in 490..500)
        // 300m 안에 들어오면 직진 대신 회전 화살표
        for (k in 1..12) tr.onState(at(lat2[k], lng2[k]))
        assertNull(tr.straightAhead())
        assertNotNull(tr.nextTurn())
        // 회전을 지난 뒤에는 도착까지
        for (k in 13..30) tr.onState(at(lat2[k], lng2[k]))
        assertNull(tr.straightAhead()!!.second)
    }

    @Test
    fun zoneRunningToCourseEndIsJudgedAtDone() {
        // 보호구역이 코스 끝까지 이어져도(빠져나가기 전에 완주) 판정합니다.
        val lat2 = DoubleArray(20) { k -> 37.0 + step * k }
        val lng2 = DoubleArray(20) { 127.0 }
        val c = Course("e", "E", lat2, lng2, emptyList())
        val school = Checkpoint(CheckpointType.SCHOOL, "어린이보호구역", c.lengthM - 50, 80.0, 30f)
        val tr = CourseTracker(c.copy(checkpoints = listOf(school)))
        val events = (0 until 20).flatMap { k -> tr.onState(at(lat2[k], lng2[k], 40f)) }
        assertEquals(1, events.count { it is CourseEvent.ZoneOver })
        assertTrue(events.last() is CourseEvent.Done)
        assertEquals(false, tr.pointResults.single().ok)
    }

    @Test
    fun activeZoneGivesLimitInside() {
        val zone = Checkpoint(CheckpointType.SCHOOL, "어린이보호구역", 100.0, 40.0)
        val tr = CourseTracker(Course("z", "Z", lat, lng, listOf(zone)))
        tr.onState(at(lat[0], lng[0]))
        assertNull(tr.activeZoneLimit())
        for (k in 1..4) tr.onState(at(lat[k], lng[k]))
        assertEquals(30f, tr.activeZoneLimit()!!.second) // 보호구역 기본 30
    }

    // ---- 11/8 안내 개선(4_개발/안내_레퍼런스.md, 2차 기록)

    /** 북쪽으로 곧게 가는 긴 코스(약 22m 간격 60점, 약 1.3km). */
    private val latL = DoubleArray(60) { k -> 37.0 + step * k }
    private val lngL = DoubleArray(60) { 127.0 }

    @Test
    fun nearNoticeGrowsWithSpeed() {
        val t = CourseThresholds()
        assertEquals(80, nearNoticeM(20f, t))   // 최소
        assertEquals(100, nearNoticeM(30f, t))  // 2.222×30+37.1 = 103.8
        assertEquals(170, nearNoticeM(60f, t))  // 170.4
        assertEquals(200, nearNoticeM(90f, t))  // 최대
        assertEquals(30, nowNoticeM(20f, t))
        assertEquals(41, nowNoticeM(50f, t))    // 3초 = 41.7m
    }

    @Test
    fun fastApproachGetsEarlierNearNotice() {
        // 60km/h로 다가가면 가까운 안내가 100m보다 앞(약 170m)에서 나옵니다
        val turn = Checkpoint(CheckpointType.LEFT, "좌회전", 600.0, 15.0)
        val tr = CourseTracker(Course("f", "F", latL, lngL, listOf(turn)))
        val near = (0 until 40).flatMap { k -> tr.onState(at(latL[k], lngL[k], 60f)) }
            .filterIsInstance<CourseEvent.Ahead>().first { it.stage == TurnStage.NEAR }
        assertTrue("가까운 안내 ${near.distanceM}m", near.distanceM in 140..170)
    }

    @Test
    fun closeConsecutiveTurnsAreStacked() {
        // 우회전 뒤 68m 만에 좌회전(2차 기록과 같은 간격): 한 번에 말하고, 두 번째는 "지금"만
        val r = Checkpoint(CheckpointType.RIGHT, "우회전", 500.0, 15.0)
        val l = Checkpoint(CheckpointType.LEFT, "좌회전", 568.0, 15.0)
        val tr = CourseTracker(Course("c", "C", latL, lngL, listOf(r, l)))
        val events = ArrayList<CourseEvent>()
        for (k in 0 until 35) {
            events += tr.onState(at(latL[k], lngL[k], 30f))
            if (k == 20) assertEquals(CheckpointType.LEFT, tr.nextTurn()?.then) // 화살표 두 개
        }
        val ahead = events.filterIsInstance<CourseEvent.Ahead>()
        val first = ahead.filter { it.checkpoint == r }
        assertTrue(first.all { it.then == CheckpointType.LEFT })
        val second = ahead.filter { it.checkpoint == l }
        assertEquals(listOf(TurnStage.NOW), second.map { it.stage })
    }

    @Test
    fun stackedTurnStaysOnScreenWhenSlowing() {
        // 30km/h로 묶어 말한 뒤 10km/h로 느려져도 화면의 두 번째 화살표는 남습니다
        val r = Checkpoint(CheckpointType.RIGHT, "우회전", 500.0, 15.0)
        val l = Checkpoint(CheckpointType.LEFT, "좌회전", 568.0, 15.0)
        val tr = CourseTracker(Course("c", "C", latL, lngL, listOf(r, l)))
        for (k in 0..10) tr.onState(at(latL[k], lngL[k], 30f))
        for (k in 11..18) tr.onState(at(latL[k], lngL[k], 10f))
        assertEquals(CheckpointType.LEFT, tr.nextTurn()?.then)
    }

    @Test
    fun farApartTurnsAreNotStacked() {
        val r = Checkpoint(CheckpointType.RIGHT, "우회전", 400.0, 15.0)
        val l = Checkpoint(CheckpointType.LEFT, "좌회전", 900.0, 15.0)
        val tr = CourseTracker(Course("c", "C", latL, lngL, listOf(r, l)))
        val ahead = (0 until 50).flatMap { k -> tr.onState(at(latL[k], lngL[k], 30f)) }.filterIsInstance<CourseEvent.Ahead>()
        assertTrue(ahead.none { it.then != null })
        assertEquals(listOf(TurnStage.FAR, TurnStage.NEAR, TurnStage.NOW), ahead.filter { it.checkpoint == l }.map { it.stage })
    }

    @Test
    fun longStopBeforeTurnRepeatsGuidanceOnStart() {
        // 회전 약 110m 앞에서 신호 대기 40초 → 출발하면 한 번 더 안내(2차 기록: 9개 중 5개가 이런 경우)
        val turn = Checkpoint(CheckpointType.LEFT, "좌회전", 400.0, 15.0)
        val tr = CourseTracker(Course("s", "S", latL, lngL, listOf(turn)))
        val events = ArrayList<CourseEvent>()
        for (k in 0..13) events += tr.onState(at(latL[k], lngL[k], 30f)) // 약 289m 지점
        repeat(40) { events += tr.onState(at(latL[13], lngL[13], 0f)) }
        events += tr.onState(at(latL[13], lngL[13], 15f))
        val again = events.filterIsInstance<CourseEvent.Ahead>().filter { it.stage == TurnStage.AGAIN }
        assertEquals(1, again.size)
        assertTrue(again.single().distanceM in 100..120)
        // 다시 서도 같은 회전은 한 번만
        repeat(20) { events += tr.onState(at(latL[13], lngL[13], 0f)) }
        events += tr.onState(at(latL[13], lngL[13], 15f))
        assertEquals(1, events.filterIsInstance<CourseEvent.Ahead>().count { it.stage == TurnStage.AGAIN })
    }

    @Test
    fun shortStopDoesNotRepeat() {
        val turn = Checkpoint(CheckpointType.LEFT, "좌회전", 400.0, 15.0)
        val tr = CourseTracker(Course("s", "S", latL, lngL, listOf(turn)))
        val events = ArrayList<CourseEvent>()
        for (k in 0..13) events += tr.onState(at(latL[k], lngL[k], 30f))
        repeat(5) { events += tr.onState(at(latL[13], lngL[13], 0f)) }
        events += tr.onState(at(latL[13], lngL[13], 15f))
        assertTrue(events.filterIsInstance<CourseEvent.Ahead>().none { it.stage == TurnStage.AGAIN })
    }

    @Test
    fun turnNearEndIsGuidedBeforeDone() {
        // 코스 끝 1% 안쪽(98% 지점 뒤)에 회전이 있어도, 그 회전을 지나기 전에는 완주로 보지 않습니다
        val c = Course("e", "E", latL, lngL, emptyList())
        val turn = Checkpoint(CheckpointType.LEFT, "좌회전", c.lengthM - 15, 15.0)
        val tr = CourseTracker(c.copy(checkpoints = listOf(turn)))
        val events = latL.indices.flatMap { k -> tr.onState(at(latL[k], lngL[k], 30f)) }
        val nowAt = events.indexOfFirst { it is CourseEvent.Ahead && it.stage == TurnStage.NOW }
        val doneAt = events.indexOfFirst { it is CourseEvent.Done }
        assertTrue("지금 안내 $nowAt, 완주 $doneAt", nowAt in 0 until doneAt)
    }

    @Test
    fun burstOfNoticesSpeaksOnlyNearest() {
        // 코스에 들어오자마자 앞쪽 지점 3개가 한꺼번에 예고 범위에 들어오면 하나만 말합니다
        val cps = listOf(
            Checkpoint(CheckpointType.BUMP, "과속방지턱", 40.0, 10.0),
            Checkpoint(CheckpointType.CROSSWALK, "횡단보도", 60.0, 30.0),
            Checkpoint(CheckpointType.SCHOOL, "어린이보호구역", 70.0, 30.0, 30f),
        )
        val tr = CourseTracker(Course("b", "B", latL, lngL, cps))
        val first = tr.onState(at(latL[0], lngL[0], 20f)).filterIsInstance<CourseEvent.Ahead>()
        assertEquals(1, first.size)
        assertEquals(CheckpointType.BUMP, first.single().checkpoint.type)
        // 나머지는 나중에 늦게 나오지 않습니다
        val later = (1 until 10).flatMap { k -> tr.onState(at(latL[k], lngL[k], 20f)) }.filterIsInstance<CourseEvent.Ahead>()
        assertTrue(later.isEmpty())
    }

    @Test
    fun turningFastIsNotSlow37() {
        // 좌·우회전 지점 앞뒤에서 20km/h를 넘으면 37번(서행 위반), 결과표 줄은 ok=false
        val tr = CourseTracker(course)
        val events = lat.indices.flatMap { k -> tr.onState(at(lat[k], lng[k], 30f)) }
        val e = events.filterIsInstance<CourseEvent.TurnNotSlow>().single()
        assertEquals(30f, e.minKmh)
        val r = tr.pointResults.single()
        assertEquals(false, r.ok)
        assertTrue(r.detail, r.detail.contains("서행 20 넘음"))
    }

    @Test
    fun turningSlowPasses37() {
        val tr = CourseTracker(course)
        val events = lat.indices.flatMap { k -> tr.onState(at(lat[k], lng[k], if (k in 8..11) 12f else 30f)) }
        assertTrue(events.none { it is CourseEvent.TurnNotSlow })
        assertEquals(true, tr.pointResults.single().ok)
    }

    @Test
    fun uturnIsNotJudgedFor37() {
        val u = Checkpoint(CheckpointType.UTURN, "유턴", corner, 15.0)
        val tr = CourseTracker(Course("u", "U", lat, lng, listOf(u)))
        val events = lat.indices.flatMap { k -> tr.onState(at(lat[k], lng[k], 30f)) }
        assertTrue(events.none { it is CourseEvent.TurnNotSlow })
    }

    @Test
    fun bumpIsGuidanceOnlyWithTopSpeed() {
        val bump = Checkpoint(CheckpointType.BUMP, "과속방지턱", 300.0, 10.0)
        val tr = CourseTracker(Course("b", "B", latL, lngL, listOf(bump)))
        val events = (0 until 25).flatMap { k -> tr.onState(at(latL[k], lngL[k], 25f)) }
        assertTrue(events.none { it is CourseEvent.Passed || it is CourseEvent.ZoneOver })
        val r = tr.pointResults.single()
        assertNull(r.ok)
        assertEquals("최고 25km/h (참고)", r.detail)
    }

    @Test
    fun pointResultRoundTrips() {
        val p = PointResult(CheckpointType.CROSSWALK, "횡단보도", false, "최저 12km/h")
        assertEquals(p, PointResult.decode(p.encode()))
        assertNull(PointResult.decode("garbage"))
        assertFalse(PointResult.decode(PointResult(CheckpointType.RIGHT, "a|b", null, "").encode())!!.name.contains("|"))
    }
}
