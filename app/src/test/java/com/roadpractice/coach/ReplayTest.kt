package com.roadpractice.coach

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 실제 주행 기록(폰 "코스 기록" 앱 CSV)을 판정 코드에 그대로 넣어 보는 재생 시험.
 * 기록과 코스는 사용자 위치라 저장소에 없습니다. 있을 때만 돌고, 없으면 건너뜁니다.
 *
 *   gradlew testDebugUnitTest --tests "*ReplayTest*" -PreplayDir=<기록 폴더>
 *
 * 폴더에는 <기록>.csv, <기록>.course.json, <기록>.truth.json이 있어야 합니다(code/course-tools로 만듦).
 * 결과는 같은 폴더 replay_report.txt에 씁니다(좌표 없음).
 * 기록에는 기어·깜빡이·브레이크·조향이 없어(폰 기록) 그 항목은 값 없음으로 두고, 위치·속도로 보는 항목만 봅니다.
 */
class ReplayTest {
    private val dir = System.getProperty("replay.dir").orEmpty()

    private data class Row(val t: Long, val lat: Double, val lon: Double, val kmh: Float?)

    private fun rows(csv: File): List<Row> = csv.readLines().filter { it.firstOrNull()?.isDigit() == true }.map {
        val c = it.split(",")
        Row(c[0].toLong(), c[1].toDouble(), c[2].toDouble(), c[5].toFloatOrNull()?.times(3.6f))
    }

    /** 시험 시작(P→D) 뒤 기록대로 달림. 거리는 속도 적분. */
    private fun run(mode: CoachMode, rows: List<Row>, course: Course, startDelayS: Int = 0): Pair<CoachSession, List<String>> {
        val log = ArrayList<String>()
        val t0 = rows.first().t
        val session = CoachSession(mode, say = { }, course = CourseTracker(course))
        var dist = 0.0
        fun feed(s: VehicleState, label: Long) {
            session.onState(s).forEach { e ->
                val sec = (label - t0) / 1000
                when (e) {
                    is ExamEvent.Deduction -> log += "${sec}s ITEM ${e.item.number} ${e.item.title}"
                    is ExamEvent.Disqualified -> log += "${sec}s DQ ${e.reason.title}"
                    is ExamEvent.Guide -> log += "${sec}s GUIDE ${e.text} @${course.name}:${(session.course?.alongM ?: 0.0).toInt()}m"
                    is ExamEvent.Started -> log += "${sec}s START"
                    is ExamEvent.Ended -> log += "${sec}s END"
                }
            }
        }
        val first = rows.first()
        feed(VehicleState(timeMs = first.t, speedKmh = 0f, gear = Gear.PARK, latitude = first.lat, longitude = first.lon), first.t)
        feed(VehicleState(timeMs = first.t + 1, speedKmh = 0f, gear = Gear.DRIVE, latitude = first.lat, longitude = first.lon), first.t)
        for (k in 1..startDelayS) {
            val tt = first.t + k * 1000L
            feed(VehicleState(timeMs = tt, speedKmh = 0f, gear = Gear.DRIVE, latitude = first.lat, longitude = first.lon), tt)
        }
        val shift = startDelayS * 1000L
        var prevT = first.t
        for (r in rows) {
            val dt = (r.t - prevT) / 1000.0
            prevT = r.t
            dist += (r.kmh ?: 0f) / 3.6 * dt
            feed(VehicleState(timeMs = r.t + shift, speedKmh = r.kmh, distanceM = dist, gear = Gear.DRIVE, latitude = r.lat, longitude = r.lon), r.t)
        }
        return session to log
    }

    @Test
    fun replayRecordedDrive() {
        assumeTrue("기록 폴더 없음: 건너뜀", dir.isNotEmpty() && File(dir).isDirectory)
        val csv = File(dir).listFiles { f -> f.name.matches(Regex("drive_\\d+_\\d+\\.csv")) }!!.sorted().first()
        val base = csv.nameWithoutExtension
        val course = CourseLoader.parse(File(dir, "$base.course.json").readText()).first()
        val original = rows(csv)
        val report = StringBuilder()
        fun section(title: String, mode: CoachMode, rs: List<Row>, c: Course = course, delay: Int = 0) {
            val (s, log) = run(mode, rs, c, delay)
            report.appendLine("## $title ($mode)")
            report.appendLine("점수 ${s.result.score}, 감점 ${s.result.deductions.map { it.number }}, 실격 ${s.result.disqualified.map { it.title }}")
            log.forEach { report.appendLine("  $it") }
            report.appendLine()
        }

        section("1 원본", CoachMode.PRACTICE, original)
        section("1b 원본", CoachMode.EXAM, original)
        section("2 출발 25초 지연", CoachMode.EXAM, original, delay = 25)

        // 3 코스 이탈: 300~320초 동안 위치를 북쪽으로 옮김(50m, 80m)
        fun offset(m: Double) = original.map { r ->
            val sec = (r.t - original.first().t) / 1000
            if (sec in 300..320) r.copy(lat = r.lat + m / 111_000.0) else r
        }
        section("3a 코스 이탈 50m 20초", CoachMode.EXAM, offset(50.0))
        section("3b 코스 이탈 80m 20초", CoachMode.EXAM, offset(80.0))

        // 4 30km/h 구간(영상 344초 노면 "30")과 가상 보호구역에서 과속
        val zoneAt = course.checkpoints.firstOrNull { it.type == CheckpointType.SPEED }
        val school = Checkpoint(CheckpointType.SCHOOL, "가상 어린이보호구역", course.lengthM * 0.5, 120.0, 30f)
        val withSchool = course.copy(checkpoints = (course.checkpoints + school).sortedBy { it.atM })
        section("4a 가상 보호구역(원본 속도)", CoachMode.EXAM, original, withSchool)
        val fast = original.map { it.copy(kmh = it.kmh?.let { k -> if (k > 5f) k + 20f else k }) }
        section("4b 가상 보호구역 + 전체 20km/h 빠르게", CoachMode.EXAM, fast, withSchool)
        report.appendLine("(30km/h 구간 지점: ${zoneAt?.let { "${it.atM.toInt()}m ±${it.radiusM.toInt()}m" } ?: "코스에 없음"})\n")

        // 5 GPS 튐 1점(400m)과 5초 끊김
        val glitch = original.mapIndexedNotNull { i, r ->
            val sec = (r.t - original.first().t) / 1000
            when {
                sec == 200L -> r.copy(lat = r.lat + 400 / 111_000.0)
                sec in 420..424 -> null
                else -> r
            }
        }
        section("5 GPS 튐·끊김", CoachMode.EXAM, glitch)

        // 6 한 번도 서지 않음(출발 뒤 속도를 최소 12km/h로): 횡단보도 지점마다 43번이 나와야 함
        val t0 = original.first().t
        val rolling = original.map { r -> if (r.t - t0 > 20_000) r.copy(kmh = maxOf(r.kmh ?: 0f, 12f)) else r }
        section("6 서지 않고 통과", CoachMode.EXAM, rolling)
        report.appendLine("(횡단보도 판정 지점 ${course.checkpoints.count { it.type == CheckpointType.CROSSWALK }}개)")

        val out = File(dir, "replay_report.txt")
        out.writeText(report.toString())
        println("REPLAY report -> ${out.absolutePath}")
    }
}
