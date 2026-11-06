package com.roadpractice.coach

import android.Manifest
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
        requestPermissions(
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                "android.car.permission.CAR_SPEED",
                "pleos.car.permission.FUSED_LOCATION",
            ),
            PERMISSION_REQUEST,
        )
    }

    override fun onStart() {
        super.onStart()
        CoachService.listener = { render(it) }
        render(CoachService.latest)
    }

    override fun onStop() {
        CoachService.listener = null
        super.onStop()
    }

    override fun onDestroy() {
        log.close()
        super.onDestroy()
    }

    private fun render(s: CoachService.Snapshot?) {
        val moving = s?.state?.isMoving == true
        when {
            s != null && s.started && !s.ended -> showDriving(s)
            s != null && s.ended && s.runId != CoachService.NO_RUN -> showReview(s.runId, moving)
            else -> {
                val pending = if (s == null) log.latestUnreviewedEnded() else null
                if (pending != null) showReview(pending.id, moving) else showHome(s)
            }
        }
    }

    private fun showHome(s: CoachService.Snapshot?) {
        reviewRunId = CoachService.NO_RUN
        review.removeAllViews()
        status.text = when (s) {
            null -> "연습용 앱입니다. 공식 채점이 아니며 합격을 보장하지 않습니다.\n" +
                "면허 2년이 지난 동승자와 함께 타고, 연습 표지를 붙이고, 보험 범위를 확인해 주세요."
            else -> "${modeName(s.mode)} 준비됨. 기어를 P에서 바꾸면 시작합니다."
        }
        controls.removeAllViews()
        controls.addView(button("연습 모드") { CoachService.start(this, CoachMode.PRACTICE) })
        controls.addView(button("시험 모드") { CoachService.start(this, CoachMode.EXAM) })
        if (s != null) controls.addView(button("끝내기") { CoachService.stop(this) })
        addHistory()
    }

    private fun showDriving(s: CoachService.Snapshot) {
        reviewRunId = CoachService.NO_RUN
        review.removeAllViews()
        controls.removeAllViews()
        status.text = "${modeName(s.mode)} 주행 중\n끝나면 P로 바꾸고 운전석 문을 열어 주세요."
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
        status.text = "결과표 (연습용 기준, 공식 채점 아님)"
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
            review.addView(text(BODY_TEXT_SP, "${modeName(run.mode)} · $score"))
        }
        val often = log.carItemCounts().entries.sortedByDescending { it.value }.take(OFTEN_LIMIT)
        if (often.isNotEmpty()) {
            review.addView(text(SECTION_TEXT_SP, "자주 틀린 항목 (차가 본 것)"))
            often.forEach { (item, count) -> review.addView(text(BODY_TEXT_SP, "${item.title} ${count}회")) }
        }
    }

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
