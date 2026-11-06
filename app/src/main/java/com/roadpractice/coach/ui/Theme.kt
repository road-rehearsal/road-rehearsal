package com.roadpractice.coach.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadpractice.coach.R

/**
 * 디자인 시스템: "채점표 위의 도로".
 * 시험장 채점표의 종이와 빨간 펜, 도로의 아스팔트·중앙선·표지판 색에서 가져왔습니다.
 * 기준은 code/app/DESIGN.md에 적었습니다. 색과 크기는 이 파일에서만 정합니다.
 */
/**
 * 색 한 벌. 낮과 밤에 같은 이름을 쓰고 값만 바꿉니다.
 * Ink는 글자, Asphalt는 도로와 어두운 판의 바탕입니다(밤에는 Ink가 밝아지므로 둘을 나눴습니다).
 */
@Immutable
data class RoadPalette(
    /** 채점표 종이. 화면 바탕. */
    val paper: Color,
    /** 카드와 판. */
    val sheet: Color,
    /** 본문 글자. */
    val ink: Color,
    /** 보조 글자. */
    val graphite: Color,
    /** 선과 구분. */
    val rule: Color,
    /** 도로와 어두운 판의 바탕. */
    val asphalt: Color,
    /** 지시 표지판 파랑. 주된 동작. */
    val signBlue: Color,
    /** 중앙선 노랑. 합격선과 코스. */
    val laneYellow: Color,
    /** 채점 빨간 펜. 감점. */
    val redPen: Color,
    /** 안내 표지판 초록. 합격선 통과. */
    val signGreen: Color,
    /** 차선 흰색(도로와 어두운 판 위). */
    val laneWhite: Color,
)

/** 낮: 채점표 종이 위. */
val DayRoad = RoadPalette(
    paper = Color(0xFFF3F4F1), sheet = Color(0xFFFFFFFF), ink = Color(0xFF1B2026), graphite = Color(0xFF5C646D),
    rule = Color(0xFFDADDD8), asphalt = Color(0xFF1B2026), signBlue = Color(0xFF0A4FA3), laneYellow = Color(0xFFF0B400),
    redPen = Color(0xFFC42B1C), signGreen = Color(0xFF00703C), laneWhite = Color(0xFFF7F7F2),
)

/**
 * 밤: 차 안이 어두울 때 눈부시지 않게 바탕을 낮춥니다. 표지판 색은 어두운 바탕에서 읽히도록 밝게 올렸습니다.
 * 글자 대비는 모두 4.5:1 이상입니다(4_개발/화면_비평.md).
 */
val NightRoad = RoadPalette(
    paper = Color(0xFF11151A), sheet = Color(0xFF1B2128), ink = Color(0xFFE8EBE6), graphite = Color(0xFF9EA7B0),
    rule = Color(0xFF333B44), asphalt = Color(0xFF07090B), signBlue = Color(0xFF7FB2F0), laneYellow = Color(0xFFF0B400),
    redPen = Color(0xFFFF7A6B), signGreen = Color(0xFF5DCB93), laneWhite = Color(0xFFE9EAE4),
)

val LocalRoad = staticCompositionLocalOf { DayRoad }

/** 화면에서 쓰는 색. 지금 테마(낮/밤)의 값을 돌려줍니다. */
object Road {
    val Paper: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.paper
    val Sheet: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.sheet
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.ink
    val Graphite: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.graphite
    val Rule: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.rule
    val Asphalt: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.asphalt
    val SignBlue: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.signBlue
    val LaneYellow: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.laneYellow
    val RedPen: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.redPen
    val SignGreen: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.signGreen
    val LaneWhite: Color @Composable @ReadOnlyComposable get() = LocalRoad.current.laneWhite
}

/** 간격과 크기. 차량 화면이라 손가락이 닿는 최소 높이를 크게 둡니다. */
@Immutable
data class Spacing(
    val xs: Dp = 6.dp,
    val s: Dp = 12.dp,
    val m: Dp = 20.dp,
    val l: Dp = 32.dp,
    val xl: Dp = 48.dp,
    /** 누르는 줄의 최소 높이(차량 화면 기준 76dp 이상). */
    val touch: Dp = 76.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

/** 숫자는 자리 폭을 맞춰 점수와 진행률이 흔들리지 않게 합니다. */
private const val TABULAR = "tnum"

/**
 * 한글은 기본으로 글자 단위로 줄이 바뀌어 낱글자 하나만 다음 줄로 넘어가기도 합니다. 어절 단위(Phrase)로 바꿉니다(Android 13 이상).
 * 제목은 줄 길이를 고르게(Heading), 본문은 문단 방식(Paragraph)으로 나눕니다.
 */
private val HeadBreak = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)
private val BodyBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

/**
 * 글자 단계. 차에서 팔 길이(약 70cm) 거리에서 읽는 것을 기준으로 본문을 24sp로 둡니다.
 * displayLarge는 점수 한 곳에만 씁니다.
 */
private val RoadTypography = Typography(
    displayLarge = TextStyle(lineBreak = HeadBreak, fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 112.sp, lineHeight = 112.sp, letterSpacing = (-3).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(lineBreak = HeadBreak, fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(lineBreak = HeadBreak, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 54.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(lineBreak = HeadBreak, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(lineBreak = HeadBreak, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    titleMedium = TextStyle(lineBreak = HeadBreak, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    bodyLarge = TextStyle(lineBreak = BodyBreak, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 34.sp),
    bodyMedium = TextStyle(lineBreak = BodyBreak, fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(lineBreak = BodyBreak, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR),
    labelMedium = TextStyle(lineBreak = BodyBreak, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR),
)

/** 모서리는 두 가지만 씁니다. 판(큰 카드)은 크게, 누르는 것은 작게. */
private val RoadShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
)

private fun colorsOf(p: RoadPalette, dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.signBlue,
    onPrimary = p.sheet,
    secondary = p.laneYellow,
    onSecondary = p.asphalt,
    error = p.redPen,
    onError = p.sheet,
    background = p.paper,
    onBackground = p.ink,
    surface = p.sheet,
    onSurface = p.ink,
    surfaceVariant = p.paper,
    onSurfaceVariant = p.graphite,
    outline = p.rule,
    outlineVariant = p.rule,
)

/** 차가 밤 모드(시스템 UI 모드 night)로 바뀌면 밤 색을 씁니다. */
@Composable
fun CoachTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) NightRoad else DayRoad
    MaterialTheme(colorScheme = colorsOf(palette, dark), typography = RoadTypography, shapes = RoadShapes) {
        CompositionLocalProvider(LocalSpacing provides Spacing(), LocalRoad provides palette, content = content)
    }
}
