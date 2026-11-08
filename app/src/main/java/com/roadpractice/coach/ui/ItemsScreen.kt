package com.roadpractice.coach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roadpractice.coach.CatalogItem
import com.roadpractice.coach.ItemCatalog
import com.roadpractice.coach.Judge

/** 판정 방식 색: 차 = 표지 파랑, 코스 = 표지 초록, 동승자 = 회색 테, 보지 않음 = 흐린 회색. */
@Composable
private fun judgeColor(j: Judge): Color = when (j) {
    Judge.CAR -> Road.SignBlue
    Judge.COURSE -> Road.SignGreen
    Judge.PASSENGER -> Road.Graphite
    Judge.NONE -> Road.Rule
}

/**
 * 전체 채점 항목(별표 26의 46개와 실격 사유 11개). 정차 중에만 엽니다(도로교통법 제49조: 운전 중 화면 조작 금지).
 * 위에서 판정 방식으로 거르고, 아래는 과제별로 묶습니다. 움직임·자동 스크롤 없음.
 */
@Composable
fun ItemsScreen(onBack: () -> Unit) {
    val sp = LocalSpacing.current
    val filters = listOf(null, Judge.CAR, Judge.COURSE, Judge.PASSENGER)
    var pick by rememberSaveable { mutableIntStateOf(0) }
    val filter = filters[pick]
    val shown = ItemCatalog.items.filter { filter == null || it.judge == filter }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = sp.l),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = sp.l, bottom = sp.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("채점 항목 46개", style = MaterialTheme.typography.headlineLarge, color = Road.Ink)
                    Text("도로교통법 시행규칙 별표 26(채점기준). 이 앱은 연습용이며 공식 채점이 아니에요.", style = MaterialTheme.typography.bodyLarge, color = Road.Graphite)
                }
                Surface(onClick = onBack, shape = MaterialTheme.shapes.medium, color = Road.Sheet, modifier = Modifier.heightIn(min = 76.dp).widthIn(min = 160.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = sp.l, vertical = sp.m)) {
                        Text("닫기", style = MaterialTheme.typography.titleLarge, color = Road.Ink)
                    }
                }
            }
        }
        item {
            // 한눈에: 판정 방식별 개수
            Row(Modifier.fillMaxWidth().padding(vertical = sp.s), horizontalArrangement = Arrangement.spacedBy(sp.s)) {
                Judge.entries.forEach { j ->
                    Summary(j, ItemCatalog.count(j), Modifier.weight(1f))
                }
                SummaryBox("실격 사유", ItemCatalog.disqualifications.size, Road.RedPen, Modifier.weight(1f))
            }
        }
        item {
            ChoiceRow(
                listOf("전체 46", "차가 자동 ${ItemCatalog.count(Judge.CAR)}", "코스 지점 ${ItemCatalog.count(Judge.COURSE)}", "동승자 체크 ${ItemCatalog.count(Judge.PASSENGER)}"),
                pick, { pick = it }, Modifier.padding(vertical = sp.s),
            )
        }
        shown.groupBy { it.task }.forEach { (task, list) ->
            item(key = "t-$task") {
                Text(task, style = MaterialTheme.typography.titleMedium, color = Road.Graphite, modifier = Modifier.padding(top = sp.l, bottom = 6.dp))
                HorizontalDivider(color = Road.Rule)
            }
            items(list, key = { "i-${it.number}" }) { ItemRow(it) }
        }
        if (filter == null) {
            item(key = "dq") {
                Text("실격 사유 11개 (점수와 상관없이 불합격)", style = MaterialTheme.typography.titleMedium, color = Road.RedPen, modifier = Modifier.padding(top = sp.xl, bottom = 6.dp))
                HorizontalDivider(color = Road.Rule)
            }
            items(ItemCatalog.disqualifications, key = { "d-${it.title}" }) { dq ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dq.title, style = MaterialTheme.typography.bodyLarge, color = Road.Ink)
                        dq.how?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Road.Graphite) }
                    }
                    JudgeTag(dq.judge)
                }
            }
        }
        item { Box(Modifier.heightIn(min = 64.dp)) }
    }
}

@Composable
private fun Summary(j: Judge, n: Int, modifier: Modifier) = SummaryBox(j.label, n, judgeColor(j).let { if (j == Judge.NONE) Road.Graphite else it }, modifier)

@Composable
private fun SummaryBox(label: String, n: Int, color: Color, modifier: Modifier) {
    val sp = LocalSpacing.current
    Panel(modifier) {
        Column(Modifier.padding(sp.m)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 6.dp).background(color))
            Text("$n", style = MaterialTheme.typography.displayMedium, color = color, modifier = Modifier.padding(top = sp.s))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Road.Ink)
        }
    }
}

@Composable
private fun ItemRow(item: CatalogItem) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${item.number}", style = MaterialTheme.typography.titleLarge, color = Road.Graphite, modifier = Modifier.widthIn(min = 56.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = if (item.judge == Judge.NONE) Road.Graphite else Road.Ink)
            item.how?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Road.Graphite) }
        }
        Text("−${item.points}", style = MaterialTheme.typography.titleLarge, color = Road.RedPen, modifier = Modifier.widthIn(min = 64.dp).padding(end = 12.dp))
        JudgeTag(item.judge)
    }
}

@Composable
private fun JudgeTag(j: Judge) {
    val c = judgeColor(j)
    when (j) {
        Judge.CAR, Judge.COURSE -> Tag(j.label, c, filled = true, large = true)
        Judge.PASSENGER -> Tag(j.label, c, large = true)
        Judge.NONE -> Surface(shape = RoundedCornerShape(50), color = Road.Paper) {
            Text(j.label, style = MaterialTheme.typography.labelLarge, color = Road.Graphite, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}

/** 처음 화면에서 여는 단추. */
@Composable
fun ItemsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = Road.Sheet, modifier = modifier.heightIn(min = 76.dp)) {
        Row(Modifier.padding(horizontal = sp.l, vertical = sp.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.m)) {
            Box(Modifier.size(12.dp).background(Road.SignBlue))
            Column(Modifier.weight(1f)) {
                Text("채점 항목 46개 보기", style = MaterialTheme.typography.titleLarge, color = Road.Ink)
                Text("차가 자동 ${ItemCatalog.count(Judge.CAR)} · 코스 지점 ${ItemCatalog.count(Judge.COURSE)} · 동승자 체크 ${ItemCatalog.count(Judge.PASSENGER)}", style = MaterialTheme.typography.bodyLarge, color = Road.Graphite)
            }
        }
    }
}
