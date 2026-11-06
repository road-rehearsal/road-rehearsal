package com.roadpractice.coach.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
fun HomeScreen(ui: HomeUi, onCourse: (Int) -> Unit, onStart: (CoachMode) -> Unit) {
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
            history(Modifier.weight(1f).fillMaxHeight())
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
                Text("아직 기록이 없어요. 첫 주행을 마치면 여기에 점수가 쌓여요.", style = MaterialTheme.typography.bodyMedium, color = Road.Graphite)
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
                        Text(modeName(run.mode) + (run.course?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Road.Ink)
                        if (run.note != null) Text(run.note, style = MaterialTheme.typography.labelMedium, color = Road.Graphite)
                    }
                }
            }
            if (ui.often.isNotEmpty()) {
                HorizontalDivider(color = Road.Rule, modifier = Modifier.padding(vertical = sp.s))
                Text("자주 틀린 항목", style = MaterialTheme.typography.titleMedium, color = Road.Ink)
                ui.often.forEach { (title, count) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(title, style = MaterialTheme.typography.bodyMedium, color = Road.Ink, modifier = Modifier.weight(1f))
                        Text("${count}회", style = MaterialTheme.typography.labelLarge, color = Road.RedPen)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 주행 중

data class DrivingUi(val mode: CoachMode, val course: String?, val fraction: Double?, val guide: String?, val mockLocation: Boolean)

/** 주행 중 화면. 감점은 보여 주지 않고(시험 모드 침묵), 큰 글자로 모드·코스·안내만 둡니다. 누를 것이 없습니다. */
@Composable
fun DrivingScreen(ui: DrivingUi) {
    val sp = LocalSpacing.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = sp.xl, vertical = sp.l),
        verticalArrangement = Arrangement.spacedBy(sp.l, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(sp.s), verticalAlignment = Alignment.CenterVertically) {
            Tag(modeName(ui.mode), if (ui.mode == CoachMode.EXAM) Road.Ink else Road.SignBlue, filled = true)
            if (ui.mockLocation) Tag("모의 위치")
        }
        Text(
            if (ui.mode == CoachMode.EXAM) "시험 중이에요" else "연습 중이에요",
            style = MaterialTheme.typography.displayMedium, color = Road.Ink,
        )
        if (ui.course != null) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.s)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(ui.course, style = MaterialTheme.typography.titleLarge, color = Road.Ink, modifier = Modifier.weight(1f))
                    Text("${((ui.fraction ?: 0.0) * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = Road.Ink)
                }
                CourseRoad(ui.fraction ?: 0.0)
            }
        }
        if (ui.guide != null) {
            Panel(Modifier.fillMaxWidth(), color = Road.Ink) {
                Text(ui.guide, style = MaterialTheme.typography.headlineMedium, color = Road.LaneWhite, modifier = Modifier.padding(sp.l))
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
    val summary: @Composable (Modifier) -> Unit = { m -> Summary(ui, marks, sheet, onMarks, onSave, m) }
    if (isWide()) {
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
private fun Summary(ui: ReviewUi, marks: ReviewMarks, sheet: ScoreSheet, onMarks: (ReviewMarks) -> Unit, onSave: () -> Unit, modifier: Modifier) {
    val sp = LocalSpacing.current
    val passed = sheet.passedLine
    Panel(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(sp.l), verticalArrangement = Arrangement.spacedBy(sp.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(sp.s), verticalAlignment = Alignment.CenterVertically) {
                Tag(modeName(ui.mode), if (ui.mode == CoachMode.EXAM) Road.Ink else Road.SignBlue, filled = true)
                Tag(ui.course?.let { "$it · ${if (ui.courseDone) "완주" else "완주 못 함"}" } ?: "코스 없음")
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${sheet.score}", style = MaterialTheme.typography.displayLarge, color = if (passed) Road.SignGreen else Road.RedPen)
                Text("점", style = MaterialTheme.typography.headlineMedium, color = Road.Ink, modifier = Modifier.padding(start = 6.dp, bottom = 14.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        sheet.disqualifications.isNotEmpty() -> "실격 사유가 있어요"
                        passed -> "합격선을 넘었어요"
                        else -> "합격선까지 ${ScoreSheet.PASS_SCORE - sheet.score}점"
                    },
                    style = MaterialTheme.typography.titleLarge, color = Road.Ink, textAlign = TextAlign.End,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
            ScoreRoad(sheet.score, ScoreSheet.PASS_SCORE, passed)
            Text(
                "차가 본 항목 −${sheet.carDeducted} · 동승자 체크 −${sheet.passengerDeducted}. 연습용 기준이며 공식 채점이 아니에요.",
                style = MaterialTheme.typography.bodyMedium, color = Road.Graphite,
            )
            if (ui.course == null) {
                Text("코스 없이 달려 서행·일시정지·횡단보도(37·38·43번)는 보지 않았어요.", style = MaterialTheme.typography.bodyMedium, color = Road.Graphite)
            }
            if (ui.interrupted) {
                Text("앱이 주행 중에 멈춰 그때까지의 기록만 있어요.", style = MaterialTheme.typography.bodyMedium, color = Road.RedPen)
            }
            SectionTitle("차가 본 항목", trailing = "${ui.car.size}개")
            if (ui.car.isEmpty()) Text("차가 본 실수가 없어요.", style = MaterialTheme.typography.bodyLarge, color = Road.Graphite)
            ui.car.forEachIndexed { i, item ->
                CarItemRow(item.number, item.title, item.points, i in marks.excluded, enabled = !ui.moving) { on ->
                    onMarks(marks.copy(excluded = if (on) marks.excluded + i else marks.excluded - i))
                }
            }
            sheet.disqualifications.forEach { title ->
                Text("실격 사유 · $title", style = MaterialTheme.typography.bodyLarge, color = Road.RedPen)
            }
            if (!ui.moving) {
                Spacer(Modifier.height(sp.s))
                PrimaryButton("저장하고 처음으로", onSave, Modifier.fillMaxWidth())
            }
        }
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
        item(key = "task-$task") { SectionTitle(task, trailing = items.count { it in marks.passenger }.takeIf { it > 0 }?.let { "${it}개" }, trailingColor = Road.RedPen) }
        items(items, key = { it.name }) { item ->
            MarkRow("${item.number}번 ${item.title}", item.points, item in marks.passenger, { on ->
                onMarks(marks.copy(passenger = if (on) marks.passenger + item else marks.passenger - item))
            })
        }
    }
    item(key = "dq") { SectionTitle("동승자가 본 실격 사유") }
    items(PassengerDisqualification.entries, key = { "dq-${it.name}" }) { reason ->
        MarkRow(reason.title, null, reason in marks.passengerDq, { on ->
            onMarks(marks.copy(passengerDq = if (on) marks.passengerDq + reason else marks.passengerDq - reason))
        })
    }
    item { Spacer(Modifier.height(48.dp)) }
}

/** 주행 중에 입력을 막을 때 보여 주는 판. */
@Composable
fun StopFirst(title: String, line: String, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Box(modifier.fillMaxSize().padding(sp.l), contentAlignment = Alignment.Center) {
        Panel(color = Road.Ink) {
            Column(Modifier.padding(sp.xl), verticalArrangement = Arrangement.spacedBy(sp.s)) {
                Box(Modifier.fillMaxWidth().height(8.dp).background(Road.LaneYellow))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Road.LaneWhite)
                Text(line, style = MaterialTheme.typography.bodyLarge, color = Road.LaneWhite.copy(alpha = 0.8f))
            }
        }
    }
}
