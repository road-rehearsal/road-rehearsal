package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 전체 채점 항목 보기가 별표 26 원문과 맞는지(11/8, 법령 조회 도구로 2026-02-24 개정본 확인). */
class ItemCatalogTest {
    /** 별표 26 항목 번호별 감점(원문에서 옮김. 앱 enum과 따로 적어 서로 확인합니다). */
    private val law = mapOf(
        1 to 5, 2 to 7, 3 to 10, 4 to 5, 5 to 10, 6 to 7, 7 to 7, 8 to 7, 9 to 7, 10 to 5,
        11 to 5, 12 to 5, 13 to 5, 14 to 5, 15 to 5, 16 to 5, 17 to 5, 18 to 5, 19 to 5, 20 to 5,
        21 to 7, 22 to 7, 23 to 7, 24 to 7, 25 to 7, 26 to 7, 27 to 7, 28 to 5, 29 to 10, 30 to 7,
        31 to 7, 32 to 7, 33 to 7, 34 to 7, 35 to 7, 36 to 7, 37 to 10, 38 to 10, 39 to 7, 40 to 7,
        41 to 7, 42 to 7, 43 to 10, 44 to 5, 45 to 5, 46 to 5,
    )

    @Test
    fun coversAll46ItemsOnceWithLawPoints() {
        val items = ItemCatalog.items
        assertEquals((1..46).toList(), items.map { it.number })
        items.forEach { assertEquals("${it.number}번 감점", law[it.number], it.points) }
    }

    @Test
    fun judgeCountsMatchApp() {
        assertEquals(17, ItemCatalog.count(Judge.CAR))
        assertEquals(2, ItemCatalog.count(Judge.COURSE))
        assertEquals(26, ItemCatalog.count(Judge.PASSENGER))
        assertEquals(1, ItemCatalog.count(Judge.NONE))
        // 차·코스가 보는 항목은 신호 설명이 있어야 합니다
        assertTrue(ItemCatalog.items.filter { it.judge == Judge.CAR || it.judge == Judge.COURSE }.all { !it.how.isNullOrBlank() })
    }

    @Test
    fun elevenDisqualifications() {
        val dq = ItemCatalog.disqualifications
        assertEquals(11, dq.size)
        assertEquals(11, dq.map { it.title }.toSet().size)
        assertEquals(2, dq.count { it.judge == Judge.CAR })
        assertEquals(2, dq.count { it.judge == Judge.COURSE })
    }

    @Test
    fun tasksFollowLawGroups() {
        assertEquals("운전자세", ItemCatalog.task(4))
        assertEquals("진로 변경", ItemCatalog.task(34))
        assertEquals("교차로 통행 등", ItemCatalog.task(43))
        assertEquals(11, ItemCatalog.items.map { it.task }.toSet().size)
    }
}
