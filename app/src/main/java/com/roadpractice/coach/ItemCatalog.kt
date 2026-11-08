package com.roadpractice.coach

/**
 * 전체 채점 항목 보기(11/8). 도로교통법 시행규칙 별표 26의 46개 항목과 실격 사유를
 * 앱이 어떻게 판정하는지(차·코스 지점·동승자·보지 않음)와 함께 보여 주기 위한 목록입니다.
 * 항목은 ExamItem·PassengerItem에서 만들어 따로 손으로 적지 않습니다(둘이 어긋나지 않게).
 */
enum class Judge(val label: String) {
    CAR("차가 자동"),
    COURSE("코스 지점"),
    PASSENGER("동승자 체크"),
    NONE("이 앱은 보지 않음"),
}

data class CatalogItem(
    val number: Int,
    val points: Int,
    val task: String,
    val title: String,
    val judge: Judge,
    /** 차·코스가 보는 항목이면 쓰는 신호, 보지 않는 항목이면 이유. */
    val how: String?,
)

data class CatalogDq(val title: String, val judge: Judge, val how: String?)

object ItemCatalog {
    /** 별표 26 과제(가~카)와 항목 번호 범위. */
    private val TASKS = listOf(
        1..3 to "출발 전 준비", 4..4 to "운전자세", 5..14 to "출발", 15..17 to "가속 및 속도 유지",
        18..21 to "제동 및 정지", 22..22 to "조향", 23..24 to "차체 감각", 25..28 to "통행 구분",
        29..36 to "진로 변경", 37..43 to "교차로 통행 등", 44..46 to "주행 종료",
    )

    fun task(number: Int): String = TASKS.first { number in it.first }.second

    /** 차·코스가 보는 항목의 신호(실측 근거: 4_개발/신호_실측결과.md). */
    private val SIGNALS = mapOf(
        1 to "문 열림 + 속도", 3 to "주차 브레이크 경고등 + 속도", 4 to "속도 + 기어(10초 정지)",
        5 to "기어를 P에서 뺀 뒤 20초 속도", 9 to "속도 변화(급가속)", 11 to "깜빡이 + 조향각",
        12 to "깜빡이 + 조향각", 13 to "깜빡이 + 조향각 + 이동 거리", 18 to "기어 + 속도",
        20 to "속도 + 기어 + 브레이크", 21 to "속도 변화(급감속)", 30 to "깜빡이 + 조향각",
        31 to "깜빡이 + 이동 거리(30m)", 32 to "깜빡이 + 조향각", 33 to "깜빡이 + 이동 거리(10m)",
        37 to "위치 + 코스 서행 지점·좌우회전 지점 + 속도(20km/h 이하)", 38 to "위치 + 코스 일시정지 지점 + 속도",
        44 to "주차 브레이크 경고등 + 종료", 46 to "기어(P) + 종료",
    )

    /** 별표 26 17번(가속 불가). 수동 변속 전제라 앱이 보지 않습니다. */
    private val NOT_JUDGED = CatalogItem(17, 5, task(17), "가속 불가", Judge.NONE, "수동 변속(저단 가속) 전제라 자동변속 차에서는 보지 않아요")

    val items: List<CatalogItem> by lazy {
        val car = ExamItem.entries.map {
            val course = it == ExamItem.SLOW_DOWN || it == ExamItem.STOP_SIGN
            CatalogItem(it.number, it.points, task(it.number), it.title, if (course) Judge.COURSE else Judge.CAR, SIGNALS[it.number])
        }
        val passenger = PassengerItem.entries.map { CatalogItem(it.number, it.points, task(it.number), it.title, Judge.PASSENGER, null) }
        (car + passenger + NOT_JUDGED).sortedBy { it.number }
    }

    /** 실격 사유(별표 26 제2호 나목 11개). 보호구역·제한속도는 코스 자료가 있으면 차가, 없으면 동승자가 봅니다. */
    val disqualifications: List<CatalogDq> by lazy {
        val byCourse = setOf(PassengerDisqualification.ZONE_SPEED, PassengerDisqualification.SPEED_10, PassengerDisqualification.REPEATED_FAIL)
        listOf(
            CatalogDq(Disqualification.SEAT_BELT.title, Judge.CAR, "안전띠 경고등"),
            // 실격 1): 급브레이크·급출발 횟수는 차가 세고, 출발불능·엔진정지 등은 동승자가 봅니다(11/8)
            CatalogDq(PassengerDisqualification.REPEATED_FAIL.title, Judge.CAR, "급브레이크(21)·급출발(9) 합계 3회는 차가 셈(근사). 출발불능·엔진정지 등은 동승자"),
        ) +
            listOf(
                CatalogDq(Disqualification.SCHOOL_ZONE_SPEED.title, Judge.COURSE, "코스의 보호구역 + 속도(코스 자료가 없으면 동승자)"),
                CatalogDq(Disqualification.SPEED_OVER.title, Judge.COURSE, "코스의 제한속도 구간 + 속도(코스 자료가 없으면 동승자)"),
            ) +
            PassengerDisqualification.entries.filter { it !in byCourse }.map { CatalogDq(it.title, Judge.PASSENGER, null) }
    }

    fun count(judge: Judge): Int = items.count { it.judge == judge }
}
