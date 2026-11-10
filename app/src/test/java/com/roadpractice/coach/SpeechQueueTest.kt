package com.roadpractice.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechQueueTest {
    private val spoken = ArrayList<String>()
    private val done = ArrayList<() -> Unit>()
    private val queue = SpeechQueue({ text, onDone ->
        spoken += text
        done += onDone
    })

    @Test
    fun speaksOneAtATime() {
        queue.say("가")
        queue.say("나")
        assertEquals(listOf("가"), spoken)
        done.removeAt(0).invoke()
        assertEquals(listOf("가", "나"), spoken)
    }

    @Test
    fun dropsOldestWhenBehind() {
        queue.say("가")
        queue.say("나")
        queue.say("다")
        queue.say("라")
        done.removeAt(0).invoke()
        assertEquals(listOf("가", "다"), spoken)
        done.removeAt(0).invoke()
        assertEquals(listOf("가", "다", "라"), spoken)
    }

    @Test
    fun ignoresSamePendingSentence() {
        queue.say("가")
        queue.say("나")
        queue.say("나")
        done.removeAt(0).invoke()
        done.removeAt(0).invoke()
        assertEquals(listOf("가", "나"), spoken)
    }

    @Test
    fun doubleDoneIsSafe() {
        queue.say("가")
        queue.say("나")
        queue.say("다")
        val first = done.removeAt(0)
        first()
        first()
        assertEquals(listOf("가", "나"), spoken)
    }

    @Test
    fun keepsTurnGuideWhenBehind() {
        // 11/8 재생: 감점 문장이 몰려도 회전 안내는 버리지 않고, 앞 단계("40미터 앞")는 "지금"으로 바뀜
        queue.say("조금 더 천천히 출발해요")
        queue.say("40미터 앞에서 우회전해요")
        queue.say("앞에 과속방지턱이 있어요. 속도를 줄여요")
        queue.say("지금 우회전해요")
        queue.say("조금 더 부드럽게요")
        done.removeAt(0).invoke()
        done.removeAt(0).invoke()
        assertEquals(listOf("조금 더 천천히 출발해요", "지금 우회전해요", "조금 더 부드럽게요"), spoken)
    }

    @Test
    fun turnGuideJumpsAheadOfWaitingSentences() {
        // 11/8 Codex 점검 7: "지금 우회전"이 기다리던 보호구역 예고보다 먼저
        queue.say("오래 설 때는 중립이에요")
        queue.say("앞에 어린이보호구역이 있어요. 30 이하로 가요")
        queue.say("지금 우회전 뒤 바로 좌회전해요")
        done.removeAt(0).invoke()
        assertEquals("지금 우회전 뒤 바로 좌회전해요", spoken.last())
    }

    @Test
    fun staleSpeed() {
        assertEquals(false, SignalHub.isStale(5_000, 0, 2_000)) // 아직 못 받음
        assertEquals(false, SignalHub.isStale(5_000, 4_000, 2_000))
        assertEquals(true, SignalHub.isStale(7_000, 4_000, 2_000))
    }

    @Test
    fun turnGuideSentences() {
        assertEquals(true, SpeechQueue.isTurnGuide("300미터 앞에서 좌회전해요"))
        assertEquals(true, SpeechQueue.isTurnGuide("지금 우회전 뒤 바로 좌회전해요"))
        assertEquals(true, SpeechQueue.isTurnGuide("지금 유턴해요"))
        assertEquals(false, SpeechQueue.isTurnGuide("좌회전 지점을 지났어요. 안전하게 코스로 돌아가요"))
        assertEquals(false, SpeechQueue.isTurnGuide("출발할 때 방향지시등을 켜요"))
    }

    @Test
    fun clearDropsPending() {
        queue.say("가")
        queue.say("나")
        queue.clear()
        done.removeAt(0).invoke()
        assertEquals(listOf("가"), spoken)
    }
}
