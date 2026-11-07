package com.roadpractice.coach

/**
 * 도로교통법 시행규칙 별표 26을 참고한 연습용 판정. 공식 채점이 아닙니다.
 * 차량 없이 시험할 수 있도록 입력은 VehicleState뿐이고, 시간도 그 안의 값만 씁니다.
 * 값을 받지 못한 신호(null, UNKNOWN)로는 감점하지 않습니다.
 */
enum class ExamItem(val number: Int, val points: Int, val title: String, val sentence: String) {
    DOOR(1, 5, "차문 닫힘 미확인", "문이 열려 있어요"),
    PARKING_BRAKE(3, 10, "주차 브레이크 미해제", "주차 브레이크를 풀어 주세요"),

    /** 출발 지시(기어를 P에서 뺀 때로 봄) 뒤 20초 안에 출발하지 않음. 신호 대기 정지와 섞이지 않게 처음 출발만 봅니다. */
    NO_START(5, 10, "20초 내 미출발", "준비되면 출발해요"),
    STOP_NOT_NEUTRAL(4, 5, "정지 중 기어 미중립", "오래 설 때는 중립이에요"),
    HARD_START(9, 7, "급조작·급출발", "조금 더 천천히 출발해요"),
    EDGE_NO_SIGNAL(11, 5, "출발 때 신호 안 함", "출발할 때 깜빡이를 켜요"),
    EDGE_SIGNAL_OFF(12, 5, "출발 때 신호 중지", "다 들어갈 때까지 켜 두세요"),
    EDGE_SIGNAL_KEPT(13, 5, "출발 후 신호 계속", "깜빡이를 꺼 주세요"),
    COAST_NEUTRAL(18, 5, "정지 전 미리 중립", "멈추기 전에 중립은 안 돼요"),
    STOP_NO_BRAKE(20, 5, "정지 때 미제동", "브레이크를 밟아 주세요"),
    HARD_BRAKE(21, 7, "급브레이크 사용", "조금 더 부드럽게요"),
    TURN_NO_SIGNAL(30, 7, "진로변경 신호 불이행", "다음엔 깜빡이 먼저요"),
    SIGNAL_LATE(31, 7, "진로변경 30미터 전 미신호", "조금 더 일찍 켜 주세요"),
    SIGNAL_NOT_KEPT(32, 7, "진로변경 신호 미유지", "돌 때는 켜 두세요"),
    SIGNAL_NOT_CANCELED(33, 7, "진로변경 신호 미중지", "깜빡이를 꺼 주세요"),
    // 37·38·43은 코스 지점에서 판정합니다(CourseTracker). 코스 없이 달리면 보지 않습니다.
    SLOW_DOWN(37, 10, "서행 위반", "여기서는 천천히 가요"),
    STOP_SIGN(38, 10, "일시정지 위반", "여기서는 완전히 멈춰요"),
    CROSSWALK_STOP(43, 10, "횡단보도 직전 일시정지 위반", "횡단보도 앞에서는 멈춰요"),
    END_NO_PARKING_BRAKE(44, 5, "종료 주차 브레이크 미작동", "주차 브레이크를 걸어 주세요"),
    END_NOT_PARK(46, 5, "종료 기어 미작동", "내리기 전에 P로 바꿔요"),
}

/** 실격 사유 중 차량 신호로 보는 것. 연습용이므로 주행을 멈추지 않고 결과표에 표시합니다. */
enum class Disqualification(val title: String, val sentence: String) {
    SEAT_BELT("좌석안전띠 미착용", "안전띠를 매 주세요"),

    /** 코스 자료에 보호구역이 있을 때만 봅니다. */
    SCHOOL_ZONE_SPEED("보호구역 최고 속도 초과", "보호구역에서는 천천히 가요"),

    /** 코스 자료에 제한속도 구간이 있을 때만 봅니다. 차량 신호의 제한속도는 에뮬레이터에서 0만 옵니다. */
    SPEED_OVER("지정 최고 속도 10km/h 초과", "제한속도를 지켜요"),
}

sealed interface ExamEvent {
    val timeMs: Long

    data class Deduction(val item: ExamItem, override val timeMs: Long) : ExamEvent
    data class Disqualified(val reason: Disqualification, override val timeMs: Long) : ExamEvent
    data class Started(override val timeMs: Long) : ExamEvent
    data class Ended(override val timeMs: Long) : ExamEvent

    /** 코스 안내 문장. 감점이 아니라 길 안내입니다. */
    data class Guide(val text: String, val course: CourseEvent, override val timeMs: Long) : ExamEvent
}

/**
 * 판정 기준. 별표 26에 숫자가 있는 것은 그 값(10초, 30미터, 10미터)을 쓰고,
 * 나머지는 공개된 값을 찾지 못해 정한 연습용 제안값입니다.
 */
