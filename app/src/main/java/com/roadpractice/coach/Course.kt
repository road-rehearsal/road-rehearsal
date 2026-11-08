package com.roadpractice.coach

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 코스 지점 종류. 감점 항목이 있는 것은 그 항목으로 판정합니다. */
enum class CheckpointType(val item: ExamItem?, val ahead: String) {
    SLOW(ExamItem.SLOW_DOWN, "앞에 서행 구간이 있어요"),
    STOP(ExamItem.STOP_SIGN, "앞에서 완전히 멈춰야 해요"),
    /** 횡단보도: 안내만 하고 감점하지 않습니다. 43번은 동승자 체크(11/7 사용자 결정). 지날 때 속도는 결과표에 참고로 남깁니다. */
    CROSSWALK(null, "도는 곳에 횡단보도가 있어요. 보행자를 확인해요"),
    UTURN(null, "유턴해요"),
    LEFT(null, "좌회전해요"),
    RIGHT(null, "우회전해요"),

    /** 지정 제한속도 구간(limitKmh 필요). 10km/h 넘게 초과하면 실격(별표 26 실격 사유). */
    SPEED(null, "제한속도 구간이에요"),

    /** 어린이보호구역(limitKmh, 없으면 30). 넘으면 실격(별표 26 실격 사유: 보호구역 최고 속도 초과). */
    SCHOOL(null, "앞에 어린이보호구역이 있어요. 천천히 가요"),

    /** 과속방지턱: 안내만 하고 감점하지 않습니다(별표 26 항목 아님). 지날 때 최고 속도를 결과표에 참고로 남깁니다(11/8 2차 영상). */
    BUMP(null, "앞에 과속방지턱이 있어요. 속도를 줄여요"),
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
    /** 먼 회전 안내 거리(m). 이 안에 들어오면 화면에 회전 화살표를 띄우고 한 번 말합니다. */
    val farNoticeM: Int = 300,
    /**
     * 가까운 회전 안내 거리 = 속도(km/h) × 2.222 + 37.1 m를 이 범위로 자름(11/8).
     * 근거: FHWA-RD-98-057 5장 "Timing of Auditory Navigation Information"의 최대 거리 식(늦는 것보다 조금 이른 것이 낫다).
     * 30km/h면 약 100m, 60km/h면 약 170m. 4_개발/안내_레퍼런스.md F3.
     */
    val nearMinM: Int = 80,
    val nearMaxM: Int = 200,
    /** "지금 ~해요" 안내: 회전까지 이 초 안이거나 nowMinM 안(11/8, FHWA: 급한 안내는 짧게). */
    val nowSeconds: Double = 3.0,
    val nowMinM: Int = 30,
    val nowMaxM: Int = 60,
    /** 다음 회전이 이 초 안이면 앞 회전과 묶어 한 번에 말합니다(FHWA 5장 "less than 10 seconds apart"). */
    val stackSeconds: Double = 10.0,
    /** 속도를 모를 때(정차 등) 묶음·안내 거리 계산에 쓰는 속도. */
    val assumeKmh: Float = 20f,
    /** 회전 이 거리 안에서 이 시간 넘게 섰다가 다시 출발하면 한 번 더 안내합니다(11/8 2차 기록: 회전 9개 중 5개가 직전 신호 대기). */
    val againWithinM: Int = 150,
    val againStopMs: Long = 10_000,
    /** 좌·우회전 서행 판정 구간: 회전 지점 앞 turnSlowBeforeM ~ 뒤 turnSlowAfterM의 최저 속도가 slowKmh 이하여야 함(11/8). */
    val turnSlowBeforeM: Double = 20.0,
    val turnSlowAfterM: Double = 5.0,
    /** 횡단보도 예고는 좌·우회전 지점 앞뒤 이 거리 안의 횡단보도만(11/8 사용자 결정). */
    val crosswalkNearTurnM: Double = 40.0,
    /** 다시 출발할 때 회전이 이 거리 안이면 "지금"으로 바로 말합니다. */
    val againAsNowM: Int = 45,
    val stoppedKmh: Float = 3f,
    val movingKmh: Float = 8f,
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
    /**
     * 회전 지점 근처에서 코스 선과 이만큼 떨어지면 회전을 놓친 것으로 봅니다(2번 연속).
     * 일반 이탈(60m)보다 작게 둡니다. 11/7 재생에서 50m 이탈은 일반 기준으로 못 잡았습니다(제안값).
     */
    val missTurnM: Double = 30.0,
    /** 회전 지점 앞뒤 이 거리 안에 있을 때만 회전 놓침을 봅니다. */
    val missTurnWindowM: Double = 40.0,
)

