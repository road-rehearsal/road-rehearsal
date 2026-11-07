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
    fun activeZoneGivesLimitInside() {
        val zone = Checkpoint(CheckpointType.SCHOOL, "어린이보호구역", 100.0, 40.0)
        val tr = CourseTracker(Course("z", "Z", lat, lng, listOf(zone)))
        tr.onState(at(lat[0], lng[0]))
        assertNull(tr.activeZoneLimit())
        for (k in 1..4) tr.onState(at(lat[k], lng[k]))
        assertEquals(30f, tr.activeZoneLimit()!!.second) // 보호구역 기본 30
    }

    @Test
    fun pointResultRoundTrips() {
        val p = PointResult(CheckpointType.CROSSWALK, "횡단보도", false, "최저 12km/h")
        assertEquals(p, PointResult.decode(p.encode()))
        assertNull(PointResult.decode("garbage"))
        assertFalse(PointResult.decode(PointResult(CheckpointType.RIGHT, "a|b", null, "").encode())!!.name.contains("|"))
    }
}
