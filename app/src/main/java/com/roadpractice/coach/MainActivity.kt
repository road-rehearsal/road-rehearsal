package com.roadpractice.coach

import android.Manifest
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 모드 선택, 진행 상황, 간단한 결과. 코스, 동승자 체크, 기록은 다음 단계에서 추가합니다.
 * 주행 중에는 감점을 화면에 보여 주지 않습니다.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var practice: Button
    private lateinit var exam: Button
    private lateinit var stop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = STATUS_TEXT_SP
            gravity = Gravity.CENTER
        }
        practice = button("연습 모드") { CoachService.start(this, CoachMode.PRACTICE) }
        exam = button("시험 모드") { CoachService.start(this, CoachMode.EXAM) }
        stop = button("끝내기") { CoachService.stop(this) }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
                addView(status)
                addView(practice)
                addView(exam)
                addView(stop)
            },
        )
        requestPermissions(
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                "android.car.permission.CAR_SPEED",
                "pleos.car.permission.FUSED_LOCATION",
            ),
            PERMISSION_REQUEST,
        )
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = BUTTON_TEXT_SP
        setOnClickListener { onClick() }
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

    private fun render(s: CoachService.Snapshot?) {
        val inProgress = s != null && !s.ended
        practice.visibility = if (inProgress && s?.started == true) View.GONE else View.VISIBLE
        exam.visibility = practice.visibility
        stop.visibility = if (s != null) View.VISIBLE else View.GONE
        status.text = when {
            s == null -> "연습용 앱입니다. 공식 채점이 아니며 합격을 보장하지 않습니다.\n면허 2년이 지난 동승자와 함께 타고, 연습 표지를 붙이고, 보험 범위를 확인해 주세요."
            s.ended -> resultText(s)
            s.started -> "${modeName(s.mode)} 주행 중"
            else -> "${modeName(s.mode)} 준비됨. 기어를 P에서 바꾸면 시작합니다."
        }
    }

    private fun modeName(mode: CoachMode) = if (mode == CoachMode.EXAM) "시험 모드" else "연습 모드"

    private fun resultText(s: CoachService.Snapshot): String {
        val r = s.result
        val verdict = when {
            r.disqualified.isNotEmpty() -> "실격 사유 있음"
            r.passedLine -> "합격선 통과"
            else -> "합격선 미달"
        }
        val lines = r.deductions.groupingBy { it }.eachCount().entries.joinToString("\n") { (item, count) ->
            "${item.title} ${count}회 -${item.points * count}"
        }
        val out = r.disqualified.joinToString("\n") { "실격: ${it.title}" }
        return listOf("차가 본 항목 기준 ${r.score}점 · $verdict", lines, out, "연습용 기준이며 공식 채점이 아닙니다.")
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private companion object {
        const val PERMISSION_REQUEST = 1
        const val STATUS_TEXT_SP = 24f
        const val BUTTON_TEXT_SP = 28f
        const val PADDING_PX = 48
    }
}
