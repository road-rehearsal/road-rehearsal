package com.roadpractice.coach

/** 기어. SDK 값을 앱 판정에 필요한 만큼만 옮긴 것입니다. 값을 못 받았으면 UNKNOWN입니다. */
enum class Gear { UNKNOWN, PARK, REVERSE, NEUTRAL, DRIVE }

/** 방향지시등. */
enum class TurnSignal { UNKNOWN, OFF, LEFT, RIGHT }

/**
 * 한 시점의 차량 상태. 판정 코드(ExamRules)는 이 값만 받으므로 차량 없이 시험할 수 있습니다.
 * null은 "아직 값을 받지 못함"입니다. 값이 없으면 감점하지 않습니다.
 */
data class VehicleState(
    val timeMs: Long = 0L,
    val speedKmh: Float? = null,
    val distanceM: Double = 0.0,
    val gear: Gear = Gear.UNKNOWN,
    val turnSignal: TurnSignal = TurnSignal.UNKNOWN,
    /** 절댓값. 콘솔과 부호가 반대라 크기만 씁니다. */
    val steeringAbsDeg: Float? = null,
    val braking: Boolean? = null,
    val openDoors: Set<String> = emptySet(),
    val parkingBrakeOn: Boolean? = null,
    val seatBeltWarningOn: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val isMoving: Boolean get() = (speedKmh ?: 0f) >= MOVING_KMH

    /** 화면과 로그용 한 줄. */
    fun summary(): String =
        "speed=${speedKmh ?: "-"} dist=${distanceM.toInt()}m gear=$gear signal=$turnSignal " +
            "steer=${steeringAbsDeg ?: "-"} brake=${braking ?: "-"} doors=$openDoors " +
            "pbrake=${parkingBrakeOn ?: "-"} belt=${seatBeltWarningOn ?: "-"} " +
            "loc=${latitude ?: "-"},${longitude ?: "-"}"

    companion object {
        /** 시험값입니다. 움직임 판단 기준이며 공식 기준이 아닙니다. */
        const val MOVING_KMH = 1f
    }
}
