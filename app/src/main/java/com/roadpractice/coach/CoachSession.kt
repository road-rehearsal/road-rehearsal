package com.roadpractice.coach

/** 시험 모드는 감점을 말하지 않습니다(별표 26 비고 마). 연습 모드는 그 순간 한 문장으로 말합니다. */
enum class CoachMode { EXAM, PRACTICE }

/**
 * 규칙은 하나이고 모드는 "말할지"만 정합니다.
 * @param say 음성 출력
 * @param repeatSpeechGapMs 연습 모드에서 같은 항목을 다시 말하기까지의 간격(제안값). 감점 기록은 줄이지 않습니다.
 */
class CoachSession(
    val mode: CoachMode,
    private val rules: ExamRules = ExamRules(),
    private val say: (String) -> Unit,
    private val repeatSpeechGapMs: Long = 60_000,
) {
    private val deductions = ArrayList<ExamItem>()
    private val disqualified = ArrayList<Disqualification>()
    private val lastSpoken = HashMap<String, Long>()
    var started = false
        private set
    var ended = false
        private set

    val result: ExamResult get() = ExamResult(deductions.toList(), disqualified.toList())

    /** 새 차량 상태를 넣습니다. 이번에 생긴 사건을 돌려줍니다. */
    fun onState(state: VehicleState): List<ExamEvent> {
        val events = if (ended) emptyList() else rules.onState(state)
        events.forEach { event ->
            when (event) {
                is ExamEvent.Started -> started = true
                is ExamEvent.Ended -> ended = true
                is ExamEvent.Deduction -> {
                    deductions += event.item
                    practiceSay(event.item.sentence, event.timeMs)
                }
                is ExamEvent.Disqualified -> {
                    disqualified += event.reason
                    practiceSay(event.reason.sentence, event.timeMs)
                }
            }
        }
        return events
    }

    private fun practiceSay(sentence: String, timeMs: Long) {
        if (mode != CoachMode.PRACTICE) return
        val last = lastSpoken[sentence]
        if (last != null && timeMs - last < repeatSpeechGapMs) return
        lastSpoken[sentence] = timeMs
        say(sentence)
    }
}
