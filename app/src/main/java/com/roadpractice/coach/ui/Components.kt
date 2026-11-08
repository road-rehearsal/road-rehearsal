package com.roadpractice.coach.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 점수 도로. 이 앱에서 가장 눈에 띄는 한 가지입니다.
 * 0~100을 도로 한 구간으로 그리고, 합격선 70점은 노란 중앙선, 점수는 그 위의 차 위치입니다.
 */
@Composable
fun ScoreRoad(score: Int, passLine: Int, carColor: Color, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    val target = (score.coerceIn(0, 100) / 100f)
    val pos by animateFloatAsState(target, tween(600), label = "score")
    val car by animateColorAsState(carColor, tween(300), label = "car")
    val asphalt = Road.Asphalt
    val laneWhite = Road.LaneWhite
    val laneYellow = Road.LaneYellow
    val sheet = Road.Sheet
    Column(modifier.semantics { contentDescription = "점수 ${score}점, 합격선 ${passLine}점" }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val r = size.height / 2
            drawRoundRect(asphalt, cornerRadius = CornerRadius(r, r))
            // 가장자리 실선과 가운데 점선
            val edge = size.height * 0.16f
            drawLine(laneWhite.copy(alpha = 0.55f), Offset(r, edge), Offset(size.width - r, edge), strokeWidth = 3f)
            drawLine(laneWhite.copy(alpha = 0.55f), Offset(r, size.height - edge), Offset(size.width - r, size.height - edge), strokeWidth = 3f)
            drawLine(
                laneWhite.copy(alpha = 0.7f), Offset(r, size.height / 2), Offset(size.width - r, size.height / 2),
                strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 22f)),
            )
            // 합격선: 노란 이중 실선
            val passX = r + (size.width - 2 * r) * passLine / 100f
            drawLine(laneYellow, Offset(passX - 5f, 4f), Offset(passX - 5f, size.height - 4f), strokeWidth = 5f)
            drawLine(laneYellow, Offset(passX + 5f, 4f), Offset(passX + 5f, size.height - 4f), strokeWidth = 5f)
            // 차: 위에서 본 둥근 사각형
            val carW = size.height * 0.95f
            val carH = size.height * 0.56f
            val cx = r + (size.width - 2 * r) * pos
            drawRoundRect(
                car, topLeft = Offset(cx - carW / 2, (size.height - carH) / 2), size = Size(carW, carH),
                cornerRadius = CornerRadius(carH * 0.35f, carH * 0.35f),
            )
            drawRoundRect(
                sheet.copy(alpha = 0.85f), topLeft = Offset(cx + carW * 0.12f, (size.height - carH) / 2 + carH * 0.18f),
                size = Size(carW * 0.2f, carH * 0.64f), cornerRadius = CornerRadius(6f, 6f),
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            val w = maxWidth
            Text("0", style = MaterialTheme.typography.labelMedium, color = Road.Graphite)
            Text(
                "합격선 $passLine", style = MaterialTheme.typography.labelMedium, color = Road.Ink,
                modifier = Modifier.offset(x = w * (passLine / 100f) - 44.dp),
            )
            Text("100", style = MaterialTheme.typography.labelMedium, color = Road.Graphite, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** 코스 진행. 지나온 길은 노란 선, 남은 길은 흰 점선입니다. */
@Composable
fun CourseRoad(fraction: Double, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.toFloat().coerceIn(0f, 1f), tween(800), label = "course")
    val asphalt = Road.Asphalt
    val laneWhite = Road.LaneWhite
    val laneYellow = Road.LaneYellow
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val r = size.height / 2
        drawRoundRect(asphalt, cornerRadius = CornerRadius(r, r))
        val y = size.height / 2
        val x0 = r
        val x1 = size.width - r
        val xf = x0 + (x1 - x0) * f
        drawLine(laneWhite.copy(alpha = 0.6f), Offset(xf, y), Offset(x1, y), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f)))
        drawLine(laneYellow, Offset(x0, y), Offset(xf, y), strokeWidth = 8f, cap = StrokeCap.Round)
        drawCircle(laneYellow, radius = size.height * 0.32f, center = Offset(xf, y))
        drawCircle(asphalt, radius = size.height * 0.14f, center = Offset(xf, y))
    }
}

/**
 * 회전 화살표. 도로 안내 표지처럼 굵은 흰 화살표를 그립니다.
 * @param kind "LEFT", "RIGHT", "UTURN"
 */
