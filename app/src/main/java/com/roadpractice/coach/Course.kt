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
    UTURN(null, "앞에서 유턴해요"),
}

/**
 * 코스 지점. 위치는 좌표가 아니라 코스 출발점에서 잰 거리(atM)로 둡니다.
 * 같은 길을 오가는 코스에서 한 지점이 여러 번 판정되지 않게 하려는 것입니다.
 */
data class Checkpoint(val type: CheckpointType, val name: String, val atM: Double, val radiusM: Double)

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
    val slowKmh: Float = 20f,
    val stopKmh: Float = 1f,
    /** 출발점에서 이 거리 안에 들어와야 코스를 따라가기 시작합니다. */
    val startRadiusM: Double = 100.0,
    /** 한 번에 이보다 많이 건너뛰면 위치 오류로 보고 그 사이 지점은 판정하지 않습니다. */
    val maxJumpM: Double = 150.0,
)

sealed interface CourseEvent {
    data class Ahead(val checkpoint: Checkpoint) : CourseEvent
    data class Passed(val checkpoint: Checkpoint, val ok: Boolean, val speedKmh: Float) : CourseEvent
    data class Progress(val fraction: Double) : CourseEvent
    data object OffRoute : CourseEvent
    data object BackOnRoute : CourseEvent
    data object Done : CourseEvent
    /** 출발점에서 멀리 떨어져 아직 코스를 따라가지 않습니다(한 번만 알림). */
    data object NotAtStart : CourseEvent
}

/**
 * 위치로 코스 진행을 따라갑니다. 지금 위치에서 앞쪽 몇 개 점만 찾으므로 되돌아오는 길과 섞이지 않습니다.
 * 속도는 위치가 아니라 차량 속도 신호를 씁니다.
 */
class CourseTracker(val course: Course, private val t: CourseThresholds = CourseThresholds()) {
    private var idx = 0
    private var offCount = 0
    private var off = false
    private var lastTenth = -1
    private var announced = HashSet<Int>()
    private var judged = HashSet<Int>()
    private var zoneIndex = -1
    private var zoneMin = Float.MAX_VALUE
    private var zoneMax = 0f
    private var onCourse = false
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
            // 건너뛴 구간의 지점은 판정하지 않고 넘어갑니다.
            course.checkpoints.forEachIndexed { i, cp -> if (cp.atM < alongM) { judged += i; announced += i } }
            zoneIndex = -1
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
            if (i !in announced && alongM >= cp.atM - cp.radiusM - t.aheadNoticeM && alongM < cp.atM) {
                announced += i
                out += CourseEvent.Ahead(cp)
            }
            val item = cp.type.item ?: return@forEachIndexed
            if (i in judged) return@forEachIndexed
            // 멈춤 지점은 지점 앞쪽만, 서행 구간은 앞뒤를 봅니다.
            val start = cp.atM - cp.radiusM
            val end = if (cp.type == CheckpointType.SLOW) cp.atM + cp.radiusM else cp.atM
            val inside = alongM in start..end
            val crossed = prevAlong < start && alongM > end
            if (inside || crossed) {
                if (zoneIndex != i) {
                    zoneIndex = i
                    zoneMin = Float.MAX_VALUE
                    zoneMax = 0f
                }
                zoneMin = minOf(zoneMin, kmh)
                zoneMax = maxOf(zoneMax, kmh)
            }
            if (alongM > end && zoneIndex == i || crossed) {
                judged += i
                zoneIndex = -1
                val ok = if (item == ExamItem.SLOW_DOWN) zoneMax <= t.slowKmh else zoneMin < t.stopKmh
                out += CourseEvent.Passed(cp, ok, if (item == ExamItem.SLOW_DOWN) zoneMax else zoneMin)
            }
        }
    }
}
