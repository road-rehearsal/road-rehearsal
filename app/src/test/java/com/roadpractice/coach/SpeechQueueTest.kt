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
    fun clearDropsPending() {
        queue.say("가")
        queue.say("나")
        queue.clear()
        done.removeAt(0).invoke()
        assertEquals(listOf("가"), spoken)
    }
}
