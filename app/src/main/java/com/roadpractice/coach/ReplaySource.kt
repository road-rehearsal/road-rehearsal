package com.roadpractice.coach

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * 시연용 재생(디버그 빌드 전용). 폰 "코스 기록" 앱 CSV를 읽어 차량 신호 대신 VehicleState로 넣습니다.
 * 에뮬레이터는 차량 속도를 밖에서 넣을 수 없어(VHAL 주입은 user 빌드에서 막힘, 11/7 확인) 기록한 속도·위치로
 * 앱 전체(서비스·판정·음성·로그)를 확인하려는 것입니다. 실제 차량 신호가 아니므로 화면·로그에 재생임을 밝힙니다.
 *
 * 순서: P(정지) → D → 기록 그대로 → 끝에서 P, 운전석 문 열림(주행 끝).
 * 판정 시간은 기록 시각을 그대로 쓰고, 재생만 [speed]배 빠르게 합니다.
 * 기록에 없는 신호(깜빡이·브레이크·조향·문·안전띠)는 "값 없음"이라 그 항목은 판정하지 않습니다.
 */
class ReplaySource(
    private val file: File,
    private val speed: Int,
    private val onChange: (VehicleState) -> Unit,
) {
    private data class Row(val t: Long, val lat: Double, val lon: Double, val kmh: Float?)

    private val main = Handler(Looper.getMainLooper())
    private var rows: List<Row> = emptyList()
    private var i = 0
    private var dist = 0.0
    private var running = false
    var state = VehicleState()
        private set

    fun start() {
        rows = file.readLines().filter { it.firstOrNull()?.isDigit() == true }.map {
            val c = it.split(",")
            Row(c[0].toLong(), c[1].toDouble(), c[2].toDouble(), c[5].toFloatOrNull()?.times(3.6f))
        }
        if (rows.isEmpty()) {
            Log.w(TAG, "REPLAY no rows in ${file.name}")
            return
        }
        running = true
        Log.i(TAG, "REPLAY start file=${file.name} rows=${rows.size} speed=${speed}x")
        val r0 = rows.first()
        emit(VehicleState(timeMs = r0.t - 2_000, speedKmh = 0f, gear = Gear.PARK, latitude = r0.lat, longitude = r0.lon))
        emit(VehicleState(timeMs = r0.t - 1_000, speedKmh = 0f, gear = Gear.DRIVE, latitude = r0.lat, longitude = r0.lon))
        main.postDelayed(step, intervalMs())
    }

    fun stop() {
        running = false
        main.removeCallbacks(step)
    }

    private fun intervalMs() = (1_000L / speed.coerceAtLeast(1)).coerceAtLeast(20)

    private val step = object : Runnable {
        override fun run() {
            if (!running) return
            if (i >= rows.size) {
                finish()
                return
            }
            val r = rows[i]
            val prevT = if (i > 0) rows[i - 1].t else r.t
            dist += (r.kmh ?: 0f) / 3.6 * ((r.t - prevT) / 1000.0)
            emit(VehicleState(timeMs = r.t, speedKmh = r.kmh, distanceM = dist, gear = Gear.DRIVE, latitude = r.lat, longitude = r.lon))
            i++
            main.postDelayed(this, intervalMs())
        }
    }

    private fun finish() {
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

    private companion object {
        const val TAG = "RoadCoach"
    }
}
