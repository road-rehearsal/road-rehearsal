package com.roadpractice.coach

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlin.math.abs

/**
 * 시연용 재생(디버그 빌드 전용). 폰 "코스 기록" 앱 CSV를 읽어 차량 신호 대신 VehicleState로 넣습니다.
 * 에뮬레이터는 차량 속도를 밖에서 넣을 수 없어(VHAL 주입은 user 빌드에서 막힘, 11/7 확인) 기록한 속도·위치로
 * 앱 전체(서비스·판정·음성·로그)를 확인하려는 것입니다. 실제 차량 신호가 아니므로 화면·로그에 재생임을 밝힙니다.
 *
 * 순서: P(정지) → D → 기록 그대로 → 끝에서 P, 운전석 문 열림(주행 끝).
 * 판정 시간은 기록 시각을 그대로 쓰고, 재생만 [speed]배 빠르게 합니다.
 *
 * 섞기 모드(11/8, 시연 화면): [live]가 있으면 속도·위치·조향각은 기록에서, 방향지시등·문·안전띠·기어·브레이크·주차 브레이크는
 * 실제 차량 신호(에뮬레이터 Virtual Console)에서 받습니다. 조향각은 기록에 없어 자이로(회전 속도)로 만듭니다.
 * 섞기 모드가 아니면 기록에 없는 신호는 "값 없음"이라 그 항목은 판정하지 않습니다.
 *
 * 구간 재생: [fromS]~[toS](기록 시작부터 초)만 재생합니다. 거리는 앞부분까지 쌓아 둡니다.
 */