@Composable
fun TurnArrow(kind: String, modifier: Modifier = Modifier, color: Color = Road.LaneWhite) {
    val label = when (kind) { "LEFT" -> "좌회전"; "RIGHT" -> "우회전"; "STRAIGHT" -> "직진"; else -> "유턴" }
    Canvas(modifier.size(150.dp).semantics { contentDescription = "$label 화살표" }) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.16f
        val mirror = kind == "LEFT"
        fun x(v: Float) = if (mirror) w - v else v
        val path = Path()
        val head = Path()
        if (kind == "STRAIGHT") {
            // 아래에서 위로 곧게 올라가는 화살표
            path.moveTo(w * 0.5f, h * 0.94f)
            path.lineTo(w * 0.5f, h * 0.34f)
            head.moveTo(w * 0.26f, h * 0.38f)
            head.lineTo(w * 0.5f, h * 0.06f)
            head.lineTo(w * 0.74f, h * 0.38f)
            head.close()
        } else if (kind == "UTURN") {
            // 위로 올라가 반원을 돌아 아래로 내려오는 화살표
            path.moveTo(x(w * 0.70f), h * 0.92f)
            path.lineTo(x(w * 0.70f), h * 0.42f)
            path.arcTo(androidx.compose.ui.geometry.Rect(x(w * 0.70f) - w * 0.40f, h * 0.10f, x(w * 0.70f), h * 0.58f), 0f, -180f, false)
            path.lineTo(x(w * 0.30f), h * 0.62f)
            head.moveTo(x(w * 0.30f) - w * 0.17f, h * 0.58f)
            head.lineTo(x(w * 0.30f) + w * 0.17f, h * 0.58f)
            head.lineTo(x(w * 0.30f), h * 0.86f)
            head.close()
        } else {
            // 아래에서 올라와 오른쪽(왼쪽은 거울)으로 꺾이는 화살표
            path.moveTo(x(w * 0.32f), h * 0.94f)
            path.lineTo(x(w * 0.32f), h * 0.46f)
            path.quadraticTo(x(w * 0.32f), h * 0.30f, x(w * 0.48f), h * 0.30f)
            path.lineTo(x(w * 0.66f), h * 0.30f)
            head.moveTo(x(w * 0.62f), h * 0.10f)
            head.lineTo(x(w * 0.94f), h * 0.30f)
            head.lineTo(x(w * 0.62f), h * 0.50f)
            head.close()
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Butt, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(head, color)
    }
}

/** 제한속도 표지 단계: 0 지킴, 1 넘음(경고), 2 실격 기준을 넘음. */
fun speedLevel(speedKmh: Float?, limitKmh: Int, school: Boolean): Int {
    val v = speedKmh ?: return 0
    return when {
        v <= limitKmh -> 0
        // 별표 26 실격: 보호구역은 최고 속도 초과, 그 밖은 지정 최고 속도 10km/h 초과
        school || v > limitKmh + 10 -> 2
        else -> 1
    }
}

/**
 * 제한속도 표지(빨간 테 원 안 숫자). 넘으면 노란 바탕(경고), 실격 기준을 넘으면 빨간 바탕(11/7 사용자 결정).
 * 보호구역은 넘는 순간 실격 기준이라 바로 빨갛게 됩니다. 보호구역이면 아래에 "어린이보호구역"을 붙입니다.
 */
@Composable
fun SpeedLimitSign(limitKmh: Int, level: Int, school: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        when (level) { 2 -> Road.RedPen; 1 -> Road.LaneYellow; else -> Road.Sheet }, tween(250), label = "limit",
    )
    val text = if (level == 2) Road.Sheet else Road.Ink
    val what = when (level) { 2 -> ", 실격 기준을 넘었어요"; 1 -> ", 넘었어요"; else -> "" }
    Column(modifier.semantics { contentDescription = "제한속도 ${limitKmh}킬로미터$what" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(bg).border(14.dp, Road.RedPen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$limitKmh", style = MaterialTheme.typography.displayMedium, color = text)
        }
        // 주행 중 글자는 24 이상(Android 차량 앱 지침, 4_개발/안내_레퍼런스.md, 11/8 점검)
        if (school) Text("어린이보호구역", style = MaterialTheme.typography.titleMedium, color = Road.Ink, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 작은 표지. 상태나 출처를 붙일 때 씁니다. */
@Composable
fun Tag(text: String, color: Color = Road.Graphite, filled: Boolean = false, large: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (filled) color else Color.Transparent,
        border = if (filled) null else BorderStroke(1.5.dp, color),
    ) {
        Text(
            text, style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            color = if (filled) Road.Sheet else color,
            modifier = Modifier.padding(horizontal = if (large) 20.dp else 14.dp, vertical = if (large) 10.dp else 6.dp),
        )
    }
}

/** 판. 화면의 큰 덩어리를 담습니다. */
@Composable
fun Panel(modifier: Modifier = Modifier, color: Color = Road.Sheet, content: @Composable () -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = color, content = content)
}

/** 묶음 제목. 오른쪽에 개수나 합계를 둘 수 있습니다. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null, trailingColor: Color = Road.Graphite) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Road.Ink, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = trailingColor)
    }
}

/**
 * 모드 판. 연습과 시험의 차이(말하는가)를 한 문장으로 보여 줍니다.
 * 왼쪽 세로 띠의 색이 모드의 색입니다(연습: 표지판 파랑, 시험: 잉크).
 */
@Composable
fun ModeTile(title: String, line: String, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 260.dp), shape = MaterialTheme.shapes.large, color = Road.Sheet) {
        Column(Modifier.padding(sp.l).heightIn(min = 260.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.s)) {
                Text(title, style = MaterialTheme.typography.displayMedium, color = Road.Ink)
                Text(line, style = MaterialTheme.typography.bodyLarge, color = Road.Graphite)
            }
            // 판 전체가 눌리지만, 무엇이 일어나는지 이름 붙인 버튼 모양을 둡니다.
            Surface(shape = MaterialTheme.shapes.small, color = accent) {
                Text(
                    "$title 시작", style = MaterialTheme.typography.titleMedium, color = Road.Sheet,
                    modifier = Modifier.padding(horizontal = sp.m, vertical = 14.dp),
                )
            }
        }
    }
}