sealed interface CourseEvent {
    /**
     * 지점 예고. 회전은 단계(먼·가까운·지금, 정차 뒤 다시)마다 한 번씩 옵니다.
     * then: 바로 이어지는 다음 회전(묶음 안내). distanceM: 남은 거리(10m 단위).
     */
    data class Ahead(
        val checkpoint: Checkpoint,
        val distanceM: Int = 0,
        val stage: TurnStage = TurnStage.FAR,
        val then: CheckpointType? = null,
    ) : CourseEvent
    data class Passed(val checkpoint: Checkpoint, val ok: Boolean, val speedKmh: Float) : CourseEvent

    /** 제한속도·보호구역을 넘었음(구간을 빠져나갈 때 한 번). 실격 기록은 이것으로 합니다. */
    data class ZoneOver(val checkpoint: Checkpoint, val maxKmh: Float, val limitKmh: Float) : CourseEvent

    /**
     * 구간 안에서 넘는 순간(11/8 사용자 결정: 넘는 순간 말함). level 1 = 제한속도 넘음, 2 = 실격 기준 넘음
     * (보호구역은 넘으면 바로 2, 지정 제한속도는 10km/h 넘게). 구간마다 단계별로 한 번씩.
     */
    data class SpeedOver(val checkpoint: Checkpoint, val kmh: Float, val limitKmh: Float, val level: Int) : CourseEvent

    /** 좌·우회전 지점에서 서행하지 않음(별표 26 37번 1) "좌회전 또는 우회전이 필요한 도로"). minKmh: 지점 앞뒤 최저 속도. */
    data class TurnNotSlow(val checkpoint: Checkpoint, val minKmh: Float, val limitKmh: Float) : CourseEvent

    /** 돌아야 할 곳을 지나쳤음(회전 지점 근처에서 코스 선과 멀어짐). */
    data class MissedTurn(val checkpoint: Checkpoint) : CourseEvent
    data class Progress(val fraction: Double) : CourseEvent
    data object OffRoute : CourseEvent
    data object BackOnRoute : CourseEvent
    data object Done : CourseEvent

    /** 출발점에서 멀리 떨어져 아직 코스를 따라가지 않습니다(한 번만 알림). */
    data object NotAtStart : CourseEvent
}

/** 코스 지점 하나의 결과. 결과표의 "코스 지점" 줄에 씁니다. ok가 null이면 판정 없이 안내만 한 지점입니다. */
data class PointResult(val type: CheckpointType, val name: String, val ok: Boolean?, val detail: String) {
    /** 저장용 한 줄(구분자 |). 이름·설명에 |가 없게 만듭니다. */
    fun encode(): String = listOf(type.name, name, ok?.let { if (it) "1" else "0" } ?: "-", detail).joinToString("|") { it.replace("|", "/") }

    companion object {
        fun decode(s: String): PointResult? = runCatching {
            val p = s.split("|")
            PointResult(CheckpointType.valueOf(p[0]), p[1], when (p[2]) { "1" -> true; "0" -> false; else -> null }, p.getOrElse(3) { "" })
        }.getOrNull()
    }
}

/** 회전 안내 단계. AGAIN은 회전 앞에서 오래 섰다가 출발할 때 한 번 더 하는 안내. */
enum class TurnStage { FAR, NEAR, NOW, AGAIN }

/** 화면에 띄울 다음 회전. then이 있으면 바로 이어지는 회전(화살표 두 개). */
data class NextTurn(val type: CheckpointType, val remainingM: Int, val then: CheckpointType? = null)

/** 회전 이름("좌회전" 등). */
val CheckpointType.turnName: String get() = ahead.removeSuffix("해요")