data class ExamThresholds(
    val stopHoldMs: Long = 10_000,
    val signalBeforeM: Double = 30.0,
    val signalAfterM: Double = 10.0,
    val noBrakeHoldMs: Long = 3_000,
    val turnSteerDeg: Float = 30f,
    val turnMinKmh: Float = 5f,
    val hardStartKmhPerS: Float = 8f,
    val hardStartBelowKmh: Float = 15f,
    val hardBrakeKmhPerS: Float = 12f,
    val rateWindowMs: Long = 1_000,
    val rateMinSpanMs: Long = 500,
    val repeatGapMs: Long = 8_000,
    /** 별표 26 5번: 출발 지시 뒤 20초. */
    val noStartMs: Long = 20_000,
)

class ExamRules(private val t: ExamThresholds = ExamThresholds()) {
    private var before: VehicleState? = null
    private var active = false
    private var movedSinceStart = false
    private var startedAt = -1L
    private var noStartScored = false
    private var stoppedAt = -1L
    private var noBrakeAt = -1L
    private var stopScored = false
    private var noBrakeScored = false
    private var turning = false
    private var turnEndM = -1.0
    private var signalOnAtTurnEnd = false
    private var signalOnM = -1.0
    private var edgePhase = false
    private val lastScored = HashMap<ExamItem, Long>()
    private var beltLatched = false
    private val speedHistory = ArrayDeque<Pair<Long, Float>>()

    val isActive: Boolean get() = active

    /** 새 상태를 넣으면 이번에 생긴 사건을 돌려줍니다. */
    fun onState(s: VehicleState): List<ExamEvent> {
        val prev = before
        before = s
        val out = ArrayList<ExamEvent>()
        if (prev == null) return out
        if (!active) {
            if (prev.gear == Gear.PARK && s.gear != Gear.PARK && s.gear != Gear.UNKNOWN) begin(s, out) else return out
        }
        checkStates(prev, s, out)
        checkRates(s, out)
        checkStops(s, out)
        checkSignals(prev, s, out)
        checkEnd(prev, s, out)
        return out
    }

    private fun begin(s: VehicleState, out: MutableList<ExamEvent>) {
        active = true
        movedSinceStart = false
        startedAt = s.timeMs
        noStartScored = false
        stoppedAt = -1L
        noBrakeAt = -1L
        turning = false
        turnEndM = -1.0
        signalOnAtTurnEnd = false
        signalOnM = if (s.turnSignal.isOn) s.distanceM else -1.0
        edgePhase = true
        beltLatched = false
        lastScored.clear()
        speedHistory.clear()
        out += ExamEvent.Started(s.timeMs)
    }

    private fun deduct(item: ExamItem, s: VehicleState, out: MutableList<ExamEvent>) {
        val last = lastScored[item]
        if (last != null && s.timeMs - last < t.repeatGapMs) return
        lastScored[item] = s.timeMs
        out += ExamEvent.Deduction(item, s.timeMs)
    }

    private fun checkStates(prev: VehicleState, s: VehicleState, out: MutableList<ExamEvent>) {
        if (s.isMoving) movedSinceStart = true
        // 속도 값을 받은 뒤에만 봅니다(값이 없으면 감점하지 않음).
        if (!movedSinceStart && !noStartScored && s.speedKmh != null && s.timeMs - startedAt >= t.noStartMs) {
            noStartScored = true
            deduct(ExamItem.NO_START, s, out)
        }
        if (s.isMoving && s.openDoors.isNotEmpty() && (!prev.isMoving || prev.openDoors.isEmpty())) deduct(ExamItem.DOOR, s, out)
        if (s.isMoving && s.parkingBrakeOn == true && (!prev.isMoving || prev.parkingBrakeOn != true)) deduct(ExamItem.PARKING_BRAKE, s, out)
        if (s.isMoving && s.gear == Gear.NEUTRAL && prev.gear != Gear.NEUTRAL) deduct(ExamItem.COAST_NEUTRAL, s, out)
        val beltOff = s.isMoving && s.seatBeltWarningOn == true
        if (beltOff && !beltLatched) {
            beltLatched = true
            out += ExamEvent.Disqualified(Disqualification.SEAT_BELT, s.timeMs)
        }
    }

    private fun checkRates(s: VehicleState, out: MutableList<ExamEvent>) {
        val kmh = s.speedKmh ?: return
        if (speedHistory.lastOrNull()?.first != s.timeMs) speedHistory.addLast(s.timeMs to kmh)
        while (speedHistory.size > 1 && s.timeMs - speedHistory.first().first > t.rateWindowMs) speedHistory.removeFirst()
        val (firstAt, firstKmh) = speedHistory.first()
        val span = s.timeMs - firstAt
        if (span < t.rateMinSpanMs) return
        val rate = (kmh - firstKmh) * 1000f / span
        if (rate >= t.hardStartKmhPerS && firstKmh < t.hardStartBelowKmh) deduct(ExamItem.HARD_START, s, out)
        if (-rate >= t.hardBrakeKmhPerS) deduct(ExamItem.HARD_BRAKE, s, out)
    }

