package com.roadpractice.coach

import com.roadpractice.coach.ui.speedLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/** 제한속도 표지 색 단계(11/7 사용자 결정): 지킴 0, 넘음 1(노랑), 실격 기준 2(빨강). */
class SpeedLevelTest {
    @Test
    fun ordinaryZoneWarnsThenFails() {
        assertEquals(0, speedLevel(30f, 30, school = false))
        assertEquals(1, speedLevel(31f, 30, school = false))
        assertEquals(1, speedLevel(40f, 30, school = false)) // 10km/h까지는 경고
        assertEquals(2, speedLevel(41f, 30, school = false))
    }

    @Test
    fun schoolZoneFailsAsSoonAsOver() {
        assertEquals(0, speedLevel(30f, 30, school = true))
        assertEquals(2, speedLevel(31f, 30, school = true))
    }

    @Test
    fun unknownSpeedIsNeutral() {
        assertEquals(0, speedLevel(null, 30, school = true))
    }
}
