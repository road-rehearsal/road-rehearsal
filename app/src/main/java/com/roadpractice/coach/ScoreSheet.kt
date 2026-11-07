package com.roadpractice.coach

/**
 * 차가 볼 수 없어 정차 후 동승자가 체크하는 항목(별표 26). 감점은 별표 26의 값입니다.
 * 과제 이름은 결과표에서 묶어 보여 주기 위한 것입니다.
 */
enum class PassengerItem(val number: Int, val points: Int, val task: String, val title: String) {
    SAFETY_CHECK(2, 7, "출발 전 준비", "출발 전 차량점검 및 안전 미확인"),
    // 5번(20초 내 미출발)은 11/7부터 차가 봅니다(ExamItem.NO_START).
    NO_RESTART_10S(6, 7, "출발", "10초 내 미시동"),
    BLOCK_TRAFFIC(7, 7, "출발", "주변 교통 방해"),
    ENGINE_STALL(8, 7, "출발", "엔진 정지"),
    SHAKE(10, 5, "출발", "심한 진동"),
    STARTER_MISUSE(14, 5, "출발", "시동장치 조작 미숙"),
    TOO_SLOW(15, 5, "가속 및 속도 유지", "저속"),
    SPEED_NOT_KEPT(16, 5, "가속 및 속도 유지", "속도 유지 불능"),
    BRAKE_NOT_READY(19, 5, "제동 및 정지", "제동 방법 미흡"),
    STEERING(22, 7, "조향", "핸들 조작 미숙 또는 불량"),
    RIGHT_SIDE_CHECK(23, 7, "차체 감각", "우측 안전 미확인"),
    SIDE_GAP(24, 7, "차체 감각", "1미터 간격 미유지"),
    LANE_RULE(25, 7, "통행 구분", "지정차로 준수위반"),
    OVERTAKE(26, 7, "통행 구분", "앞지르기 방법 등 위반"),
    CUT_IN(27, 7, "통행 구분", "끼어들기 금지 위반"),
    LANE_KEEP(28, 5, "통행 구분", "차로유지 미숙"),
    LANE_CHANGE_LOOK(29, 10, "진로 변경", "진로 변경 시 안전 미확인"),
    LANE_CHANGE_MANY(34, 7, "진로 변경", "진로변경 과다"),
    LANE_CHANGE_FORBIDDEN(35, 7, "진로 변경", "진로변경 금지장소에서의 진로변경"),
    LANE_CHANGE_POOR(36, 7, "진로 변경", "진로변경 미숙"),
    JUNCTION_ENTRY(39, 7, "교차로 통행 등", "교차로 진입 통행 위반"),
    SIGNAL_CAR_BLOCK(40, 7, "교차로 통행 등", "신호차 방해"),
    BOX_BLOCK(41, 7, "교차로 통행 등", "꼬리 물기"),
    NO_YIELD(42, 7, "교차로 통행 등", "신호 없는 교차로 양보 불이행"),
    /** 11/7부터 동승자 체크: 앱은 신호 색과 보행자를 볼 수 없어 녹색 직진 통과도 감점되는 문제가 있었음. */
    CROSSWALK_STOP(43, 10, "교차로 통행 등", "횡단보도 직전 일시정지 위반"),
    ENGINE_LEFT_ON(45, 5, "주행 종료", "종료 엔진 미정지"),
}

/** 동승자가 체크하는 실격 사유(별표 26 제2호 나목). 차가 보는 안전띠는 제외합니다. */
enum class PassengerDisqualification(val title: String) {
    REPEATED_FAIL("3회 이상 출발불능·엔진정지·급브레이크·급출발 등"),
    DANGER("교통사고 위험 또는 사고"),
    IMPAIRED_OR_PHONE("음주·과로·약물·휴대전화 사용 등"),
    SIGNAL("신호 또는 지시 위반"),
    PEDESTRIAN("보행자 보호의무 소홀"),
    ZONE_SPEED("보호구역 최고 속도 초과"),
    CENTER_LINE("중앙선 침범(우측 통행 의무 위반)"),
    SPEED_10("지정 최고 속도 10km/h 초과"),
    EMERGENCY("긴급자동차 양보 안 함"),
    SCHOOL_BUS("어린이통학버스 특별보호의무 위반"),
}

/** 결과표 한 줄. 차가 본 항목은 틀렸다고 보면 제외할 수 있습니다. */
data class SheetLine(
    val number: Int,
    val title: String,
    val points: Int,
    val byCar: Boolean,
    val excluded: Boolean = false,
)

/**
 * 결과표. 100점에서 빼고 70점 이상이면 합격선 통과입니다(별표 26). 연습용 표시이며 공식 채점이 아닙니다.
 */
data class ScoreSheet(
    val lines: List<SheetLine>,
    val disqualifications: List<String>,
) {
    val carDeducted: Int get() = lines.filter { it.byCar && !it.excluded }.sumOf { it.points }
    val passengerDeducted: Int get() = lines.filter { !it.byCar }.sumOf { it.points }
    val score: Int get() = (MAX_SCORE - carDeducted - passengerDeducted).coerceAtLeast(0)
    val passedLine: Boolean get() = score >= PASS_SCORE && disqualifications.isEmpty()

    /** 같은 항목을 묶어 횟수로 보여 줍니다. 제외한 줄은 따로 셉니다. */
    fun grouped(): List<Triple<SheetLine, Int, Int>> =
        lines.groupBy { Triple(it.number, it.byCar, it.excluded) }
            .map { (_, same) -> Triple(same.first(), same.size, same.size * same.first().points) }
            .sortedWith(compareBy({ !it.first.byCar }, { it.first.number }, { it.first.excluded }))

    companion object {
        const val MAX_SCORE = 100
        const val PASS_SCORE = 70

        fun build(
            car: List<ExamItem>,
            excludedCarIndexes: Set<Int>,
            passenger: List<PassengerItem>,
            carDisqualified: List<Disqualification>,
            passengerDisqualified: List<PassengerDisqualification>,
        ): ScoreSheet {
            val carLines = car.mapIndexed { i, item -> SheetLine(item.number, item.title, item.points, byCar = true, excluded = i in excludedCarIndexes) }
            val passengerLines = passenger.map { SheetLine(it.number, it.title, it.points, byCar = false) }
            return ScoreSheet(
                carLines + passengerLines,
                carDisqualified.map { it.title } + passengerDisqualified.map { it.title },
            )
        }
    }
}
