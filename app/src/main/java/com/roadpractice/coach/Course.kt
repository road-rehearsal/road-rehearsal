package com.roadpractice.coach

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 코스 지점 종류. 감점 항목이 있는 것은 그 항목으로 판정합니다. */
enum class CheckpointType(val item: ExamItem?, val ahead: String) {
    SLOW(ExamItem.SLOW_DOWN, "앞에 서행 구간이 있어요"),
    STOP(ExamItem.STOP_SIGN, "앞에서 완전히 멈춰야 해요"),
    CROSSWALK(ExamItem.CROSSWALK_STOP, "앞 횡단보도에서 멈춰요"),
    UTURN(null, "유턴해요"),
    LEFT(null, "좌회전해요"),
    RIGHT(null, "우회전해요"),

    /** 지정 제한속도 구간(limitKmh 필요). 10km/h 넘게 초과하면 실격(별표 26 실격 사유). */
    SPEED(null, "제한속도 구간이에요"),

    /** 어린이보호구역(limitKmh, 없으면 30). 넘으면 실격(별표 26 실격 사유: 보호구역 최고 속도 초과). */
    SCHOOL(null, "앞에 어린이보호구역이 있어요. 천천히 가요"),
    ;

    /** 길 안내(회전·유턴). 두 모드 모두 말합니다. */
    val isTurn: Boolean get() = this == LEFT || this == RIGHT || this == UTURN

    /** 앞뒤 반경 안 전체를 보는 구간 지점. */
    val isZone: Boolean get() = this == SLOW || this == SPEED || this == SCHOOL
}

/**
 * 코스 지점. 위치는 좌표가 아니라 코스 출발점에서 잰 거리(atM)로 둡니다.
 * 같은 길을 오가는 코스에서 한 지점이 여러 번 판정되지 않게 하려는 것입니다.
 * 구간 지점(서행·제한속도·보호구역)은 atM±radiusM 전체, 멈춤 지점은 atM-radiusM부터 atM까지 봅니다.
 */
data class Checkpoint(
    val type: CheckpointType,
    val name: String,
    val atM: Double,
    val radiusM: Double,
    val limitKmh: Float? = null,
)

data class Course(
    val id: String,
    val name: String,
    val lat: DoubleArray,
    val lng: DoubleArray,
    val checkpoints: List<Checkpoint>,
) {
    /** 출발점부터 각 점까지의 누적 거리(m). */
    val cumM: DoubleArray = DoubleArray(lat.size).also { cum ->
        for (k in 1 until lat.size) cum[k] = cum[k - 1] + distanceMeters(lat[k - 1], lng[k - 1], lat[k], lng[k])
    }
    val lengthM: Double get() = cumM.last()

    override fun equals(other: Any?) = other is Course && other.id == id
    override fun hashCode() = id.hashCode()
}

/** 두 좌표 사이 거리(m). 단위 테스트에서도 쓰도록 안드로이드 API 없이 계산합니다. */
fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6_371_008.8
    val p1 = Math.toRadians(aLat)
    val p2 = Math.toRadians(bLat)
    val dp = p2 - p1
    val dl = Math.toRadians(bLng - aLng)
    val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * r * asin(sqrt(h))
}

/** 코스 판정 기준. 별표 26에 서행 수치가 없어 서행 속도는 연습용 제안값입니다. */
data class CourseThresholds(
    val offRouteM: Double = 60.0,
    val offRouteCount: Int = 2,
    val windowAhead: Int = 15,
    val doneFraction: Double = 0.98,
    val aheadNoticeM: Double = 80.0,
    /** 회전 안내 거리(m). 먼 것부터. 마지막 값 이하에서는 "곧"이라고 말합니다(작업 지시 6단계 제안값). */
    val turnNoticeM: List<Int> = listOf(300, 100, 30),
    val slowKmh: Float = 20f,
    val stopKmh: Float = 1f,
    /** 보호구역 제한속도 값이 없을 때(도로교통법상 보통 30km/h). */
    val schoolKmh: Float = 30f,
    /** 지정 제한속도에서 이만큼 넘으면 실격(별표 26: 10km/h 초과). */
    val speedOverKmh: Float = 10f,
    /** 출발점에서 이 거리 안에 들어와야 코스를 따라가기 시작합니다. */
    val startRadiusM: Double = 100.0,
    /** 한 번에 이보다 많이 건너뛰면 위치 오류로 보고 그 사이 지점은 판정하지 않습니다. */
    val maxJumpM: Double = 150.0,
)

