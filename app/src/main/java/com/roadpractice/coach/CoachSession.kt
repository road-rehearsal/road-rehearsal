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
    /** 고른 코스. 없으면 코스 지점(37·38·43)은 보지 않습니다. */
    val course: CourseTracker? = null,
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
        val events = if (ended) ArrayList() else rules.onState(state).toMutableList()
        if (rules.isActive && !ended && events.none { it is ExamEvent.Ended }) events += courseEvents(state)
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
                is ExamEvent.Guide -> say(event.text)
            }
        }
        return events
    }

    /**
     * 코스 사건을 감점과 안내로 바꿉니다. 길 안내(유턴, 이탈, 완주)는 두 모드 모두 말합니다.
     * 지점 예고(횡단보도·일시정지·서행)는 답을 알려 주는 셈이라 연습 모드에서만 말합니다.
     */
    private fun courseEvents(state: VehicleState): List<ExamEvent> {
        val tracker = course ?: return emptyList()
        val t = state.timeMs
        return tracker.onState(state).mapNotNull { e ->
            when (e) {
                is CourseEvent.Ahead -> when {
                    e.checkpoint.type.isTurn ->
                        ExamEvent.Guide(turnSentence(e.checkpoint.type, e.distanceM, NEAR_TURN_M), e, t)
                    mode == CoachMode.PRACTICE -> ExamEvent.Guide(e.checkpoint.type.ahead, e, t)
                    else -> null
                }
                is CourseEvent.Passed -> e.checkpoint.type.item?.takeIf { !e.ok }?.let { ExamEvent.Deduction(it, t) }
                is CourseEvent.ZoneOver -> ExamEvent.Disqualified(
                    if (e.checkpoint.type == CheckpointType.SCHOOL) Disqualification.SCHOOL_ZONE_SPEED else Disqualification.SPEED_OVER,
                    t,
                )
                CourseEvent.OffRoute -> ExamEvent.Guide("코스를 벗어났어요. 안전하게 코스로 돌아가요", e, t)
                is CourseEvent.MissedTurn -> ExamEvent.Guide("${e.checkpoint.type.ahead.removeSuffix("해요")} 지점을 지났어요. 안전하게 코스로 돌아가요", e, t)
                CourseEvent.BackOnRoute -> ExamEvent.Guide("코스로 돌아왔어요", e, t)
                CourseEvent.Done -> ExamEvent.Guide("코스를 다 돌았어요. 안전한 곳에 세워 주세요", e, t)
                is CourseEvent.Progress -> null
                CourseEvent.NotAtStart -> ExamEvent.Guide("코스 출발점에서 시작하면 코스를 따라 안내해요", e, t)
            }
        }
    }

    private companion object {
        /** 이 거리 이하 회전 안내는 "곧 ~해요". CourseThresholds.turnNoticeM의 마지막 값과 같게 둡니다. */
        const val NEAR_TURN_M = 30
    }

    private fun practiceSay(sentence: String, timeMs: Long) {
        if (mode != CoachMode.PRACTICE) return
        val last = lastSpoken[sentence]
        if (last != null && timeMs - last < repeatSpeechGapMs) return
        lastSpoken[sentence] = timeMs
        say(sentence)
    }
}