/** 하나만 고르는 줄(코스 선택). */
@Composable
fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // 내 기록 코스 이름이 길어 창을 넘을 수 있어 옆으로 밀 수 있게 둡니다.
    Row(modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (on) Road.Ink else Road.Sheet,
                border = if (on) null else BorderStroke(1.5.dp, Road.Rule),
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) }),
            ) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Road.Sheet else Road.Ink)
                }
            }
        }
    }
}

/** 채점 칸. 체크하면 빨간 펜 표시가 되고 감점이 빨갛게 바뀝니다. */
@Composable
fun MarkRow(label: String, points: Int?, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = sp.touch)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = sp.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PenBox(checked)
        Spacer(Modifier.width(sp.m))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Road.Ink, modifier = Modifier.weight(1f))
        if (points != null) {
            Text("−$points", style = MaterialTheme.typography.labelLarge, color = if (checked) Road.RedPen else Road.Graphite)
        }
    }
}

/** 빨간 펜으로 긋는 체크 칸. */
@Composable
private fun PenBox(checked: Boolean) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) Road.RedPen else Road.Sheet)
            .border(2.dp, if (checked) Road.RedPen else Road.Graphite, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            val mark = Road.Sheet
            Canvas(Modifier.size(22.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.55f)
                    lineTo(size.width * 0.4f, size.height * 0.85f)
                    lineTo(size.width * 0.95f, size.height * 0.15f)
                }
                drawPath(p, mark, style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }
    }
}

/** 차가 본 항목. 동승자가 보기에 틀렸으면 "제외"로 뺍니다. 뺀 줄은 줄을 긋습니다. */
@Composable
fun CarItemRow(number: Int, title: String, points: Int, excluded: Boolean, enabled: Boolean, onExclude: (Boolean) -> Unit) {
    val sp = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = sp.touch).padding(horizontal = sp.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (excluded) Road.Paper else Road.Ink)
                .border(1.5.dp, if (excluded) Road.Graphite else Road.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = if (excluded) Road.Graphite else Road.Sheet)
        }
        Spacer(Modifier.width(sp.m))
        Text(
            title, style = MaterialTheme.typography.bodyLarge,
            color = if (excluded) Road.Graphite else Road.Ink,
            textDecoration = if (excluded) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        Text("−$points", style = MaterialTheme.typography.labelLarge, color = if (excluded) Road.Graphite else Road.RedPen)
        if (enabled) {
            Spacer(Modifier.width(sp.m))
            // 차가 잘못 본 항목을 빼는 버튼. 눌린 상태는 채워서 보여 줍니다.
            Surface(
                shape = RoundedCornerShape(50),
                color = if (excluded) Road.Graphite else Road.Sheet,
                border = BorderStroke(1.5.dp, Road.Graphite),
                modifier = Modifier.heightIn(min = 56.dp).toggleable(value = excluded, role = Role.Switch, onValueChange = onExclude),
            ) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(if (excluded) "제외됨" else "제외", style = MaterialTheme.typography.labelLarge, color = if (excluded) Road.Sheet else Road.Graphite)
                }
            }
        }
    }
}

/** 큰 주 버튼. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 80.dp), shape = MaterialTheme.shapes.medium, color = Road.SignBlue) {
        Box(Modifier.padding(horizontal = 32.dp, vertical = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = Road.Sheet)
        }
    }
}
