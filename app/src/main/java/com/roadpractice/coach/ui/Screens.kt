package com.roadpractice.coach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.roadpractice.coach.CoachMode
import com.roadpractice.coach.Disqualification
import com.roadpractice.coach.ExamItem
import com.roadpractice.coach.PassengerDisqualification
import com.roadpractice.coach.PassengerItem
import com.roadpractice.coach.PointResult
import com.roadpractice.coach.ScoreSheet

/** 창 폭이 넓으면(840dp 이상) 두 판으로, 좁으면 한 줄로 놓습니다. 앱 창 크기 기준입니다. */
@Composable
fun isWide(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

fun modeName(mode: CoachMode) = if (mode == CoachMode.EXAM) "시험 모드" else "연습 모드"

// ---------------------------------------------------------------- 처음 화면

data class RunRow(val mode: CoachMode, val course: String?, val score: Int?, val passed: Boolean?, val note: String?)

data class HomeUi(
    val courseOptions: List<String>,
    val courseChoice: Int,
    val runs: List<RunRow>,
    val often: List<Pair<String, Int>>,
    val moving: Boolean,
)

@Composable
fun HomeScreen(ui: HomeUi, onCourse: (Int) -> Unit, onStart: (CoachMode) -> Unit, onItems: () -> Unit = {}) {
    val sp = LocalSpacing.current
    if (ui.moving) {
        StopFirst("주행 중에는 설정할 수 없어요", "차를 세운 뒤 모드를 고르세요.")
        return
    }
    val start: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(sp.m)) {
            Text("도로주행 연습 코치", style = MaterialTheme.typography.headlineLarge, color = Road.Ink)
            Text(
                "실제 시험 코스와 채점 항목으로, 차가 조작을 봐 드려요.",
                style = MaterialTheme.typography.bodyLarge, color = Road.Graphite,
            )
            Spacer(Modifier.height(sp.xs))
            Text("코스", style = MaterialTheme.typography.titleMedium, color = Road.Ink)
            ChoiceRow(ui.courseOptions, ui.courseChoice, onCourse)
            Spacer(Modifier.height(sp.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(sp.m)) {
                ModeTile("연습", "실수한 순간 한 문장으로 알려 줘요", Road.SignBlue, { onStart(CoachMode.PRACTICE) }, Modifier.weight(1f))
                ModeTile("시험", "끝날 때까지 말하지 않고, 끝나면 결과표", Road.Ink, { onStart(CoachMode.EXAM) }, Modifier.weight(1f))
            }
            ItemsButton(onItems, Modifier.fillMaxWidth())
            Text(
                "연습용 기준이며 공식 채점이 아니에요. 면허 2년이 지난 동승자와 타고, 연습 표지를 붙이고, 보험 범위를 확인해 주세요.",
                style = MaterialTheme.typography.bodyMedium, color = Road.Graphite,
            )
            Text(
                "코스 좌표 © OpenStreetMap contributors (ODbL). 공단 제공 자료가 아니며, 가상 지점은 시연용이에요.",
                style = MaterialTheme.typography.labelMedium, color = Road.Graphite,
            )
        }
    }
    val history: @Composable (Modifier) -> Unit = { m -> History(ui, m) }
    if (isWide()) {
        Row(Modifier.fillMaxSize().padding(sp.l), horizontalArrangement = Arrangement.spacedBy(sp.l)) {
            start(Modifier.weight(1.5f).verticalScroll(rememberScrollState()))
            history(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sp.m), verticalArrangement = Arrangement.spacedBy(sp.l)) {
            start(Modifier)
            history(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun History(ui: HomeUi, modifier: Modifier) {
    val sp = LocalSpacing.current
    Panel(modifier) {
        Column(Modifier.padding(sp.l), verticalArrangement = Arrangement.spacedBy(sp.s)) {
            Text("지난 연습", style = MaterialTheme.typography.titleLarge, color = Road.Ink)
            if (ui.runs.isEmpty()) {
                Text("아직 기록이 없어요", style = MaterialTheme.typography.titleMedium, color = Road.Ink, modifier = Modifier.padding(top = sp.s))
                Text(
                    "코스를 고르고 연습이나 시험을 한 번 마치면, 점수와 자주 틀린 항목이 여기에 쌓여요.",
                    style = MaterialTheme.typography.bodyLarge, color = Road.Graphite,
                )
            }
            ui.runs.forEach { run ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        run.score?.toString() ?: "–",
                        style = MaterialTheme.typography.headlineMedium,
                        color = when (run.passed) { true -> Road.SignGreen; false -> Road.RedPen; null -> Road.Graphite },
                        modifier = Modifier.widthIn(min = 72.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(modeName(run.mode) + (run.course?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, color = Road.Ink)
                        if (run.note != null) Text(run.note, style = MaterialTheme.typography.labelMedium, color = Road.Graphite)
                    }
                }
            }
            if (ui.often.isNotEmpty()) {
                HorizontalDivider(color = Road.Rule, modifier = Modifier.padding(vertical = sp.s))
                Text("자주 틀린 항목", style = MaterialTheme.typography.titleMedium, color = Road.Ink)
                ui.often.forEach { (title, count) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, color = Road.Ink, modifier = Modifier.weight(1f))
                        Text("${count}회", style = MaterialTheme.typography.labelLarge, color = Road.RedPen)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 시작 대기

data class ReadyUi(val mode: CoachMode, val course: String?, val moving: Boolean)

/**
 * 모드를 고른 뒤 기어를 P에서 D로 바꾸기 전까지의 화면.
 * 이 화면이 없으면 시작을 눌러도 처음 화면이 그대로라 눌리지 않은 것처럼 보였습니다(11/6 사용자 검토).
 */
@Composable
fun ReadyScreen(ui: ReadyUi, onCancel: () -> Unit) {
    val sp = LocalSpacing.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = sp.xl, vertical = sp.l),
        verticalArrangement = Arrangement.spacedBy(sp.l, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(sp.s), verticalAlignment = Alignment.CenterVertically) {
            Tag(modeName(ui.mode), if (ui.mode == CoachMode.EXAM) Road.Ink else Road.SignBlue, filled = true, large = true)
            Tag(ui.course ?: "코스 없이", large = true)
        }
        Panel(Modifier.fillMaxWidth().heightIn(min = 180.dp), color = Road.Asphalt) {
            Box(Modifier.padding(sp.l), contentAlignment = Alignment.CenterStart) {
                Text("기어를 D로 바꾸면 시작해요", style = MaterialTheme.typography.displayMedium, color = Road.LaneWhite)
            }
        }
        Text(
            if (ui.mode == CoachMode.EXAM) "시험 모드는 끝날 때까지 감점을 말하지 않아요. 끝나면 P로 바꾸고 운전석 문을 열어 주세요."
            else "실수하면 그 순간 한 문장으로 알려 드려요. 끝나면 P로 바꾸고 운전석 문을 열어 주세요.",
            style = MaterialTheme.typography.bodyLarge, color = Road.Graphite,
        )
        if (!ui.moving) {
            Surface(
                onClick = onCancel, shape = MaterialTheme.shapes.medium, color = Road.Sheet,
                border = BorderStroke(1.5.dp, Road.Graphite), modifier = Modifier.heightIn(min = 72.dp),
            ) {
                Box(Modifier.padding(horizontal = sp.l, vertical = sp.m), contentAlignment = Alignment.Center) {
                    Text("취소하고 처음으로", style = MaterialTheme.typography.titleMedium, color = Road.Ink)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 주행 중

data class DrivingUi(
    val mode: CoachMode,
    val course: String?,
    val fraction: Double?,
    val guide: String?,
    val mockLocation: Boolean,
    /** 기록 파일 재생 중(시연용). "모의 위치" 대신 이것을 밝힙니다. */
    val replaying: Boolean = false,
    /** 다음 회전: "LEFT"·"RIGHT"·"UTURN"과 남은 거리(m). 300m 안일 때만. */
    val turnKind: String? = null,
    val turnRemainingM: Int? = null,
    /** 바로 이어지는 다음 회전("LEFT" 등). 있으면 작은 화살표를 하나 더 띄웁니다(11/8, FHWA 연속 회전). */
    val turnThen: String? = null,
    /** 직진 구간: 다음 회전(없으면 도착)까지 남은 m. 300m 안에 회전이 있거나 코스를 벗어났으면 null. */
    val straightM: Int? = null,
    /** 직진 뒤 다음 회전 이름("우회전" 등). 남은 회전이 없으면 null(도착까지). */
    val straightNext: String? = null,
    val zoneLimitKmh: Int? = null,
    val zoneSchool: Boolean = false,
    val speedKmh: Float? = null,
)

private fun turnLabel(kind: String) = when (kind) { "LEFT" -> "좌회전"; "RIGHT" -> "우회전"; else -> "유턴" }

/** 남은 거리 글자: 1km 넘으면 0.1km 단위, 아니면 10m 단위. */
fun distanceText(m: Int): String = if (m >= 1000) "%.1fkm".format(m / 1000.0) else "${(m / 10) * 10}m"

/** 주행 중 화면. 감점은 보여 주지 않고(시험 모드 침묵), 큰 글자로 모드·코스·안내만 둡니다. 누를 것이 없습니다. */
@Composable
fun DrivingScreen(ui: DrivingUi) {
    val sp = LocalSpacing.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = sp.xl, vertical = sp.l),
        verticalArrangement = Arrangement.spacedBy(sp.l, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(sp.s), verticalAlignment = Alignment.CenterVertically) {
            Tag(modeName(ui.mode), if (ui.mode == CoachMode.EXAM) Road.Ink else Road.SignBlue, filled = true, large = true)
            Text(
                if (ui.mode == CoachMode.EXAM) "끝날 때까지 감점은 말하지 않아요" else "실수하면 바로 한 문장으로 알려 줘요",
                style = MaterialTheme.typography.bodyLarge, color = Road.Graphite, modifier = Modifier.weight(1f),
            )
            // 연출을 숨기지 않습니다: 기록 재생이면 "시연용 재생", adb로 위치를 넣으면 "모의 위치"
            when {
                ui.replaying -> Tag("시연용 재생", Road.RedPen, filled = true, large = true)
                ui.mockLocation -> Tag("모의 위치", large = true)
            }
        }
        if (ui.course != null) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.s)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(ui.course, style = MaterialTheme.typography.titleLarge, color = Road.Ink, modifier = Modifier.weight(1f))
                    Text("${((ui.fraction ?: 0.0) * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = Road.Ink)
                }
                CourseRoad(ui.fraction ?: 0.0)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sp.l), verticalAlignment = Alignment.CenterVertically) {
            Panel(Modifier.weight(1f).heightIn(min = 180.dp), color = Road.Asphalt) {
                Row(
                    Modifier.padding(horizontal = sp.l, vertical = sp.l),
                    horizontalArrangement = Arrangement.spacedBy(sp.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 다음 회전이 300m 안이면 화살표와 남은 거리를 크게. 거리는 위치가 바뀔 때마다 줄어듭니다.
                    if (ui.turnKind != null && ui.turnRemainingM != null) {
                        TurnArrow(ui.turnKind)
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (ui.turnRemainingM <= 30) "지금" else "${(ui.turnRemainingM / 10) * 10}m",
                                style = MaterialTheme.typography.displayLarge, color = Road.LaneYellow,
                            )
                            Text(
                                turnLabel(ui.turnKind) + (ui.turnThen?.let { " 뒤 바로 ${turnLabel(it)}" } ?: ""),
                                style = MaterialTheme.typography.headlineMedium, color = Road.LaneWhite,
                            )
                        }
                        // 연속 회전이면 다음 화살표를 작게 하나 더(두 번째임을 흐린 색으로)
                        ui.turnThen?.let { TurnArrow(it, Modifier.size(90.dp), color = Road.LaneWhite.copy(alpha = 0.7f)) }
                    } else if (ui.straightM != null) {
                        // 직진 구간에서도 화살표로 계속 방향을 보여 줍니다(11/7 사용자 요청).
                        TurnArrow("STRAIGHT")
                        Column(Modifier.weight(1f)) {
                            Text("직진", style = MaterialTheme.typography.displayLarge, color = Road.LaneWhite)
                            Text(
                                (ui.straightNext?.let { "다음 ${it}까지 " } ?: "도착까지 ") + distanceText(ui.straightM),
                                style = MaterialTheme.typography.headlineMedium, color = Road.LaneYellow,
                            )
                        }
                    } else {
                        Text(
                            ui.guide ?: if (ui.course != null) "${ui.course}를 따라가요" else "차의 조작을 보고 있어요",
                            style = MaterialTheme.typography.displayMedium,
                            color = if (ui.guide != null) Road.LaneWhite else Road.LaneWhite.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            // 제한속도·보호구역 구간 안이면 표지. 넘으면 노랑(경고), 실격 기준을 넘으면 빨강
            if (ui.zoneLimitKmh != null) {
                SpeedLimitSign(ui.zoneLimitKmh, speedLevel(ui.speedKmh, ui.zoneLimitKmh, ui.zoneSchool), school = ui.zoneSchool)
            }
        }
        Text("끝나면 P로 바꾸고 운전석 문을 열어 주세요", style = MaterialTheme.typography.bodyLarge, color = Road.Graphite)
    }
}

// ---------------------------------------------------------------- 결과표

data class ReviewUi(
    val runId: Long,
    val mode: CoachMode,
    val course: String?,
    val courseDone: Boolean,
    val interrupted: Boolean,
    val car: List<ExamItem>,
    val carDq: List<Disqualification>,
    val moving: Boolean,
    /** 코스 지점별 결과(회전 안내, 횡단보도 정지, 구간 최고 속도). */
    val points: List<PointResult> = emptyList(),
    /** 같은 코스 지난번과 비교한 한 줄. 지난 기록이 없으면 null. */
    val compare: String? = null,
)

data class ReviewMarks(
    val excluded: Set<Int> = emptySet(),
    val passenger: Set<PassengerItem> = emptySet(),
    val passengerDq: Set<PassengerDisqualification> = emptySet(),
)

@Composable
fun ReviewScreen(ui: ReviewUi, marks: ReviewMarks, onMarks: (ReviewMarks) -> Unit, onSave: () -> Unit) {
    val sp = LocalSpacing.current
    val sheet = ScoreSheet.build(ui.car, marks.excluded, marks.passenger.toList(), ui.carDq, marks.passengerDq.toList())
    val wide = isWide()
    // 좁은 창에서는 요약이 바깥 목록 안에 들어가므로 스스로 스크롤하면 안 됩니다(무한 높이로 앱이 멈춤, 11/6 실측).
    val summary: @Composable (Modifier) -> Unit = { m -> Summary(ui, marks, sheet, onMarks, onSave, m, scrollable = wide) }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(sp.l), horizontalArrangement = Arrangement.spacedBy(sp.l)) {
            summary(Modifier.weight(1f).fillMaxHeight())
            if (ui.moving) StopFirst("주행 중에는 체크할 수 없어요", "차를 세우면 동승자 체크가 다시 보여요.", Modifier.weight(1f))
            else Panel(Modifier.weight(1f).fillMaxHeight()) {
                LazyColumn(Modifier.padding(horizontal = sp.m), state = rememberLazyListState(), contentPadding = PaddingValues(vertical = sp.l)) {
                    passengerSheet(marks, onMarks)
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = sp.m), state = rememberLazyListState()) {
            item { summary(Modifier.fillMaxWidth().padding(vertical = sp.m)) }
            if (ui.moving) item { StopFirst("주행 중에는 체크할 수 없어요", "차를 세우면 동승자 체크가 다시 보여요.") }
            else passengerSheet(marks, onMarks)
        }
    }
}

@Composable
private fun Summary(
    ui: ReviewUi, marks: ReviewMarks, sheet: ScoreSheet, onMarks: (ReviewMarks) -> Unit, onSave: () -> Unit,
    modifier: Modifier, scrollable: Boolean,
) {
    val sp = LocalSpacing.current
    val passed = sheet.passedLine
    val disqualified = sheet.disqualifications.isNotEmpty()
    // 중간에 멈춘 기록은 합격·불합격을 말하지 않습니다(남은 구간을 모름).
    val tone = when {
        ui.interrupted -> Road.Graphite
        passed -> Road.SignGreen
        else -> Road.RedPen
    }
    val verdict = when {
        ui.interrupted -> "기록이 중간에 끊겼어요"
        disqualified -> "실격 사유가 있어요"
        // 합격 판정처럼 읽히지 않게 "연습 기준"으로 씁니다(11/9 설명 영상 이해도 시험 지적, 사용자 결정).
        passed -> "연습 기준 ${ScoreSheet.PASS_SCORE}점을 넘었어요"
        else -> "연습 기준 ${ScoreSheet.PASS_SCORE}점까지 ${ScoreSheet.PASS_SCORE - sheet.score}점"
    }
    Panel(modifier) {
        val scroll = rememberScrollState()
        Column(
            (if (scrollable) Modifier.verticalScroll(scroll) else Modifier).padding(sp.l),
            verticalArrangement = Arrangement.spacedBy(sp.s),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(sp.s), verticalAlignment = Alignment.CenterVertically) {
                Tag(modeName(ui.mode), if (ui.mode == CoachMode.EXAM) Road.Ink else Road.SignBlue, filled = true)
                Tag(ui.course?.let { "$it · ${if (ui.courseDone) "완주" else "완주 못 함"}" } ?: "코스 없음")
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${sheet.score}", style = MaterialTheme.typography.displayLarge, color = tone)
                Text("점", style = MaterialTheme.typography.headlineMedium, color = Road.Ink, modifier = Modifier.padding(start = 6.dp, bottom = 14.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    verdict,
                    style = MaterialTheme.typography.titleLarge, color = if (disqualified && !ui.interrupted) Road.RedPen else Road.Ink, textAlign = TextAlign.End,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
            ScoreRoad(sheet.score, ScoreSheet.PASS_SCORE, tone)
            if (disqualified) {
                // 실격은 점수와 상관없이 결과를 정하므로 점수 도로 바로 아래에 둡니다.
                Surface(shape = MaterialTheme.shapes.medium, color = Road.Sheet, border = BorderStroke(2.dp, Road.RedPen), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(sp.m), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("실격 사유", style = MaterialTheme.typography.labelLarge, color = Road.RedPen)
                        sheet.disqualifications.forEach { Text(it, style = MaterialTheme.typography.titleMedium, color = Road.Ink) }
                    }
                }
            }
            Text(
                "차가 본 항목 −${sheet.carDeducted} · 동승자 체크 −${sheet.passengerDeducted}. 연습용 기준이며 공식 채점이 아니에요.",
                style = MaterialTheme.typography.bodyMedium, color = Road.Graphite,
            )
            if (ui.course == null) {
                Text("코스 없이 달려 서행·일시정지(37·38번)는 보지 않았어요.", style = MaterialTheme.typography.bodyMedium, color = Road.Graphite)
            }
            if (ui.interrupted) {
                Text("앱이 주행 중에 멈춰 그때까지의 기록만 있어요. 점수는 참고로만 보세요.", style = MaterialTheme.typography.bodyLarge, color = Road.Ink)
            }
            SectionTitle("차가 본 항목", trailing = "${ui.car.size}개")
            if (ui.car.isEmpty()) Text("차가 본 실수가 없어요.", style = MaterialTheme.typography.bodyLarge, color = Road.Graphite)
            ui.car.forEachIndexed { i, item ->
                CarItemRow(item.number, item.title, item.points, i in marks.excluded, enabled = !ui.moving) { on ->
                    onMarks(marks.copy(excluded = if (on) marks.excluded + i else marks.excluded - i))
                }
            }
            if (ui.compare != null) {
                Surface(shape = MaterialTheme.shapes.medium, color = Road.Paper, modifier = Modifier.fillMaxWidth()) {
                    Text(ui.compare, style = MaterialTheme.typography.titleMedium, color = Road.Ink, modifier = Modifier.padding(sp.m))
                }
            }
            if (ui.points.isNotEmpty()) {
                val ok = ui.points.count { it.ok == true }
                val judged = ui.points.count { it.ok != null }
                SectionTitle("코스 지점", trailing = "${ok}/${judged} 지킴")
                ui.points.forEach { PointRow(it) }
            }
            if (!ui.moving) {
                Spacer(Modifier.height(sp.s))
                PrimaryButton("저장하고 처음으로", onSave, Modifier.fillMaxWidth())
            }
        }
    }
}

/** 코스 지점 한 줄: 표시(지킴 ✓ / 못 지킴 ✕ / 안내만 ·), 이름, 설명. */
@Composable
private fun PointRow(p: PointResult) {
    val sp = LocalSpacing.current
    val (mark, color) = when (p.ok) { true -> "✓" to Road.SignGreen; false -> "✕" to Road.RedPen; null -> "·" to Road.Graphite }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = sp.s), verticalAlignment = Alignment.CenterVertically) {
        Text(mark, style = MaterialTheme.typography.titleLarge, color = color, modifier = Modifier.widthIn(min = 44.dp))
        Text(p.name, style = MaterialTheme.typography.bodyLarge, color = Road.Ink, modifier = Modifier.weight(1f))
        Text(p.detail, style = MaterialTheme.typography.labelLarge, color = if (p.ok == false) Road.RedPen else Road.Graphite)
    }
}

/** 동승자 체크. 과제별로 묶고, 실격 사유는 맨 아래에 둡니다. */
private fun LazyListScope.passengerSheet(marks: ReviewMarks, onMarks: (ReviewMarks) -> Unit) {
    item {
        Column(Modifier.padding(bottom = 8.dp)) {
            Text("동승자 체크", style = MaterialTheme.typography.titleLarge, color = Road.Ink)
            Text("차가 볼 수 없는 항목이에요. 정차한 뒤 본 것만 체크하세요.", style = MaterialTheme.typography.bodyMedium, color = Road.Graphite)
        }
    }
    PassengerItem.entries.groupBy { it.task }.forEach { (task, items) ->
        item(key = "task-$task") { TaskTitle(task, items.count { it in marks.passenger }) }
        items(items, key = { it.name }) { item ->
            MarkRow("${item.number}번 ${item.title}", item.points, item in marks.passenger, { on ->
                onMarks(marks.copy(passenger = if (on) marks.passenger + item else marks.passenger - item))
            })
        }
    }
    item(key = "dq") { TaskTitle("동승자가 본 실격 사유", marks.passengerDq.size) }
    items(PassengerDisqualification.entries, key = { "dq-${it.name}" }) { reason ->
        MarkRow(reason.title, null, reason in marks.passengerDq, { on ->
            onMarks(marks.copy(passengerDq = if (on) marks.passengerDq + reason else marks.passengerDq - reason))
        })
    }
    item { Spacer(Modifier.height(48.dp)) }
}

/** 동승자 체크의 과제 제목. 항목(24sp)과 구분되게 작고 회색으로, 위쪽 여백을 크게 둡니다. */
@Composable
private fun TaskTitle(text: String, checked: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 6.dp, start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Road.Graphite, modifier = Modifier.weight(1f))
        if (checked > 0) Text("${checked}개 체크", style = MaterialTheme.typography.labelMedium, color = Road.RedPen)
    }
    HorizontalDivider(color = Road.Rule, modifier = Modifier.padding(horizontal = 12.dp))
}

/** 주행 중에 입력을 막을 때 보여 주는 판. */
@Composable
fun StopFirst(title: String, line: String, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Box(modifier.fillMaxSize().padding(sp.l), contentAlignment = Alignment.Center) {
        Panel(color = Road.Asphalt) {
            Column(Modifier.padding(sp.xl), verticalArrangement = Arrangement.spacedBy(sp.s)) {
                Box(Modifier.fillMaxWidth().height(8.dp).background(Road.LaneYellow))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Road.LaneWhite)
                Text(line, style = MaterialTheme.typography.bodyLarge, color = Road.LaneWhite.copy(alpha = 0.8f))
            }
        }
    }
}
