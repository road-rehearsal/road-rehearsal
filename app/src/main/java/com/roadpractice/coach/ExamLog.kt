package com.roadpractice.coach

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 연습 기록. 프로세스가 다시 떠도 사라지지 않도록 감점이 생길 때마다 바로 저장합니다.
 * 위치나 개인정보는 저장하지 않습니다.
 */
class ExamLog(context: Context) : SQLiteOpenHelper(context, "exam_log.db", null, VERSION) {

    data class Run(
        val id: Long,
        val mode: CoachMode,
        val startedAt: Long,
        val endedAt: Long?,
        val score: Int?,
        val passedLine: Boolean?,
        val reviewed: Boolean,
        val course: String?,
        val courseDone: Boolean,
        val interrupted: Boolean,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE runs (id INTEGER PRIMARY KEY AUTOINCREMENT, mode TEXT NOT NULL, started_at INTEGER NOT NULL, " +
                "ended_at INTEGER, score INTEGER, passed INTEGER, reviewed INTEGER NOT NULL DEFAULT 0, " +
                "course TEXT, course_done INTEGER NOT NULL DEFAULT 0, interrupted INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TABLE lines (id INTEGER PRIMARY KEY AUTOINCREMENT, run_id INTEGER NOT NULL, kind TEXT NOT NULL, " +
                "code TEXT NOT NULL, excluded INTEGER NOT NULL DEFAULT 0)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE runs ADD COLUMN course TEXT")
            db.execSQL("ALTER TABLE runs ADD COLUMN course_done INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE runs ADD COLUMN interrupted INTEGER NOT NULL DEFAULT 0")
    }

    fun startRun(mode: CoachMode, now: Long, course: String? = null): Long =
        writableDatabase.insert("runs", null, ContentValues().apply {
            put("mode", mode.name)
            put("started_at", now)
            put("course", course)
        })

    /**
     * 서비스 없이 남은 끝나지 않은 주행을 닫습니다. 앱이 주행 중에 멈췄다는 뜻이므로 "중간에 멈춤"으로 표시하고,
     * 그때까지 저장된 감점으로 결과표를 보여 줍니다. 닫은 개수를 돌려줍니다.
     */
    fun closeOpenRuns(now: Long): Int =
        writableDatabase.update(
            "runs",
            ContentValues().apply {
                put("ended_at", now)
                put("interrupted", 1)
            },
            "ended_at IS NULL",
            null,
        )

    fun markCourseDone(runId: Long) {
        writableDatabase.update("runs", ContentValues().apply { put("course_done", 1) }, "id = ?", arrayOf(runId.toString()))
    }

    fun addCar(runId: Long, item: ExamItem) = addLine(runId, KIND_CAR, item.name)

    fun addCarDisqualification(runId: Long, reason: Disqualification) = addLine(runId, KIND_CAR_DQ, reason.name)

    private fun addLine(runId: Long, kind: String, code: String) {
        writableDatabase.insert("lines", null, ContentValues().apply {
            put("run_id", runId)
            put("kind", kind)
            put("code", code)
        })
    }

    fun endRun(runId: Long, now: Long) {
        writableDatabase.update("runs", ContentValues().apply { put("ended_at", now) }, "id = ?", arrayOf(runId.toString()))
    }