/** 회전 안내 문장. NOW는 짧게 "지금 ~해요", 묶음이면 "~ 뒤 바로 ~해요". */
fun turnSentence(type: CheckpointType, distanceM: Int, stage: TurnStage, then: CheckpointType? = null): String {
    val what = if (then != null) "${type.turnName} 뒤 바로 ${then.ahead}" else type.ahead
    return if (stage == TurnStage.NOW) "지금 $what" else "${distanceM}미터 앞에서 $what"
}

/** 속도에 따른 가까운 안내 거리(m, 10m 단위). */
fun nearNoticeM(kmh: Float, t: CourseThresholds): Int =
    ((2.222 * kmh + 37.1) / 10).toInt().times(10).coerceIn(t.nearMinM, t.nearMaxM)

/** 속도에 따른 "지금" 안내 거리(m). */
fun nowNoticeM(kmh: Float, t: CourseThresholds): Int =
    (kmh / 3.6 * t.nowSeconds).toInt().coerceIn(t.nowMinM, t.nowMaxM)

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

    /** 회전별로 마지막에 말한 단계(FAR·NEAR·NOW). */
    private val turnStages = HashMap<Int, TurnStage>()

    /** 앞 회전과 묶어 이미 말한 회전: 먼·가까운 안내 없이 "지금"만 합니다. */
    private val stacked = HashSet<Int>()
    private val againDone = HashSet<Int>()

    /** 좌·우회전 지점 앞뒤 구간의 최저 속도(서행 판정). */
    private val turnMin = HashMap<Int, Float>()

    /** 구간별로 이미 알린 속도 초과 단계(1·2). */
    private val overLevels = HashMap<Int, Int>()
    /** 넘는 순간 이미 실격을 낸 구간(나갈 때 다시 내지 않음). */
    private val zoneDq = HashSet<Int>()
    private var stoppedSinceMs = -1L
    private var lastKmh = 0f
    private val judged = HashSet<Int>()
    private val turnHeard = HashMap<Int, ArrayList<Int>>()
    private val results = ArrayList<PointResult>()
    private var missCount = 0

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

    /**
     * 시연용 구간 재생(11/8): 코스 중간에서 시작할 때 기록상 거리(m). 있으면 출발점 대신 지금 위치 근처 코스 점에서 시작하고,
     * 같은 길을 오가는 코스에서는 이 거리와 가장 가까운 쪽(같은 방향) 점을 고릅니다. 앞쪽 지점은 지난 것으로 칩니다.
     */
    var midStartHintM: Double? = null

    private fun startMid(lat: Double, lng: Double, hint: Double): Boolean {
        val near = course.lat.indices.filter { distanceMeters(lat, lng, course.lat[it], course.lng[it]) <= MID_START_M }
        // 힌트(기록 속도로 쌓은 거리)는 코스 거리와 어긋남(코스 앞 자름·GPS와 속도 적분 차이). 그래서 힌트로는
        // 오가는 길 중 어느 쪽인지만 고르고, 그쪽 점들 가운데 지금 위치에 가장 가까운 점에서 시작합니다
        // (11/8 1095초 구간: 힌트로만 고르면 바로 앞 좌회전을 지난 점이 골라져 "지점을 지났어요"가 나옴).
        val pass = near.minByOrNull { kotlin.math.abs(course.cumM[it] - hint) } ?: return false
        val k = near.filter { kotlin.math.abs(course.cumM[it] - course.cumM[pass]) <= 2 * MID_START_M }
            .minByOrNull { distanceMeters(lat, lng, course.lat[it], course.lng[it]) } ?: pass
        onCourse = true
        locked = true
        idx = k
        alongM = course.cumM[k]
        course.checkpoints.forEachIndexed { i, cp ->
            // 구간 지점(제한속도·보호구역 등)은 끝까지 지났을 때만 지난 것으로 칩니다(시작 위치가 구간 안일 수 있음)
            val end = if (cp.type.isZone) cp.atM + cp.radiusM else cp.atM
            if (end < alongM) {
                judged += i
                announced += i
                turnStages[i] = TurnStage.NOW
            } else if (cp.type.isZone && cp.atM - cp.radiusM < alongM) {
                announced += i // 이미 구간 안: 예고는 하지 않고 판정·표지만
            }
        }
        return true
    }

    fun onState(s: VehicleState): List<CourseEvent> {
        val lat = s.latitude ?: return emptyList()
        val lng = s.longitude ?: return emptyList()
        if (done) return emptyList()
        val out = ArrayList<CourseEvent>()
        val hint = midStartHintM
        if (!onCourse && hint != null && startMid(lat, lng, hint)) {
            midStartHintM = null
        }
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
        if (checkMissedTurn(bestD, out)) return out
        if (bestD > t.offRouteM) {
            offCount++
            if (offCount >= t.offRouteCount && !off) {
                off = true
                out += CourseEvent.OffRoute
            }
            return out
        }
        // 벗어난 뒤에는 코스 선 30m 안으로 들어와야 돌아온 것으로 봅니다(60m로 보면 옆길에서 너무 일찍 "돌아왔어요").
        if (off && bestD > t.missTurnM) return out
        if (off) out += CourseEvent.BackOnRoute
        off = false
        offCount = 0
        val prevAlong = alongM
        idx = best
        alongM = maxOf(alongM, course.cumM[idx])
        // 채점에는 받은 속도만 씁니다. 속도가 없으면 안내 거리 계산에만 마지막 값을 쓰고 최저·최고 속도에는 넣지 않습니다
        // (11/8 Codex 점검 3: 속도 없음을 0km/h로 바꿔 일시정지·서행을 통과로 판정했음)
        val real = s.speedKmh
        val kmh = real ?: lastKmh
        if (real != null) lastKmh = real
        againAfterStop(s.timeMs, kmh, out)
        if (alongM - prevAlong > t.maxJumpM) {
            // 건너뛴 구간의 지점은 판정·안내하지 않고 넘어갑니다.
            course.checkpoints.forEachIndexed { i, cp ->
                if (cp.atM < alongM) {
                    judged += i
                    announced += i
                    turnStages[i] = TurnStage.NOW
                }
            }
            zones.clear()
        }
        checkpoints(prevAlong, kmh, real, out)
        val tenth = (fraction * 10).toInt()
        if (tenth != lastTenth) {
            lastTenth = tenth
            out += CourseEvent.Progress(fraction)
        }
        // 끝 근처에 아직 돌지 않은 회전이 있으면 그 회전을 지날 때까지 완주로 보지 않습니다
        // (11/8 2차 재생: 마지막 연속 회전 직전에 "코스를 다 돌았어요"가 나옴).
        val turnPending = course.checkpoints.withIndex().any { (i, c) -> c.type.isTurn && i !in judged }
        if (fraction >= t.doneFraction && (!turnPending || alongM >= course.lengthM - TURN_DONE_M)) {
            done = true
            // 코스 끝까지 이어진 구간(예: 보호구역이 도착 근처까지)은 빠져나가기 전에 끝나므로 여기서 판정합니다(11/7 재생에서 찾음).
            settleOpenZones(out)
            out += CourseEvent.Done
        }
        return out
    }

    /**
     * 주행을 끝낼 때(운전석 문) 아직 안에 있는 구간을 그때까지의 속도로 판정합니다. 이미 끝났으면 아무것도 하지 않습니다.
     * (11/8 Codex 점검 1: 보호구역 안에서 끝내면 넘는 순간 "시험이면 실격" 안내만 있고 실격이 결과에 없었음)
     */
    fun finish(): List<CourseEvent> {
        if (done) return emptyList()
        val out = ArrayList<CourseEvent>()
        settleOpenZones(out)
        done = true
        return out
    }

    private fun settleOpenZones(out: MutableList<CourseEvent>) {
        course.checkpoints.forEachIndexed { i, cp ->
            val z = zones[i]
            if (z != null && i !in judged) {
                judged += i
                val e = judge(cp, z[0], z[1])
                e?.takeUnless { it is CourseEvent.ZoneOver && i in zoneDq }?.let { out += it }
                if (cp.type.isZone) {
                    val limit = cp.limitKmh ?: if (cp.type == CheckpointType.SCHOOL) t.schoolKmh else t.slowKmh
                    results += PointResult(cp.type, cp.name, e == null || (e as? CourseEvent.Passed)?.ok == true, "최고 ${z[1].toInt()}km/h (제한 ${limit.toInt()})")
                }
            }
        }
        zones.clear()
    }

    /**
     * 회전 놓침: 아직 지나지 않은 회전 지점 앞뒤(40m) 안에서 코스 선과 30m 넘게 2번 연속 떨어지면
     * 그 회전을 놓친 것으로 봅니다. 놓친 뒤에는 일반 이탈 상태로 두어 돌아오면 "코스로 돌아왔어요"를 냅니다.
     * @return 이번 위치를 여기서 다 처리했으면 true
     */
    private fun checkMissedTurn(bestD: Double, out: MutableList<CourseEvent>): Boolean {
        if (bestD <= t.missTurnM) {
            missCount = 0
            return false
        }
        val i = course.checkpoints.indices.firstOrNull { j ->
            val cp = course.checkpoints[j]
            cp.type.isTurn && j !in judged && kotlin.math.abs(cp.atM - alongM) <= t.missTurnWindowM
        } ?: return false
        missCount++
        if (missCount < t.offRouteCount) return true
        val cp = course.checkpoints[i]
        judged += i
        missCount = 0
        off = true
        results += PointResult(cp.type, cp.name, false, "지나침")
        out += CourseEvent.MissedTurn(cp)
        return true
    }

    /** 지금 보여 줄 다음 회전(먼 안내 거리 안). 바로 이어지는 회전이 있으면 then에 넣습니다. 없으면 null. */
    fun nextTurn(): NextTurn? {
        if (!onCourse || done) return null
        val (i, cp) = course.checkpoints.withIndex()
            .filter { (i, c) -> c.type.isTurn && i !in judged && c.atM > alongM }
            .minByOrNull { it.value.atM } ?: return null
        val remaining = (cp.atM - alongM).toInt()
        // 음성에서 이미 묶어 말한 회전은 화면에도 계속 두 개로 보여 줍니다(속도가 줄어 다시 계산하면 풀리는 일 방지, 11/8 에뮬레이터)
        val then = course.checkpoints.withIndex()
            .filter { (j, c) -> j != i && c.type.isTurn && c.atM > cp.atM }
            .minByOrNull { it.value.atM }
            ?.takeIf { it.index in stacked } ?: stackedNext(i, lastKmh)
        return if (remaining <= t.farNoticeM) NextTurn(cp.type, remaining, then?.value?.type) else null
    }

    /** i번 회전 바로 뒤(stackSeconds 안)에 이어지는 다음 회전. */
    private fun stackedNext(i: Int, kmh: Float): IndexedValue<Checkpoint>? {
        val cp = course.checkpoints[i]
        val next = course.checkpoints.withIndex()
            .filter { (j, c) -> j != i && c.type.isTurn && c.atM > cp.atM }
            .minByOrNull { it.value.atM } ?: return null
        val mps = maxOf(kmh, t.assumeKmh) / 3.6
        return next.takeIf { it.value.atM - cp.atM <= mps * t.stackSeconds }
    }

    /**
     * 회전 앞에서 againStopMs 넘게 섰다가(신호 대기) 다시 출발하면, 이미 안내한 회전을 한 번 더 말합니다.
     * 차량 속도 신호로 정차·출발을 봅니다(위치보다 빠르고 정확).
     */
    private fun againAfterStop(nowMs: Long, kmh: Float, out: MutableList<CourseEvent>) {
        if (kmh < t.stoppedKmh) {
            if (stoppedSinceMs < 0) stoppedSinceMs = nowMs
            return
        }
        val since = stoppedSinceMs
        if (kmh < t.movingKmh) return
        stoppedSinceMs = -1
        if (since < 0 || nowMs - since < t.againStopMs) return
        val (i, cp) = course.checkpoints.withIndex()
            .filter { (j, c) -> c.type.isTurn && j !in judged && c.atM > alongM }
            .minByOrNull { it.value.atM } ?: return
        val remaining = cp.atM - alongM
        if (remaining > t.againWithinM || i in againDone || turnStages[i] == null) return
        againDone += i
        // 곧 돌 거리면 다시 안내 대신 바로 "지금"(11/8 재생: "30미터 앞" 직후 "지금"이 겹침)
        val now = remaining <= maxOf(nowNoticeM(kmh, t), t.againAsNowM)
        if (now) turnStages[i] = TurnStage.NOW
        val stage = if (now) TurnStage.NOW else TurnStage.AGAIN
        val then = stackedNext(i, kmh)
        then?.let { stacked += it.index }
        out += CourseEvent.Ahead(cp, (remaining / 10).toInt() * 10, stage, then?.value?.type)
    }

    /**
     * 직진 구간 안내(11/7 사용자 요청): 300m 안에 회전이 없으면, 다음 회전까지(없으면 도착까지) 남은 거리와 그 회전 종류.
     * 코스를 벗어났거나 아직 코스에 닿지 않았으면 null(그때는 안내 문장을 보여 줌).
     */
    fun straightAhead(): Pair<Int, CheckpointType?>? {
        if (!onCourse || !locked || done || off) return null
        if (nextTurn() != null) return null
        val cp = course.checkpoints.withIndex()
            .filter { (i, c) -> c.type.isTurn && i !in judged && c.atM > alongM }
            .minByOrNull { it.value.atM }?.value
        val to = (cp?.atM ?: course.lengthM) - alongM
        return to.toInt().coerceAtLeast(0) to cp?.type
    }

    /**
     * 지금 들어와 있는 제한속도·보호구역 구간과 그 제한속도. 겹치면 가장 낮은 제한속도, 같으면 보호구역을 보여 줍니다
     * (11/8 사용자 지적: 제한속도 50 구간 안의 보호구역에서 표지가 50으로 나왔음).
     */
    fun activeZoneLimit(): Pair<Checkpoint, Float>? {
        if (!onCourse || done) return null
        return course.checkpoints
            .filter { (it.type == CheckpointType.SPEED || it.type == CheckpointType.SCHOOL) && alongM in (it.atM - it.radiusM)..(it.atM + it.radiusM) }
            .mapNotNull { cp -> (cp.limitKmh ?: if (cp.type == CheckpointType.SCHOOL) t.schoolKmh else null)?.let { cp to it } }
            .minWithOrNull(compareBy<Pair<Checkpoint, Float>> { it.second }.thenBy { if (it.first.type == CheckpointType.SCHOOL) 0 else 1 })
    }

    /** kmh: 안내용(속도가 없으면 마지막 값), real: 받은 속도(없으면 null, 채점에 넣지 않음). */
    private fun checkpoints(prevAlong: Double, kmh: Float, real: Float?, out: MutableList<CourseEvent>) {
        // 한 번에 여러 지점 예고가 몰리면(코스에 막 들어왔을 때 등) 가장 가까운 하나만 말합니다(11/8 2차 재생: 4개가 한꺼번에).
        // 나머지도 예고한 것으로 쳐서 나중에 늦게 나오지 않게 합니다. FHWA: 한 번에 줄 정보는 3~4개 이하.
        var aheadSaid = false
        course.checkpoints.forEachIndexed { i, cp ->
            if (cp.type.isTurn) {
                announceTurn(i, cp, kmh, out)
                val slowJudged = cp.type == CheckpointType.LEFT || cp.type == CheckpointType.RIGHT
                val wFrom = cp.atM - t.turnSlowBeforeM
                val wTo = cp.atM + t.turnSlowAfterM
                // 구간 안에 위치가 들어왔거나, 한 번에 구간을 건너뛰었으면(위치 1초 간격·빠른 속도) 그때 속도로 봅니다
                if (real != null && slowJudged && i !in judged && (alongM in wFrom..wTo || (prevAlong < wFrom && alongM > wTo))) {
                    turnMin[i] = minOf(turnMin[i] ?: Float.MAX_VALUE, real)
                }
                // 회전 지점을 10m 넘게 지나면 회전한 것으로 기록합니다(놓쳤으면 checkMissedTurn에서 먼저 기록됨).
                if (i !in judged && alongM > cp.atM + TURN_DONE_M) {
                    judged += i
                    val heard = turnHeard[i].orEmpty()
                    val guide = if (heard.isEmpty()) "안내 없음" else "안내 " + heard.joinToString("·") { "${it}m" }
                    // 좌·우회전 서행(37번 1)). 구간 안 위치가 하나도 없으면(건너뜀) 판정하지 않습니다.
                    val min = turnMin.remove(i)
                    val notSlow = min != null && min > t.slowKmh
                    if (notSlow) out += CourseEvent.TurnNotSlow(cp, min!!, t.slowKmh)
                    val slowText = min?.let { " · 최저 ${it.toInt()}km/h" + if (notSlow) " (서행 ${t.slowKmh.toInt()} 넘음)" else "" } ?: ""
                    results += PointResult(cp.type, cp.name, !notSlow, guide + slowText)
                }
                return@forEachIndexed
            }
            val start = cp.atM - cp.radiusM
            if (i !in announced && alongM >= start - t.aheadNoticeM && alongM < cp.atM) {
                announced += i
                // 횡단보도는 좌·우회전 지점에 있을 때만 예고합니다(직진 중 횡단보도는 신호대로 지나면 됨, 11/8 사용자 결정)
                val say = cp.type != CheckpointType.CROSSWALK || crosswalkAtTurn(cp)
                // 제한속도·보호구역 예고는 방지턱·횡단보도 예고에 밀려 사라지지 않게 따로 말합니다
                // (11/8 사용자 지적, 전체 녹화: 1250초 보호구역 예고가 방지턱에, 1404초 "제한속도 50"이 횡단보도에 밀려 빠짐)
                if (say && cp.type.isZone) {
                    out += CourseEvent.Ahead(cp, (start - alongM).coerceAtLeast(0.0).toInt())
                } else if (say && !aheadSaid) {
                    out += CourseEvent.Ahead(cp, (start - alongM).coerceAtLeast(0.0).toInt())
                    aheadSaid = true
                }
            }
            if (i in judged) return@forEachIndexed
            // 멈춤 지점은 지점 앞쪽만, 구간 지점은 앞뒤를 봅니다.
            val end = if (cp.type.isZone) cp.atM + cp.radiusM else cp.atM
            val inside = alongM in start..end
            val crossed = prevAlong < start && alongM > end
            if ((inside || crossed) && real != null) {
                val z = zones.getOrPut(i) { floatArrayOf(Float.MAX_VALUE, 0f) }
                z[0] = minOf(z[0], real)
                z[1] = maxOf(z[1], real)
                if (inside) speedOverNow(i, cp, real, out)
            }
            val z = zones[i]
            if (z != null && alongM > end) {
                judged += i
                zones.remove(i)
                val e = judge(cp, z[0], z[1])
                e?.takeUnless { it is CourseEvent.ZoneOver && i in zoneDq }?.let { out += it }
                results += when (cp.type) {
                    // 횡단보도는 판정하지 않고(ok=null) 참고로 속도만 남깁니다. 43번은 동승자 체크.
                    CheckpointType.CROSSWALK ->
                        PointResult(cp.type, cp.name, null, if (z[0] < t.stopKmh) "멈춤 (참고)" else "최저 ${z[0].toInt()}km/h (참고)")
                    CheckpointType.BUMP -> PointResult(cp.type, cp.name, null, "최고 ${z[1].toInt()}km/h (참고)")
                    CheckpointType.STOP ->
                        PointResult(cp.type, cp.name, (e as? CourseEvent.Passed)?.ok, if (z[0] < t.stopKmh) "멈춤" else "최저 ${z[0].toInt()}km/h")
                    else -> {
                        val limit = cp.limitKmh ?: if (cp.type == CheckpointType.SCHOOL) t.schoolKmh else t.slowKmh
                        PointResult(cp.type, cp.name, e == null || (e as? CourseEvent.Passed)?.ok == true, "최고 ${z[1].toInt()}km/h (제한 ${limit.toInt()})")
                    }
                }
            }
        }
    }

    /** 이 횡단보도가 좌·우회전 지점 앞뒤 crosswalkNearTurnM 안에 있는가. */
    private fun crosswalkAtTurn(cp: Checkpoint): Boolean = course.checkpoints.any {
        (it.type == CheckpointType.LEFT || it.type == CheckpointType.RIGHT) && kotlin.math.abs(it.atM - cp.atM) <= t.crosswalkNearTurnM
    }

    /** 제한속도·보호구역 구간 안에서 넘는 순간 단계별로 한 번씩 알립니다. 판정(실격)은 구간을 나갈 때 그대로 합니다. */
    private fun speedOverNow(i: Int, cp: Checkpoint, kmh: Float, out: MutableList<CourseEvent>) {
        val limit = when (cp.type) {
            CheckpointType.SCHOOL -> cp.limitKmh ?: t.schoolKmh
            CheckpointType.SPEED -> cp.limitKmh ?: return
            else -> return
        }
        val level = when {
            kmh <= limit -> 0
            cp.type == CheckpointType.SCHOOL || kmh > limit + t.speedOverKmh -> 2
            else -> 1
        }
        if (level > (overLevels[i] ?: 0)) {
            overLevels[i] = level
            out += CourseEvent.SpeedOver(cp, kmh, limit, level)
            // 실격 기준을 넘는 순간 실격으로 기록합니다(구간을 나갈 때가 아니라). 나갈 때는 다시 내지 않습니다
            // (11/8 사용자 지적: 149초 과속의 실격이 그 구간이 끝나는 889초에 나와 영상에서 엉뚱한 곳에 실격이 보였음)
            if (level >= 2 && zoneDq.add(i)) out += CourseEvent.ZoneOver(cp, kmh, limit)
        }
    }

    /** 지금까지 지난 코스 지점의 결과. 결과표에 씁니다. */
    val pointResults: List<PointResult> get() = results.toList()

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
     * 회전 안내: 먼(300m)·가까운(속도에 따라 80~200m)·지금(약 3초 앞) 단계를 지날 때마다 한 번씩. 이미 가까우면 앞 단계는 건너뜁니다.
     * 앞 회전을 다 돌기 전에는 다음 회전을 말하지 않습니다(11/7 재생: "곧 우회전" 직후 다음 회전 "300미터"가 겹쳐 헷갈림).
     * 다음 회전이 10초 안이면 함께 말하고(묶음), 그 회전은 나중에 "지금"만 말합니다(11/8, FHWA 5장).
     */
    private fun announceTurn(i: Int, cp: Checkpoint, kmh: Float, out: MutableList<CourseEvent>) {
        val remaining = cp.atM - alongM
        if (remaining <= 0) return
        val earlierPending = course.checkpoints.any { o -> o.type.isTurn && o.atM < cp.atM && o.atM > alongM }
        if (earlierPending) return
        val v = if (kmh < t.stoppedKmh) t.assumeKmh else kmh
        val nearM = nearNoticeM(v, t)
        val nowM = nowNoticeM(v, t)
        val stage = when {
            remaining <= nowM -> TurnStage.NOW
            remaining <= nearM -> TurnStage.NEAR
            remaining <= t.farNoticeM -> TurnStage.FAR
            else -> return
        }
        if (i in stacked && stage != TurnStage.NOW) return
        val last = turnStages[i]
        if (last != null && last.ordinal >= stage.ordinal) return
        turnStages[i] = stage
        val dist = when (stage) {
            TurnStage.FAR -> t.farNoticeM
            TurnStage.NOW -> 0
            else -> (remaining / 10).toInt() * 10
        }
        turnHeard.getOrPut(i) { ArrayList() } += if (stage == TurnStage.NOW) nowM else dist
        val then = if (i in stacked) null else stackedNext(i, v)
        then?.let { stacked += it.index }
        out += CourseEvent.Ahead(cp, dist, stage, then?.value?.type)
    }

    private companion object {
        const val TURN_DONE_M = 10.0

        /** 코스 중간에서 잡을 때 지금 위치에서 이 거리 안의 코스 점만 봅니다(멀면 바로 이탈로 보임, 11/8). */
        const val MID_START_M = 40.0
    }
}