sealed interface CourseEvent {
    /** 지점 예고. 회전 안내는 distanceM(300·100·30)마다 한 번씩 옵니다. */
    data class Ahead(val checkpoint: Checkpoint, val distanceM: Int = 0) : CourseEvent
    data class Passed(val checkpoint: Checkpoint, val ok: Boolean, val speedKmh: Float) : CourseEvent

    /** 제한속도·보호구역을 넘었음(구간을 빠져나갈 때 한 번). */
    data class ZoneOver(val checkpoint: Checkpoint, val maxKmh: Float, val limitKmh: Float) : CourseEvent
    data class Progress(val fraction: Double) : CourseEvent
    data object OffRoute : CourseEvent
    data object BackOnRoute : CourseEvent
    data object Done : CourseEvent

    /** 출발점에서 멀리 떨어져 아직 코스를 따라가지 않습니다(한 번만 알림). */
    data object NotAtStart : CourseEvent
}

/** 회전 안내 문장. */
fun turnSentence(type: CheckpointType, distanceM: Int, nearM: Int): String =
    if (distanceM <= nearM) "곧 ${type.ahead}" else "${distanceM}미터 앞에서 ${type.ahead}"

/**
 * 위치로 코스 진행을 따라갑니다. 지금 위치에서 앞쪽 몇 개 점만 찾으므로 되돌아오는 길과 섞이지 않습니다.
 * 속도는 위치가 아니라 차량 속도 신호를 씁니다.
 */
class CourseTracker(val course: Course, private val t: CourseThresholds = CourseThresholds()) {
    private var idx = 0
    private var offCount = 0
    private var off = false
    private var lastTenth = -1
    private val announced = HashSet<Int>()
    private val turnLevels = HashMap<Int, Int>()
    private val judged = HashSet<Int>()

    /** 지점별 구간 안 최저·최고 속도. 구간이 겹쳐도(제한속도 구간 안 횡단보도) 따로 셉니다. */
    private val zones = HashMap<Int, FloatArray>()
    private var onCourse = false
    private var locked = false
    private var toldNotAtStart = false
    var alongM = 0.0
        private set
    var done = false
        private set

    val fraction: Double get() = alongM / course.lengthM

    fun onState(s: VehicleState): List<CourseEvent> {
        val lat = s.latitude ?: return emptyList()
        val lng = s.longitude ?: return emptyList()
        if (done) return emptyList()
        val out = ArrayList<CourseEvent>()
        if (!onCourse) {
            if (distanceMeters(lat, lng, course.lat[0], course.lng[0]) > t.startRadiusM) {
                if (!toldNotAtStart) {
                    toldNotAtStart = true
                    out += CourseEvent.NotAtStart
                }
                return out
            }
            onCourse = true
        }
        var best = idx
        var bestD = Double.MAX_VALUE
        val end = minOf(course.lat.size - 1, idx + t.windowAhead)
        for (k in idx..end) {
            val d = distanceMeters(lat, lng, course.lat[k], course.lng[k])
            if (d < bestD) {
                bestD = d
                best = k
            }
        }
        if (bestD <= t.offRouteM) locked = true
        // 출발점 반경(100m) 안에 들어왔어도 아직 코스 선에 닿지 않았으면 이탈로 보지 않습니다
        // (11/7 실제 기록 재생: 옆길에서 다가오며 "코스를 벗어났어요"가 잘못 나옴).
        if (bestD > t.offRouteM && !locked) return out
        if (bestD > t.offRouteM) {
            offCount++
            if (offCount >= t.offRouteCount && !off) {
                off = true
                out += CourseEvent.OffRoute
            }
            return out
        }
        if (off) out += CourseEvent.BackOnRoute
        off = false
        offCount = 0
        val prevAlong = alongM
        idx = best
        alongM = maxOf(alongM, course.cumM[idx])
        val kmh = s.speedKmh ?: 0f
        if (alongM - prevAlong > t.maxJumpM) {
            // 건너뛴 구간의 지점은 판정·안내하지 않고 넘어갑니다.
            course.checkpoints.forEachIndexed { i, cp ->
                if (cp.atM < alongM) {
                    judged += i
                    announced += i
                    turnLevels[i] = 0
                }
            }
            zones.clear()
        }
        checkpoints(prevAlong, kmh, out)
        val tenth = (fraction * 10).toInt()
        if (tenth != lastTenth) {
            lastTenth = tenth
            out += CourseEvent.Progress(fraction)
        }
        if (fraction >= t.doneFraction) {
            done = true
            out += CourseEvent.Done
        }
        return out
    }