    private fun checkStops(s: VehicleState, out: MutableList<ExamEvent>) {
        val stoppedInGear = s.speedKmh != null && !s.isMoving && (s.gear == Gear.DRIVE || s.gear == Gear.REVERSE)
        if (!stoppedInGear) {
            stoppedAt = -1L
            noBrakeAt = -1L
            return
        }
        if (stoppedAt < 0) {
            stoppedAt = s.timeMs
            stopScored = false
        }
        if (!stopScored && s.timeMs - stoppedAt >= t.stopHoldMs) {
            stopScored = true
            deduct(ExamItem.STOP_NOT_NEUTRAL, s, out)
        }
        // 출발 직전의 오탐을 막으려고, 한 번 움직인 뒤의 정지만 봅니다.
        if (movedSinceStart && s.braking == false) {
            if (noBrakeAt < 0) {
                noBrakeAt = s.timeMs
                noBrakeScored = false
            }
            if (!noBrakeScored && s.timeMs - noBrakeAt >= t.noBrakeHoldMs) {
                noBrakeScored = true
                deduct(ExamItem.STOP_NO_BRAKE, s, out)
            }
        } else {
            noBrakeAt = -1L
        }
    }

    private fun checkSignals(prev: VehicleState, s: VehicleState, out: MutableList<ExamEvent>) {
        val signalOn = s.turnSignal.isOn
        val signalKnown = s.turnSignal != TurnSignal.UNKNOWN
        if (signalOn && !prev.turnSignal.isOn) signalOnM = s.distanceM
        if (!signalOn && prev.turnSignal.isOn && signalKnown) {
            if (turning && s.isMoving) deduct(if (edgePhase) ExamItem.EDGE_SIGNAL_OFF else ExamItem.SIGNAL_NOT_KEPT, s, out)
            signalOnM = -1.0
            // 다 돈 뒤 깜빡이를 껐으면 그 회전은 바르게 끝난 것입니다. 회전 끝 기억을 지워,
            // 10m 안에 켠 다음 깜빡이를 앞 회전의 "신호 미중지"(13·33번)로 보지 않게 합니다(11/6 에뮬레이터에서 찾은 오탐).
            if (!turning && turnEndM >= 0) {
                turnEndM = -1.0
                edgePhase = false
            }
        }
        val steer = s.steeringAbsDeg
        if (steer != null) {
            val big = steer >= t.turnSteerDeg
            if (big && !turning) {
                turning = true
                turnEndM = -1.0
                if (signalKnown && (s.speedKmh ?: 0f) >= (if (edgePhase) VehicleState.MOVING_KMH else t.turnMinKmh)) {
                    when {
                        !signalOn -> deduct(if (edgePhase) ExamItem.EDGE_NO_SIGNAL else ExamItem.TURN_NO_SIGNAL, s, out)
                        !edgePhase && s.distanceM - signalOnM < t.signalBeforeM -> deduct(ExamItem.SIGNAL_LATE, s, out)
                    }
                }
            } else if (!big && turning) {
                turning = false
                turnEndM = s.distanceM
                signalOnAtTurnEnd = signalOn
            }
        }
        if (turnEndM >= 0 && !turning && s.distanceM - turnEndM >= t.signalAfterM) {
            if (signalOn && signalOnAtTurnEnd) {
                deduct(if (edgePhase) ExamItem.EDGE_SIGNAL_KEPT else ExamItem.SIGNAL_NOT_CANCELED, s, out)
            }
            turnEndM = -1.0
            edgePhase = false
        }
        if (edgePhase && turnEndM >= 0 && !signalOn) edgePhase = false
    }

    private fun checkEnd(prev: VehicleState, s: VehicleState, out: MutableList<ExamEvent>) {
        val opened = DRIVER_DOOR in s.openDoors && DRIVER_DOOR !in prev.openDoors
        if (!opened || s.isMoving) return
        if (s.gear != Gear.PARK && s.gear != Gear.UNKNOWN) deduct(ExamItem.END_NOT_PARK, s, out)
        if (s.parkingBrakeOn == false) deduct(ExamItem.END_NO_PARKING_BRAKE, s, out)
        out += ExamEvent.Ended(s.timeMs)
        active = false
    }

    private val TurnSignal.isOn: Boolean get() = this == TurnSignal.LEFT || this == TurnSignal.RIGHT

    companion object {
        const val DRIVER_DOOR = "ROW_1_LEFT"
    }
}

/** 결과표 계산. 100점에서 빼고 70점 이상이면 합격선 통과입니다(별표 26). 연습용 표시입니다. */
data class ExamResult(val deductions: List<ExamItem>, val disqualified: List<Disqualification>) {
    val totalDeducted: Int get() = deductions.sumOf { it.points }
    val score: Int get() = (MAX_SCORE - totalDeducted).coerceAtLeast(0)
    val passedLine: Boolean get() = score >= PASS_SCORE && disqualified.isEmpty()

    companion object {
        const val MAX_SCORE = 100
        const val PASS_SCORE = 70
    }
}