    /** 동승자 체크와 제외 표시를 저장하고 최종 점수를 기록합니다. */
    fun saveReview(
        runId: Long,
        excludedCarIndexes: Set<Int>,
        passenger: List<PassengerItem>,
        passengerDisqualified: List<PassengerDisqualification>,
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("lines", "run_id = ? AND kind IN (?, ?)", arrayOf(runId.toString(), KIND_PASSENGER, KIND_PASSENGER_DQ))
            val carIds = lineIds(db, runId, KIND_CAR)
            carIds.forEachIndexed { i, id ->
                db.update("lines", ContentValues().apply { put("excluded", if (i in excludedCarIndexes) 1 else 0) }, "id = ?", arrayOf(id.toString()))
            }
            passenger.forEach { addLine(runId, KIND_PASSENGER, it.name) }
            passengerDisqualified.forEach { addLine(runId, KIND_PASSENGER_DQ, it.name) }
            val sheet = sheet(runId)
            db.update("runs", ContentValues().apply {
                put("score", sheet.score)
                put("passed", if (sheet.passedLine) 1 else 0)
                put("reviewed", 1)
            }, "id = ?", arrayOf(runId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun lineIds(db: SQLiteDatabase, runId: Long, kind: String): List<Long> =
        db.rawQuery("SELECT id FROM lines WHERE run_id = ? AND kind = ? ORDER BY id", arrayOf(runId.toString(), kind)).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }

    /** 저장된 기록으로 결과표를 다시 만듭니다. */
    fun sheet(runId: Long): ScoreSheet {
        val car = ArrayList<ExamItem>()
        val excluded = HashSet<Int>()
        val passenger = ArrayList<PassengerItem>()
        val carDq = ArrayList<Disqualification>()
        val passengerDq = ArrayList<PassengerDisqualification>()
        readableDatabase.rawQuery("SELECT kind, code, excluded FROM lines WHERE run_id = ? ORDER BY id", arrayOf(runId.toString())).use { c ->
            while (c.moveToNext()) {
                val code = c.getString(1)
                when (c.getString(0)) {
                    KIND_CAR -> runCatching { ExamItem.valueOf(code) }.getOrNull()?.let {
                        if (c.getInt(2) == 1) excluded += car.size
                        car += it
                    }
                    KIND_CAR_DQ -> runCatching { Disqualification.valueOf(code) }.getOrNull()?.let { carDq += it }
                    KIND_PASSENGER -> runCatching { PassengerItem.valueOf(code) }.getOrNull()?.let { passenger += it }
                    KIND_PASSENGER_DQ -> runCatching { PassengerDisqualification.valueOf(code) }.getOrNull()?.let { passengerDq += it }
                }
            }
        }
        return ScoreSheet.build(car, excluded, passenger, carDq, passengerDq)
    }

    fun recentRuns(limit: Int = RECENT_LIMIT): List<Run> =
        readableDatabase.rawQuery(
            "SELECT id, mode, started_at, ended_at, score, passed, reviewed, course, course_done, interrupted FROM runs ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Run(
                            id = c.getLong(0),
                            mode = runCatching { CoachMode.valueOf(c.getString(1)) }.getOrDefault(CoachMode.PRACTICE),
                            startedAt = c.getLong(2),
                            endedAt = if (c.isNull(3)) null else c.getLong(3),
                            score = if (c.isNull(4)) null else c.getInt(4),
                            passedLine = if (c.isNull(5)) null else c.getInt(5) == 1,
                            reviewed = c.getInt(6) == 1,
                            course = if (c.isNull(7)) null else c.getString(7),
                            courseDone = c.getInt(8) == 1,
                            interrupted = c.getInt(9) == 1,
                        ),
                    )
                }
            }
        }

    /** 동승자 체크를 하지 않은 지난 기록 중 가장 최근 것. */
    fun run(runId: Long): Run? = recentRuns(Int.MAX_VALUE).firstOrNull { it.id == runId }

    /** 코스별로 완주한 횟수. 아직 안 해 본 코스를 먼저 고를 때 씁니다. */
    fun courseDoneCounts(): Map<String, Int> =
        readableDatabase.rawQuery("SELECT course, COUNT(*) FROM runs WHERE course IS NOT NULL AND course_done = 1 GROUP BY course", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }

    fun latestUnreviewedEnded(): Run? = recentRuns(1).firstOrNull()?.takeIf { it.endedAt != null && !it.reviewed }

    /** 차가 본 항목의 횟수(제외 빼고). 자주 틀리는 항목을 보여 줄 때 씁니다. */
    fun carItemCounts(): Map<ExamItem, Int> =
        readableDatabase.rawQuery("SELECT code, COUNT(*) FROM lines WHERE kind = ? AND excluded = 0 GROUP BY code", arrayOf(KIND_CAR)).use { c ->
            buildMap {
                while (c.moveToNext()) runCatching { ExamItem.valueOf(c.getString(0)) }.getOrNull()?.let { put(it, c.getInt(1)) }
            }
        }

    private companion object {
        const val VERSION = 3
        const val KIND_CAR = "car"
        const val KIND_CAR_DQ = "car_dq"
        const val KIND_PASSENGER = "passenger"
        const val KIND_PASSENGER_DQ = "passenger_dq"
        const val RECENT_LIMIT = 5
    }
}