    private fun checkpoints(prevAlong: Double, kmh: Float, out: MutableList<CourseEvent>) {
        course.checkpoints.forEachIndexed { i, cp ->
            if (cp.type.isTurn) {
                announceTurn(i, cp, out)
                return@forEachIndexed
            }
            val start = cp.atM - cp.radiusM
            if (i !in announced && alongM >= start - t.aheadNoticeM && alongM < cp.atM) {
                announced += i
                out += CourseEvent.Ahead(cp, (start - alongM).coerceAtLeast(0.0).toInt())
            }
            if (i in judged) return@forEachIndexed
            // 멈춤 지점은 지점 앞쪽만, 구간 지점은 앞뒤를 봅니다.
            val end = if (cp.type.isZone) cp.atM + cp.radiusM else cp.atM
            val inside = alongM in start..end
            val crossed = prevAlong < start && alongM > end
            if (inside || crossed) {
                val z = zones.getOrPut(i) { floatArrayOf(Float.MAX_VALUE, 0f) }
                z[0] = minOf(z[0], kmh)
                z[1] = maxOf(z[1], kmh)
            }
            val z = zones[i]
            if (z != null && alongM > end) {
                judged += i
                zones.remove(i)
                judge(cp, z[0], z[1])?.let { out += it }
            }
        }
    }

    private fun judge(cp: Checkpoint, min: Float, max: Float): CourseEvent? = when (cp.type) {
        CheckpointType.SLOW -> CourseEvent.Passed(cp, max <= (cp.limitKmh ?: t.slowKmh), max)
        CheckpointType.STOP, CheckpointType.CROSSWALK -> CourseEvent.Passed(cp, min < t.stopKmh, min)
        CheckpointType.SCHOOL -> {
            val limit = cp.limitKmh ?: t.schoolKmh
            if (max > limit) CourseEvent.ZoneOver(cp, max, limit) else null
        }
        CheckpointType.SPEED -> {
            val limit = cp.limitKmh
            if (limit != null && max > limit + t.speedOverKmh) CourseEvent.ZoneOver(cp, max, limit) else null
        }
        else -> null
    }

    /**
     * 회전까지 남은 거리가 안내 거리(300·100·30)를 지날 때마다 한 번씩. 이미 가까우면 먼 안내는 건너뜁니다.
     * 앞 회전을 다 돌기 전에는 다음 회전을 말하지 않습니다(11/7 재생: "곧 우회전" 직후 다음 회전 "300미터"가 겹쳐 헷갈림).
     */
    private fun announceTurn(i: Int, cp: Checkpoint, out: MutableList<CourseEvent>) {
        val remaining = cp.atM - alongM
        if (remaining <= 0) return
        val earlierPending = course.checkpoints.any { o -> o.type.isTurn && o.atM < cp.atM && o.atM > alongM }
        if (earlierPending) return
        val level = t.turnNoticeM.filter { remaining <= it }.minOrNull() ?: return
        val last = turnLevels[i]
        if (last != null && last <= level) return
        turnLevels[i] = level
        out += CourseEvent.Ahead(cp, level)
    }
}
