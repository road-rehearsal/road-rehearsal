package com.roadpractice.coach

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 음성 출력 한 문장. 끝나면 onDone을 부릅니다. 시험할 수 있게 분리했습니다. */
fun interface Speaker {
    fun speak(text: String, onDone: () -> Unit)
}

/**
 * 음성을 한 번에 하나만 냅니다. 겹치면 앞 음성이 끊기는 것으로 보여(실측, 추정) 순서를 지킵니다.
 * 밀리면 오래된 문장을 버립니다. 지난 일을 뒤늦게 말하지 않기 위해서입니다.
 * 회전 안내는 마지막까지 남깁니다(11/8 재생: 출발 직후 감점 문장에 밀려 "40미터 앞에서 우회전해요"·"지금 우회전해요"가 버려짐).
 * 새 회전 안내가 오면 아직 말하지 않은 앞 단계 회전 안내는 버리고("40미터 앞" 대신 "지금"만), 기다리는 문장 맨 앞에 둡니다.
 */
class SpeechQueue(private val speaker: Speaker, private val maxPending: Int = 2) {
    private val pending = ArrayDeque<String>()
    private var speaking = false

    fun say(text: String) {
        if (text in pending) return
        if (isTurnGuide(text)) {
            pending.removeAll { isTurnGuide(it) }
            // 회전 안내는 기다리는 다른 문장보다 먼저 말합니다(11/8 Codex 점검 7: "지금 우회전"이 보호구역 예고 뒤로 밀려 5.8초 늦음).
            // 말하고 있는 문장은 끊지 않습니다(Pleos TTS에서 끊었을 때의 동작은 미확인).
            pending.addFirst(text)
        } else {
            pending.addLast(text)
        }
        while (pending.size > maxPending) {
            val drop = pending.indexOfFirst { !isTurnGuide(it) }
            if (drop >= 0) pending.removeAt(drop) else pending.removeFirst()
        }
        next()
    }

    fun clear() = pending.clear()

    private fun next() {
        if (speaking) return
        val text = pending.removeFirstOrNull() ?: return
        speaking = true
        var finished = false
        speaker.speak(text) {
            if (!finished) {
                finished = true
                speaking = false
                next()
            }
        }
    }

    companion object {
        private val TURN = Regex("(좌회전|우회전|유턴)(해요| 뒤)")

        /** 회전 안내 문장인가("300미터 앞에서 좌회전해요", "지금 우회전 뒤 바로 좌회전해요"). "좌회전 지점을 지났어요"는 아님. */
        fun isTurnGuide(text: String) = TURN.containsMatchIn(text)
    }
}

/**
 * Pleos TextToSpeech. speak(text, isFinal=true)여야 onDone이 옵니다(실측).
 * onDone이 오지 않아도 큐가 멈추지 않도록 시간 제한을 둡니다.
 */
class TtsSpeaker(context: Context) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val engine = TextToSpeech(context, Mode.ON_DEVICE)
    private var ready = false
    private var doneCallback: (() -> Unit)? = null
    private val timeout = Runnable { finish("timeout") }
    private var held: Pair<String, () -> Unit>? = null
    private val giveUp = Runnable {
        held?.let { (_, done) ->
            held = null
            Log.w(TAG, "TTS_NOT_READY dropped")
            done()
        }
    }

    init {
        engine.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                ready = true
                // 준비 전에 온 문장을 이제 말합니다(11/7 재생: 첫 안내가 TTS_NOT_READY로 사라짐).
                held?.let { (text, done) ->
                    held = null
                    main.removeCallbacks(giveUp)
                    main.post { speak(text, done) }
                }
                engine.addEventListener(object : EventListener {
                    override fun onStart() = Unit
                    override fun onReady() = Unit
                    override fun onStop() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() {
                        main.post { finish("done") }
                    }
                    override fun onError(errorType: ErrorType) {
                        main.post { finish("error $errorType") }
                    }
                })
                Log.i(TAG, "TTS_READY")
            }
            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "TTS_INIT_FAILED $errorType")
            }
        })
    }

    override fun speak(text: String, onDone: () -> Unit) {
        Log.i(TAG, "SPEAK $text")
        if (!ready) {
            // 엔진 준비 전: 잠시 들고 있다가 준비되면 말합니다. 너무 늦으면(지난 일) 버립니다.
            Log.w(TAG, "TTS_NOT_READY hold")
            held = text to onDone
            main.removeCallbacks(giveUp)
            main.postDelayed(giveUp, HOLD_MS)
            return
        }
        doneCallback = onDone
        main.postDelayed(timeout, TIMEOUT_MS)
        try {
            engine.speak(text, true)
        } catch (e: Exception) {
            finish("exception ${e.javaClass.simpleName}")
        }
    }

    private fun finish(why: String) {
        val callback = doneCallback ?: return
        doneCallback = null
        main.removeCallbacks(timeout)
        Log.i(TAG, "SPEAK_END $why")
        callback()
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        doneCallback = null
        held = null
        try {
            engine.release()
        } catch (e: Exception) {
            Log.w(TAG, "TTS release: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "RoadCoach"
        const val TIMEOUT_MS = 8_000L

        /** 엔진 준비를 기다리는 최대 시간. 이보다 늦으면 지난 안내라 버립니다. */
        const val HOLD_MS = 6_000L
    }
}
