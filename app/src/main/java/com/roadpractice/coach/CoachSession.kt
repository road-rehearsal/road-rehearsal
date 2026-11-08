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

    /** 시연용 구간 재생: 이미 차로 위(교차로 정차 등)에서 시작합니다. 가장자리 출발(11~13번)은 보지 않습니다. */
    fun startMidRoad() {
        rules.startsAtRoadEdge = false
    }

    /** 새 차량 상태를 넣습니다. 이번에 생긴 사건을 돌려줍니다. */
    fun onState(state: VehicleState): List<ExamEvent> {
        val events = if (ended) ArrayList() else rules.onState(state).toMutableList()
        if (rules.isActive && !ended && events.none { it is ExamEvent.Ended }) events += courseEvents(state)
        // 운전석 문으로 끝낼 때 아직 안에 있는 구간(보호구역·제한속도)을 정산합니다. 결과 기록 순서를 지키려고 Ended 앞에 넣습니다
        // (11/8 Codex 점검 1: 보호구역 안에서 끝내면 "시험이면 실격" 안내만 있고 실격이 결과에 없었음)
        val endAt = events.indexOfFirst { it is ExamEvent.Ended }
        if (endAt >= 0 && course != null) events.addAll(endAt, course.finish().mapNotNull { mapCourse(it, state.timeMs) })
        // P가 아닌 기어로 켜져 채점을 시작하지 못하면 한 번 알립니다(11/8 사용자 결정 (가)). 두 모드 모두
        if (!started && !ended && rules.needsPark && !toldPark) {
            toldPark = true
            events += ExamEvent.Guide(PARK_FIRST, null, state.timeMs)
        }
        val extra = ArrayList<ExamEvent>()
        events.forEach { event ->
            when (event) {
                is ExamEvent.Started -> started = true
                is ExamEvent.Ended -> ended = true
                is ExamEvent.Deduction -> {
                    deductions += event.item
                    practiceSay(event.item.sentence, event.timeMs)
                    // 실격 1) 3회 이상 급브레이크·급출발(11/8). 9·21번 횟수 합계로 보는 근사입니다.
                    if (event.item in HARD_ITEMS && Disqualification.REPEATED_HARD !in disqualified &&
                        deductions.count { it in HARD_ITEMS } >= REPEATED_HARD_COUNT
                    ) {
                        disqualified += Disqualification.REPEATED_HARD
                        practiceSay(Disqualification.REPEATED_HARD.sentence, event.timeMs)
                        extra += ExamEvent.Disqualified(Disqualification.REPEATED_HARD, event.timeMs, speak = false)
                    }
                }
                is ExamEvent.Disqualified -> {
                    disqualified += event.reason
                    if (event.speak) practiceSay(event.reason.sentence, event.timeMs)
                }
                is ExamEvent.Guide -> say(event.text)
            }
        }
        return events + extra
    }

    /**
     * 코스 사건을 감점과 안내로 바꿉니다. 길 안내(유턴, 이탈, 완주)는 두 모드 모두 말합니다.
     * 지점 예고(횡단보도·일시정지·서행)는 답을 알려 주는 셈이라 연습 모드에서만 말합니다.
     */
    private fun courseEvents(state: VehicleState): List<ExamEvent> {
        val tracker = course ?: return emptyList()
        return tracker.onState(state).mapNotNull { mapCourse(it, state.timeMs) }
    }

    private fun mapCourse(e: CourseEvent, t: Long): ExamEvent? =
            when (e) {
                is CourseEvent.Ahead -> when {
                    e.checkpoint.type.isTurn ->
                        ExamEvent.Guide(turnSentence(e.checkpoint.type, e.distanceM, e.stage, e.then), e, t)
                    mode == CoachMode.PRACTICE -> ExamEvent.Guide(aheadSentence(e.checkpoint), e, t)
                    else -> null
                }
                is CourseEvent.Passed -> e.checkpoint.type.item?.takeIf { !e.ok }?.let { ExamEvent.Deduction(it, t) }
                // 좌·우회전 지점 서행(37번 1), 11/8)
                is CourseEvent.TurnNotSlow -> ExamEvent.Deduction(ExamItem.SLOW_DOWN, t)
                is CourseEvent.ZoneOver -> ExamEvent.Disqualified(
                    if (e.checkpoint.type == CheckpointType.SCHOOL) Disqualification.SCHOOL_ZONE_SPEED else Disqualification.SPEED_OVER,
                    t,
                    speak = e.checkpoint !in warnedDq,
                )
                // 넘는 순간 말합니다(연습 모드만, 11/8 사용자 결정). 시험 모드는 침묵(별표 26 비고 마).
                is CourseEvent.SpeedOver -> if (mode == CoachMode.PRACTICE) {
                    if (e.level >= 2) warnedDq += e.checkpoint
                    ExamEvent.Guide(speedOverSentence(e), e, t)
                } else {
                    null
                }
                CourseEvent.OffRoute -> ExamEvent.Guide("코스를 벗어났어요. 안전하게 코스로 돌아가요", e, t)
                is CourseEvent.MissedTurn -> ExamEvent.Guide("${e.checkpoint.type.ahead.removeSuffix("해요")} 지점을 지났어요. 안전하게 코스로 돌아가요", e, t)
                CourseEvent.BackOnRoute -> ExamEvent.Guide("코스로 돌아왔어요", e, t)
                CourseEvent.Done -> ExamEvent.Guide("코스를 다 돌았어요. 안전한 곳에 세워 주세요", e, t)
                is CourseEvent.Progress -> null
                CourseEvent.NotAtStart -> ExamEvent.Guide("코스 출발점에서 시작하면 코스를 따라 안내해요", e, t)
            }

    private var toldPark = false

    companion object {
        const val PARK_FIRST = "기어를 P로 바꾼 뒤 출발하면 채점을 시작해요"
        private val HARD_ITEMS = setOf(ExamItem.HARD_START, ExamItem.HARD_BRAKE)
        /** 별표 26 실격 1) "3회 이상". */
        private const val REPEATED_HARD_COUNT = 3
    }

    /** 실격 기준을 넘는 순간 이미 말한 구간(구간을 나갈 때 실격 문장을 또 말하지 않음). */
    private val warnedDq = HashSet<Checkpoint>()

    private fun speedOverSentence(e: CourseEvent.SpeedOver): String {
        val limit = e.limitKmh.toInt()
        return when {
            e.checkpoint.type == CheckpointType.SCHOOL -> "어린이보호구역 ${limit}을 넘었어요. 시험이면 실격이에요"
            e.level >= 2 -> "제한속도보다 10km/h 넘게 빨라요. 시험이면 실격이에요"
            else -> "제한속도 ${limit}을 넘었어요. 속도를 줄여요"
        }
    }

    /** 지점 예고 문장. 제한속도·보호구역은 숫자를 함께 말합니다(11/8: 구간이 바뀔 때 무엇으로 바뀌는지 알게). */
    private fun aheadSentence(cp: Checkpoint): String = when (cp.type) {
        CheckpointType.SPEED -> cp.limitKmh?.let { "앞으로 제한속도 ${it.toInt()}이에요" } ?: cp.type.ahead
        CheckpointType.SCHOOL -> "앞에 어린이보호구역이 있어요. ${(cp.limitKmh ?: 30f).toInt()} 이하로 가요"
        else -> cp.type.ahead
    }

    private fun practiceSay(sentence: String, timeMs: Long) {
        if (mode != CoachMode.PRACTICE) return
        val last = lastSpoken[sentence]
        if (last != null && timeMs - last < repeatSpeechGapMs) return
        lastSpoken[sentence] = timeMs
        say(sentence)
    }
}
