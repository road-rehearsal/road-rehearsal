package com.roadpractice.coach

import android.content.Context
import org.json.JSONObject

/** assets/courses.json을 읽습니다. 좌표는 OpenStreetMap 도로로 그린 연습용 자료입니다. */
object CourseLoader {
    @Volatile
    private var cache: List<Course>? = null

    fun all(context: Context): List<Course> = cache ?: load(context).also { cache = it }

    fun find(context: Context, id: String?): Course? = id?.let { key -> all(context).firstOrNull { it.id == key } }

    private fun load(context: Context): List<Course> {
        val text = context.assets.open("courses.json").bufferedReader().use { it.readText() }
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
                    Checkpoint(CheckpointType.valueOf(c.getString("type")), c.getString("name"), c.getDouble("at_m"), c.getDouble("r"))
                },
            )
        }
    }
}
