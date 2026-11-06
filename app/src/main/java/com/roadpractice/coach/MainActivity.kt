package com.roadpractice.coach

import android.Manifest
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 모드 선택, 주행 중 표시, 정차 후 결과표와 동승자 체크.
 * 주행 중에는 감점을 보여 주지 않고 입력도 받지 않습니다(움직이면 입력을 숨깁니다).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var controls: LinearLayout
    private lateinit var review: LinearLayout
    private lateinit var log: ExamLog
    private lateinit var scroll: ScrollView

    /** 지금 결과표를 보고 있는 기록. */
    private var reviewRunId = CoachService.NO_RUN
    private var reviewMoving = false
    private val excluded = HashSet<Int>()
    private val passenger = LinkedHashSet<PassengerItem>()
    private val passengerDq = LinkedHashSet<PassengerDisqualification>()
    private var sheetHeader: TextView? = null

    /** AAOS 주행 규제. 서비스가 꺼져 있어도 주행 중인지 알 수 있게 씁니다. */
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var restricted = false

    /** 코스 선택: 0 = 코스 없음, 1 = 뽑기, 2부터 코스 목록. */
    private var courseChoice = 1
    private val courses: List<Course> by lazy { runCatching { CourseLoader.all(applicationContext) }.getOrDefault(emptyList()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = ExamLog(applicationContext)
        status = TextView(this).apply {
            textSize = STATUS_TEXT_SP
            gravity = Gravity.CENTER
        }
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        review = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
            addView(status)
            addView(controls)
            addView(review)
        }
        scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        // 이미 받은 권한은 다시 묻지 않습니다. 권한 화면이 이 앱 작업에 끼면
        // 홈 화면이 주행 중 표시 가능(distractionOptimized)하지 않은 앱으로 보고 가리는 것을 11/6에 확인했습니다(추정 원인).
        val missing = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            "android.car.permission.CAR_SPEED",
            "pleos.car.permission.FUSED_LOCATION",
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
    }

    override fun onStart() {
        super.onStart()
        connectUx()
        // 서비스가 없는데 끝나지 않은 주행이 있으면 앱이 주행 중에 멈췄던 것입니다.
        if (CoachService.latest == null) log.closeOpenRuns(System.currentTimeMillis())
        CoachService.listener = { render(it) }
        render(CoachService.latest)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        render(CoachService.latest)
    }

    override fun onStop() {
        CoachService.listener = null
        runCatching { uxManager?.unregisterListener() }
        runCatching { car?.disconnect() }
        uxManager = null
        car = null
        super.onStop()
    }

    override fun onDestroy() {
        log.close()
        super.onDestroy()
    }

    private fun connectUx() {
        runCatching {
            val c = Car.createCar(this).also { car = it }
            uxManager = (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).also { m ->
                m.registerListener { onUx(it.isRequiresDistractionOptimization) }
                restricted = m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            }
        }
    }

    private fun onUx(value: Boolean) {
        if (value == restricted) return
        restricted = value
        render(CoachService.latest)
    }

    private fun render(s: CoachService.Snapshot?) {
        // 서비스가 꺼져 있으면 속도를 모르므로 주행 규제 값도 함께 봅니다.
        val moving = restricted || s?.state?.isMoving == true
        when {
            s != null && s.started && !s.ended -> showDriving(s)
            s != null && s.ended && s.runId != CoachService.NO_RUN -> showReview(s.runId, moving)
            else -> {
                val pending = if (s == null) log.latestUnreviewedEnded() else null
                if (pending != null) showReview(pending.id, moving) else showHome(s, moving)
            }
        }
    }

    private fun showHome(s: CoachService.Snapshot?, moving: Boolean = false) {
        reviewRunId = CoachService.NO_RUN
        review.removeAllViews()
        status.text = when (s) {
            null -> "연습용 앱입니다. 공식 채점이 아니며 합격을 보장하지 않습니다.\n" +
                "면허 2년이 지난 동승자와 함께 타고, 연습 표지를 붙이고, 보험 범위를 확인해 주세요.\n" +
                "코스 좌표: © OpenStreetMap contributors (ODbL). 공단 제공 자료가 아니며 '가상' 지점은 시연용입니다."
            else -> "${modeName(s.mode)} 준비됨. 기어를 P에서 바꾸면 시작합니다."
        }
        controls.removeAllViews()
        if (moving) {
            status.text = "주행 중에는 설정할 수 없습니다. 정차한 뒤 모드를 고르세요."
            return
        }
        controls.addView(button("코스: ${courseLabel()}") {
            courseChoice = (courseChoice + 1) % (courses.size + 2)
            showHome(s, moving)
        })
        controls.addView(button("연습 모드") { CoachService.start(this, CoachMode.PRACTICE, pickCourse()) })
        controls.addView(button("시험 모드") { CoachService.start(this, CoachMode.EXAM, pickCourse()) })
        if (s != null) controls.addView(button("끝내기") { CoachService.stop(this) })
        addHistory()
    }

    private fun showDriving(s: CoachService.Snapshot) {
        reviewRunId = CoachService.NO_RUN
        review.removeAllViews()
        controls.removeAllViews()
        val course = s.courseName?.let { name -> "\n$name ${((s.courseFraction ?: 0.0) * 100).toInt()}%" } ?: ""
        status.text = "${modeName(s.mode)} 주행 중$course\n끝나면 P로 바꾸고 운전석 문을 열어 주세요.$mockNote"
    }

    private fun showReview(runId: Long, moving: Boolean) {
        if (runId == reviewRunId && moving == reviewMoving) return
        val fresh = runId != reviewRunId
        if (fresh) {
            excluded.clear()
            passenger.clear()
            passengerDq.clear()
        }
        reviewRunId = runId
        reviewMoving = moving
        controls.removeAllViews()
        review.removeAllViews()
        val run = log.run(runId)
        val courseLine = when {
            run?.course == null -> "코스 없이 달려 37·38·43번(서행·일시정지·횡단보도)은 보지 않았습니다."
            else -> "${courses.firstOrNull { it.id == run.course }?.name ?: run.course} · ${if (run.courseDone) "완주" else "완주 못 함"}"
        }
        val stopped = if (run?.interrupted == true) "\n앱이 주행 중에 멈춰 그때까지의 기록만 있습니다." else ""
        status.text = "결과표 (연습용 기준, 공식 채점 아님)\n$courseLine$stopped"
        sheetHeader = text(SECTION_TEXT_SP).also { review.addView(it) }
        if (moving) {
            review.addView(text(BODY_TEXT_SP, "주행 중에는 입력할 수 없습니다. 정차한 뒤 체크해 주세요."))
            refreshHeader()
            scroll.post { scroll.scrollTo(0, 0) }
            return
        }
        val base = log.sheet(runId)
        review.addView(text(SECTION_TEXT_SP, "차가 본 항목 (틀렸으면 제외)"))
        val carLines = base.lines.filter { it.byCar }
        if (carLines.isEmpty()) review.addView(text(BODY_TEXT_SP, "없음"))
        carLines.forEachIndexed { i, line ->
            review.addView(check("${line.number}번 ${line.title} -${line.points}  [제외]", i in excluded) { on ->
                if (on) excluded += i else excluded -= i
                refreshHeader()
            })
        }
        base.disqualifications.forEach { review.addView(text(BODY_TEXT_SP, "실격 사유: $it")) }
        review.addView(text(SECTION_TEXT_SP, "동승자 체크 (차가 볼 수 없는 항목)"))
        PassengerItem.entries.groupBy { it.task }.forEach { (task, items) ->
            review.addView(text(BODY_TEXT_SP, task))
            items.forEach { item ->
                review.addView(check("${item.number}번 ${item.title} -${item.points}", item in passenger) { on ->
                    if (on) passenger += item else passenger -= item
                    refreshHeader()
                })
            }
        }
        review.addView(text(SECTION_TEXT_SP, "동승자가 본 실격 사유"))
        PassengerDisqualification.entries.forEach { reason ->
            review.addView(check(reason.title, reason in passengerDq) { on ->
                if (on) passengerDq += reason else passengerDq -= reason
                refreshHeader()
            })
        }
        review.addView(button("저장하고 처음으로") {
            log.saveReview(runId, excluded.toSet(), passenger.toList(), passengerDq.toList())
            CoachService.stop(this)
            showHome(null)
            scroll.post { scroll.scrollTo(0, 0) }
        })
        refreshHeader()
        if (fresh) scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun refreshHeader() {
        val runId = reviewRunId
        if (runId == CoachService.NO_RUN) return
        val base = log.sheet(runId)
        val car = base.lines.filter { it.byCar }.map { line -> ExamItem.entries.first { it.number == line.number } }
        val carDq = Disqualification.entries.filter { dq -> dq.title in base.disqualifications }
        val sheet = ScoreSheet.build(car, excluded, passenger.toList(), carDq, passengerDq.toList())
        val verdict = when {
            sheet.disqualifications.isNotEmpty() -> "실격 사유 있음"
            sheet.passedLine -> "합격선(70점) 통과"
            else -> "합격선(70점) 미달"
        }
        sheetHeader?.text = "${sheet.score}점 · $verdict\n차가 본 항목 -${sheet.carDeducted}, 동승자 체크 -${sheet.passengerDeducted}"
    }

    private fun addHistory() {
        val runs = log.recentRuns()
        if (runs.isEmpty()) return
        review.addView(text(SECTION_TEXT_SP, "최근 기록"))
        runs.forEach { run ->
            val score = run.score?.let { "${it}점" } ?: if (run.endedAt == null) "끝나지 않음" else "체크 전"
            val course = run.course?.let { id -> " · " + (courses.firstOrNull { it.id == id }?.name ?: id) } ?: ""
            review.addView(text(BODY_TEXT_SP, "${modeName(run.mode)}$course · $score"))
        }
        val often = log.carItemCounts().entries.sortedByDescending { it.value }.take(OFTEN_LIMIT)
        if (often.isNotEmpty()) {
            review.addView(text(SECTION_TEXT_SP, "자주 틀린 항목 (차가 본 것)"))
            often.forEach { (item, count) -> review.addView(text(BODY_TEXT_SP, "${item.title} ${count}회")) }
        }
    }

    private fun courseLabel(): String = when (courseChoice) {
        0 -> "없음"
        1 -> "뽑기(덜 해 본 코스 먼저)"
        else -> courses.getOrNull(courseChoice - 2)?.name ?: "없음"
    }

    /** 뽑기는 완주 횟수가 가장 적은 코스 중에서 고릅니다. */
    private fun pickCourse(): String? = when (courseChoice) {
        0 -> null
        1 -> {
            val done = log.courseDoneCounts()
            val least = courses.minOfOrNull { done[it.id] ?: 0 }
            courses.filter { (done[it.id] ?: 0) == least }.randomOrNull()?.id
        }
        else -> courses.getOrNull(courseChoice - 2)?.id
    }

    /** 에뮬레이터에서는 위치를 adb로 넣으므로 모의 위치라고 밝힙니다. */
    private val mockNote: String
        get() = if (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) "\n(위치: 모의 위치)" else ""

    private fun modeName(mode: CoachMode) = if (mode == CoachMode.EXAM) "시험 모드" else "연습 모드"

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = BUTTON_TEXT_SP
        setOnClickListener { onClick() }
    }

    private fun text(size: Float, value: String = "") = TextView(this).apply {
        textSize = size
        text = value
    }

    private fun check(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = label
        textSize = BODY_TEXT_SP
        isChecked = checked
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }.also { it.visibility = View.VISIBLE }

    private companion object {
        const val PERMISSION_REQUEST = 1
        const val STATUS_TEXT_SP = 24f
        const val SECTION_TEXT_SP = 22f
        const val BODY_TEXT_SP = 18f
        const val BUTTON_TEXT_SP = 26f
        const val PADDING_PX = 48
        const val OFTEN_LIMIT = 3
    }
}
