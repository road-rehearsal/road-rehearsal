package com.roadpractice.coach

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 코스를 읽습니다.
 * - assets/courses.json: 앱에 들어 있는 연습용 좌표(OpenStreetMap 도로로 그림)
 * - 앱 파일 폴더(filesDir)의 courses_local.json(있으면): 사용자가 폰으로 기록해 만든 코스.
 *   사용자 위치가 들어 있어 앱·저장소에 넣지 않고 기기에만 둡니다(adb push 뒤 run-as로 복사).
 */
object CourseLoader {
    private const val TAG = "RoadCoach"
    const val LOCAL_FILE = "courses_local.json"

    @Volatile
    private var cache: List<Course>? = null

    fun all(context: Context): List<Course> = cache ?: load(context).also { cache = it }

    fun find(context: Context, id: String?): Course? = id?.let { key -> all(context).firstOrNull { it.id == key } }

    /** 폰으로 기록해 만든 코스(기기 안 파일)인지. 코스 고르기에서 앱 기본 코스와 나눠 보여 줍니다. */
    @Volatile
    var localIds: Set<String> = emptySet()
        private set

    private fun load(context: Context): List<Course> {
        val bundled = parse(context.assets.open("courses.json").bufferedReader().use { it.readText() })
        val local = File(context.filesDir, LOCAL_FILE)
        val mine = if (local.isFile) {
            runCatching { parse(local.readText()) }
                .onFailure { Log.w(TAG, "local course load failed: ${it.message}") }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        localIds = mine.map { it.id }.toSet()
        Log.i(TAG, "COURSES bundled=${bundled.size} local=${mine.size}")
        return bundled + mine
    }

    /** courses.json 형식을 읽습니다. 단위 테스트에서도 쓰도록 공개합니다. */
    fun parse(text: String): List<Course> {
        val arr = JSONObject(text).getJSONArray("courses")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val pts = o.getJSONArray("points")
            val cps = o.getJSONArray("checkpoints")
            Course(
                id = o.getString("id"),
                name = o.getString("name"),
                lat = DoubleArray(pts.length()) { pts.getJSONArray(it).getDouble(0) },
                lng = DoubleArray(pts.length()) { pts.getJSONArray(it).getDouble(1) },
                checkpoints = List(cps.length()) {
                    val c = cps.getJSONObject(it)
                    Checkpoint(
                        CheckpointType.valueOf(c.getString("type")),
                        c.getString("name"),
                        c.getDouble("at_m"),
                        c.getDouble("r"),
                        if (c.has("limit")) c.getDouble("limit").toFloat() else null,
                    )
                },
            )
        }
    }
}
