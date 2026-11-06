package com.roadpractice.coach.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
object Road {
    /** 채점표 종이. 화면 바탕. */
    val Paper = Color(0xFFF3F4F1)
    /** 카드와 판. */
    val Sheet = Color(0xFFFFFFFF)
    /** 아스팔트 잉크. 본문 글자와 도로. */
    val Ink = Color(0xFF1B2026)
    /** 보조 글자. */
    val Graphite = Color(0xFF5C646D)
    /** 선과 구분. */
    val Rule = Color(0xFFDADDD8)
    /** 지시 표지판 파랑. 주된 동작. */
    val SignBlue = Color(0xFF0A4FA3)
    /** 중앙선 노랑. 합격선과 코스. */
    val LaneYellow = Color(0xFFF0B400)
    /** 채점 빨간 펜. 감점. */
    val RedPen = Color(0xFFC42B1C)
    /** 안내 표지판 초록. 합격선 통과. */
    val SignGreen = Color(0xFF00703C)
    /** 차선 흰색(어두운 도로 위). */
    val LaneWhite = Color(0xFFF7F7F2)
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
 * 글자 단계. 차에서 팔 길이(약 70cm) 거리에서 읽는 것을 기준으로 본문을 24sp로 둡니다.
 * displayLarge는 점수 한 곳에만 씁니다.
 */
private val RoadTypography = Typography(
    displayLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 112.sp, lineHeight = 112.sp, letterSpacing = (-3).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-1.5).sp),
    headlineLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 54.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    titleMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 34.sp),
    bodyMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    labelLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = TABULAR),
    labelMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR),
)

/** 모서리는 두 가지만 씁니다. 판(큰 카드)은 크게, 누르는 것은 작게. */
private val RoadShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
)

private val RoadColors = lightColorScheme(
    primary = Road.SignBlue,
    onPrimary = Road.Sheet,
    secondary = Road.LaneYellow,
    onSecondary = Road.Ink,
    error = Road.RedPen,
    onError = Road.Sheet,
    background = Road.Paper,
    onBackground = Road.Ink,
    surface = Road.Sheet,
    onSurface = Road.Ink,
    surfaceVariant = Road.Paper,
    onSurfaceVariant = Road.Graphite,
    outline = Road.Rule,
    outlineVariant = Road.Rule,
)

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoadColors, typography = RoadTypography, shapes = RoadShapes) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSpacing provides Spacing(), content = content)
    }
}