class ReplaySource(
    private val file: File,
    private val speed: Int,
    private val onChange: (VehicleState) -> Unit,
    private val fromS: Int = 0,
    private val toS: Int = Int.MAX_VALUE,
    private val live: (() -> VehicleState?)? = null,
    /** 구간 재생에서 첫 상태를 넣기 전에 시작 거리(m)를 알려 줍니다(코스를 중간에서 잡는 힌트). */
    private val onStartDist: (Double) -> Unit = {},
    /** 기록을 흘리기 전에 D로 서 있을 초(5번 "20초 내 미출발" 시연, 11/8). 기록 시각은 그만큼 앞당겨 넣습니다. */
    private val holdS: Int = 0,
) {
    /** 섞기 모드 끝: 조작판에서 운전석 문을 열 때까지 서 있는 상태를 1초마다 넣습니다(44·46번 시연). */
    private val idle = object : Runnable {
        override fun run() {
            if (!running) return
            val l = live?.invoke()
            if (l != null && ExamRules.DRIVER_DOOR in l.openDoors) {
                emit(merge(state.copy(timeMs = state.timeMs + 1_000, speedKmh = 0f)))
                Log.i(TAG, "REPLAY end (driver door)")
                running = false
                return
            }
            emit(merge(state.copy(timeMs = state.timeMs + 1_000, speedKmh = 0f)))
            main.postDelayed(this, 1_000L)
        }
    }
    private data class Row(val t: Long, val lat: Double, val lon: Double, val kmh: Float?, val yaw: Float?)

    private val main = Handler(Looper.getMainLooper())
    private var rows: List<Row> = emptyList()
    private var i = 0
    private var dist = 0.0
    private var running = false
    private var t0 = 0L
    var state = VehicleState()
        private set

    /** 구간 재생을 시작한 곳의 기록 거리(m). 코스를 중간에서 잡을 때 힌트로 씁니다. */
    var startDistM = 0.0
        private set

    fun start() {
        rows = file.readLines().filter { it.firstOrNull()?.isDigit() == true }.map {
            val c = it.split(",")
            Row(c[0].toLong(), c[1].toDouble(), c[2].toDouble(), c[5].toFloatOrNull()?.times(3.6f), c.getOrNull(6)?.toFloatOrNull())
        }
        if (rows.isEmpty()) {
            Log.w(TAG, "REPLAY no rows in ${file.name}")
            return
        }
        t0 = rows.first().t
        // 구간 앞부분은 거리만 쌓고 건너뜁니다
        while (i < rows.size && rows[i].t - t0 < fromS * 1000L) {
            if (i > 0) dist += (rows[i].kmh ?: 0f) / 3.6 * ((rows[i].t - rows[i - 1].t) / 1000.0)
            i++
        }
        if (i >= rows.size) {
            Log.w(TAG, "REPLAY from=$fromS beyond end")
            return
        }
        startDistM = dist
        if (fromS > 0) onStartDist(dist)
        running = true
        Log.i(TAG, "REPLAY start file=${file.name} rows=${rows.size} speed=${speed}x from=${fromS}s to=${if (toS == Int.MAX_VALUE) "end" else "${toS}s"} mixed=${live != null}")
        val r0 = rows[i]
        val hold = holdS * 1000L
        emit(VehicleState(timeMs = r0.t - 2_000 - hold, speedKmh = 0f, gear = Gear.PARK, latitude = r0.lat, longitude = r0.lon))
        val d = VehicleState(timeMs = r0.t - 1_000 - hold, speedKmh = 0f, gear = Gear.DRIVE, latitude = r0.lat, longitude = r0.lon)
        emit(if (live != null) merge(d).copy(gear = Gear.DRIVE) else d)
        firstT = r0.t
        if (live != null && holdS > 0) {
            // 섞기 모드 + 대기: 조작판이 기어를 P에서 빼는 순간을 0초로 삼습니다. 앱이 켜지는 데 걸리는 시간이
            // 실행마다 0.4~1.2초로 달라 조작판 시간표와 어긋났기 때문입니다(11/8, 20·32번이 잘못 나옴).
            val go = object : Runnable {
                override fun run() {
                    if (!running) return
                    val g = live.invoke()?.gear
                    if (g == null || g == Gear.PARK || g == Gear.UNKNOWN) {
                        main.postDelayed(this, 20L)
                        return
                    }
                    Log.i(TAG, "REPLAY go (console gear=$g)")
                    begin(r0, hold)
                }
            }
            main.post(go)
            return
        }
        begin(r0, hold)
    }

    private fun begin(r0: Row, hold: Long) {
        // 절대 시간표(11/8): 처리 시간이 쌓여 재생이 벽시계보다 늦어지지 않게, 기록 시각에서 정한 때에 맞춰 넣습니다.
        wallStart = SystemClock.uptimeMillis()
        if (holdS > 0) {
            // 출발 전 대기: 1초마다 서 있는 상태를 넣고 나서 기록을 흘립니다
            var k = 1
            val wait = object : Runnable {
                override fun run() {
                    if (!running) return
                    if (k > holdS) {
                        main.postAtTime(step, dueOf(i))
                        return
                    }
                    val s = state.copy(timeMs = r0.t - 1_000 - hold + k * 1000L, speedKmh = 0f)
                    emit(if (live != null) merge(s) else s)
                    k++
                    main.postAtTime(this, wallStart + k * 1000L / speed.coerceAtLeast(1))
                }
            }
            main.postAtTime(wait, wallStart + 1000L / speed.coerceAtLeast(1))
        } else {
            main.postAtTime(step, dueOf(i))
        }
    }

    private var wallStart = 0L
    private var firstT = 0L

    /** i번 기록을 넣을 벽시계 시각(uptime): 시작 + (대기 + 1초 + 기록 경과) / 배속. */
    private fun dueOf(k: Int): Long =
        wallStart + ((holdS + 1) * 1000L + (rows[k].t - firstT)) / speed.coerceAtLeast(1)

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
    }

    /** 섞기 모드에서 조작판 값이 바뀌면 바로 반영합니다(기록 시각은 그대로, 1ms만 더함). */
    fun liveChanged() {
        if (!running || live == null || state.timeMs == 0L) return
        emit(merge(state.copy(timeMs = state.timeMs + 1)))
    }

    private fun intervalMs() = (1_000L / speed.coerceAtLeast(1)).coerceAtLeast(20)

    private val step = object : Runnable {
        override fun run() {
            if (!running) return
            if (i >= rows.size || rows[i].t - t0 > toS * 1000L) {
                finish()
                return
            }
            val r = rows[i]
            val prevT = if (i > 0) rows[i - 1].t else r.t
            dist += (r.kmh ?: 0f) / 3.6 * ((r.t - prevT) / 1000.0)
            val base = VehicleState(timeMs = r.t, speedKmh = r.kmh, distanceM = dist, gear = Gear.DRIVE, latitude = r.lat, longitude = r.lon)
            emit(if (live != null) merge(base.copy(steeringAbsDeg = heldSteering(i))) else base)
            i++
            if (i < rows.size) main.postAtTime(this, dueOf(i)) else main.postDelayed(this, intervalMs())
        }
    }

    /**
     * 최근 [STEER_HOLD]개 기록(약 2초) 중 가장 큰 핸들 각. 자이로가 회전 중간에 잠깐 줄어 한 번의 회전이 둘로 쪼개지는 것을 막습니다
     * (11/8 2차 기록: 23~24초·26~36초 → 23~38초 한 번). 재생에서 만드는 값에만 쓰고 실제 차 판정 규칙은 그대로입니다.
     */
    private fun heldSteering(at: Int): Float? =
        (maxOf(0, at - STEER_HOLD)..at).mapNotNull { steeringFromYaw(rows[it].yaw, rows[it].kmh) }.maxOrNull()

    /** 기록 값(속도·위치·거리·조향)에 조작판 값(기어·방향지시등·문·안전띠·브레이크·주차 브레이크)을 덮습니다. */
    private fun merge(s: VehicleState): VehicleState {
        val l = live?.invoke() ?: return s
        return s.copy(
            gear = if (l.gear == Gear.UNKNOWN) s.gear else l.gear,
            turnSignal = l.turnSignal,
            openDoors = l.openDoors,
            seatBeltWarningOn = l.seatBeltWarningOn,
            braking = l.braking,
            parkingBrakeOn = l.parkingBrakeOn,
        )
    }

    private fun finish() {
        // 구간 끝에서 달리던 중이면 1초에 5km/h씩 천천히 세웁니다(바로 0으로 떨어뜨리면 급브레이크 21번이 잘못 나옴, 11/8)
        var s = state
        while ((s.speedKmh ?: 0f) > 0f) {
            s = s.copy(timeMs = s.timeMs + 1_000, speedKmh = ((s.speedKmh ?: 0f) - 5f).coerceAtLeast(0f), braking = true)
            emit(if (live != null) merge(s).copy(speedKmh = s.speedKmh) else s)
        }
        if (live != null) {
            // 섞기 모드: 끝(P·주차 브레이크·운전석 문)은 조작판이 정합니다. 문을 열 때까지 서 있는 상태를 넣습니다.
            Log.i(TAG, "REPLAY stopped, waiting for driver door")
            main.postDelayed(idle, 1_000L)
            return
        }
        val last = state
        emit(last.copy(timeMs = last.timeMs + 1_000, speedKmh = 0f, gear = Gear.PARK, parkingBrakeOn = true))
        emit(last.copy(timeMs = last.timeMs + 2_000, speedKmh = 0f, gear = Gear.PARK, parkingBrakeOn = true, openDoors = setOf(ExamRules.DRIVER_DOOR)))
        Log.i(TAG, "REPLAY end")
        running = false
    }

    private fun emit(s: VehicleState) {
        state = s
        onChange(s)
    }

    companion object {
        private const val TAG = "RoadCoach"
        private const val WHEELBASE_M = 2.9
        private const val STEER_RATIO = 15.0
        private const val STEER_HOLD = 2

        /**
         * 자이로 회전 속도(rad/s)와 속도로 핸들 각도(도)를 근사합니다: 바퀴 각 ≈ 축간거리 × 회전 속도 / 속도, 핸들 = 바퀴 각 × 조향비.
         * 축간거리 2.9m·조향비 15는 일반 승용차 값(추정). 방향지시등 판정이 "돌고 있다"를 알 수 있을 정도면 됩니다.
         */
        fun steeringFromYaw(yaw: Float?, kmh: Float?): Float? {
            val y = yaw ?: return null
            // 서 있을 때 자이로 값은 잡음뿐인데 속도를 2m/s로 잡고 나누면 큰 핸들 각이 나와, 서 있는 동안 "회전"으로 보였음
            // (11/8 리허설: 출발 전 3~7초가 회전이 되어 11번이 빠짐). 서 있으면 0으로 둡니다.
            if ((kmh ?: 0f) < VehicleState.MOVING_KMH) return 0f
            val v = ((kmh ?: 0f) / 3.6).coerceAtLeast(2.0)
            return Math.toDegrees(WHEELBASE_M * abs(y) / v * STEER_RATIO).toFloat().coerceAtMost(540f)
        }
    }
}
