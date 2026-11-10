package com.roadpractice.coach

import android.Manifest
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.roadpractice.coach.ui.CoachTheme
import com.roadpractice.coach.ui.DrivingScreen
import com.roadpractice.coach.ui.DrivingUi
import com.roadpractice.coach.ui.HomeScreen
import com.roadpractice.coach.ui.HomeUi
import com.roadpractice.coach.ui.ReadyScreen
import com.roadpractice.coach.ui.ReadyUi
import com.roadpractice.coach.ui.ReviewMarks
import com.roadpractice.coach.ui.ReviewScreen
import com.roadpractice.coach.ui.ReviewUi
import com.roadpractice.coach.ui.Road
import com.roadpractice.coach.ui.RunRow

/**
 * 화면은 차량 상태가 정합니다. 정차 중에는 처음 화면과 결과표, 주행 중에는 주행 화면만 보입니다.
 * 사용자가 화면을 오가는 앱이 아니라서 내비게이션 스택을 두지 않고, 상태에 따라 화면을 고릅니다.
 * 주행 중에는 감점을 보여 주지 않고 입력도 받지 않습니다.
 */
class MainActivity : ComponentActivity() {
    private lateinit var log: ExamLog

    private var snapshot by mutableStateOf<CoachService.Snapshot?>(null)
    /** AAOS 주행 규제. 서비스가 꺼져 있어도 주행 중인지 알 수 있게 씁니다. */
    private var restricted by mutableStateOf(false)
    /** 기록이 바뀌면 올려서 처음 화면을 다시 읽게 합니다. */
    private var dataVersion by mutableIntStateOf(0)

    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private val courses: List<Course> by lazy { runCatching { CourseLoader.all(applicationContext) }.getOrDefault(emptyList()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = ExamLog(applicationContext)
        setContent {
            CoachTheme {
                Box(Modifier.fillMaxSize().background(Road.Paper)) { App() }
            }
        }
        // 이미 받은 권한은 다시 묻지 않습니다. 권한 화면이 이 앱 작업에 끼면
        // 홈 화면이 주행 중 표시 가능(distractionOptimized)하지 않은 앱으로 보고 가리는 것을 11/6에 확인했습니다(추정 원인).
        val missing = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            "android.car.permission.CAR_SPEED",
            "pleos.car.permission.FUSED_LOCATION",
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
        startReplayIfAsked(intent)
    }

    /**
     * 시연용 재생(디버그 빌드만): adb로 기록 파일 이름을 넘기면 그 기록으로 바로 시작합니다.
     *   am start --user 10 -n com.roadpractice.coach/.MainActivity --es replay drive.csv --es course my_route_1 --es mode EXAM --ei replaySpeed 4
     * 시연 화면(11/8): --ei replayFrom 1380 --ei replayTo 1520 --ez replayMixed true
     *   (구간만, 방향지시등·문·안전띠·기어·브레이크는 Virtual Console에서)
     *   --ei replayHold 25: 기록 전에 D로 25초 서 있기(5번), --ez replayEdge true: 구간 시작을 길가 출발로(11~13번)
     *   섞기 모드의 끝은 조작판에서 운전석 문을 열 때(44·46번)
     */
    private fun startReplayIfAsked(i: Intent?) {
        val file = i?.getStringExtra(CoachService.EXTRA_REPLAY) ?: return
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val mode = runCatching { CoachMode.valueOf(i.getStringExtra("mode") ?: "PRACTICE") }.getOrDefault(CoachMode.PRACTICE)
        CoachService.start(
            this, mode, i.getStringExtra("course"), file, i.getIntExtra(CoachService.EXTRA_REPLAY_SPEED, 1),
            i.getIntExtra(CoachService.EXTRA_REPLAY_FROM, 0), i.getIntExtra(CoachService.EXTRA_REPLAY_TO, Int.MAX_VALUE),
            i.getBooleanExtra(CoachService.EXTRA_REPLAY_MIXED, false),
            i.getIntExtra(CoachService.EXTRA_REPLAY_HOLD, 0), i.getBooleanExtra(CoachService.EXTRA_REPLAY_EDGE, false),
        )
        i.removeExtra(CoachService.EXTRA_REPLAY)
    }

    override fun onStart() {
        super.onStart()
        connectUx()
        // 서비스가 없는데 끝나지 않은 주행이 있으면 앱이 주행 중에 멈췄던 것입니다.
        if (CoachService.latest == null && log.closeOpenRuns(System.currentTimeMillis()) > 0) dataVersion++
        CoachService.listener = { onSnapshot(it) }
        onSnapshot(CoachService.latest)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startReplayIfAsked(intent)
        onSnapshot(CoachService.latest)
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

    private fun onSnapshot(s: CoachService.Snapshot?) {
        // 끝나는 순간 기록이 바뀌므로 처음 화면 자료도 다시 읽습니다.
        if (s?.ended != snapshot?.ended || (s == null) != (snapshot == null)) dataVersion++
        snapshot = s
    }

    private fun connectUx() {
        runCatching {
            val c = Car.createCar(this).also { car = it }
            uxManager = (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).also { m ->
                m.registerListener { restricted = it.isRequiresDistractionOptimization }
                restricted = m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            }
        }
    }

    @Composable
    private fun App() {
        val s = snapshot
        // 서비스가 꺼져 있으면 속도를 모르므로 주행 규제 값도 함께 봅니다.
        val moving = restricted || s?.state?.isMoving == true
        val reviewRun: Long? = when {
            s != null && s.started && !s.ended -> null
            s != null && s.ended && s.runId != CoachService.NO_RUN -> s.runId
            s == null -> remember(dataVersion) { log.latestUnreviewedEnded()?.id }
            else -> null
        }
        when {
            s != null && s.started && !s.ended -> DrivingScreen(
                DrivingUi(
                    s.mode, s.courseName?.let(::shortName), s.courseFraction, s.lastGuide, isEmulator,
                    replaying = s.replaying,
                    turnKind = s.nextTurn?.type?.name,
                    turnRemainingM = s.nextTurn?.remainingM,
                    turnThen = s.nextTurn?.then?.name,
                    straightM = s.straight?.first,
                    straightNext = s.straight?.let { st ->
                        when (st.second) {
                            CheckpointType.LEFT -> "좌회전"
                            CheckpointType.RIGHT -> "우회전"
                            CheckpointType.UTURN -> "유턴"
                            else -> null
                        }
                    },
                    zoneLimitKmh = s.zoneLimitKmh?.toInt(),
                    zoneSchool = s.zoneSchool,
                    speedKmh = s.state?.speedKmh,
                ),
            )
            reviewRun != null -> Review(reviewRun, moving)
            s != null && !s.started && !s.ended -> ReadyScreen(
                ReadyUi(s.mode, s.courseName?.let(::shortName), moving),
                onCancel = {
                    CoachService.stop(this)
                    snapshot = null
                },
            )
            else -> Home(moving)
        }
    }

    @Composable
    private fun Home(moving: Boolean) {
        var choice by rememberSaveable { mutableIntStateOf(0) }
        // 폰으로 기록한 코스는 "내 기록"과 길이를 붙여 앱 기본 코스(현대시험장)와 나눠 보여 줍니다.
        val options = remember {
            listOf("뽑기") + courses.map { c ->
                if (c.id in CourseLoader.localIds) "${shortName(c.name)} · 내 기록 ${"%.1f".format(c.lengthM / 1000)}km" else shortName(c.name)
            } + "코스 없이"
        }
        val version = dataVersion
        val runs = remember(version) {
            log.recentRuns().map { run ->
                RunRow(
                    mode = run.mode,
                    course = run.course?.let { id -> courses.firstOrNull { it.id == id }?.name?.let(::shortName) ?: id },
                    score = run.score,
                    passed = if (run.interrupted) null else run.passedLine,
                    note = when {
                        run.interrupted -> "중간에 멈춤"
                        run.endedAt == null -> "진행 중"
                        !run.reviewed -> "동승자 체크 전"
                        else -> null
                    },
                )
            }
        }
        val often = remember(version) {
            log.carItemCounts().entries.sortedByDescending { it.value }.take(OFTEN_LIMIT).map { it.key.title to it.value }
        }
        // 전체 채점 항목 보기(11/8). 정차 중에만: 주행이 시작되면 HomeScreen이 "주행 중에는 설정할 수 없어요"로 바뀌며 닫힙니다.
        var showItems by rememberSaveable { mutableStateOf(false) }
        if (showItems && !moving) {
            androidx.activity.compose.BackHandler { showItems = false }
            com.roadpractice.coach.ui.ItemsScreen(onBack = { showItems = false })
            return
        }
        androidx.compose.runtime.LaunchedEffect(moving) { if (moving) showItems = false }
        HomeScreen(
            HomeUi(options, choice, runs, often, moving),
            onCourse = { choice = it },
            onStart = { mode -> CoachService.start(this, mode, pickCourse(choice)) },
            onItems = { showItems = true },
        )
    }

    @Composable
    private fun Review(runId: Long, moving: Boolean) {
        val data = remember(runId) {
            val run = log.run(runId)
            val base = log.sheet(runId)
            ReviewUi(
                runId = runId,
                mode = run?.mode ?: CoachMode.PRACTICE,
                course = run?.course?.let { id -> courses.firstOrNull { it.id == id }?.name?.let(::shortName) ?: id },
                courseDone = run?.courseDone == true,
                interrupted = run?.interrupted == true,
                car = base.lines.filter { it.byCar }.mapNotNull { line -> ExamItem.entries.firstOrNull { it.number == line.number } },
                carDq = Disqualification.entries.filter { it.title in base.disqualifications },
                moving = false,
                points = log.coursePoints(runId),
                compare = run?.course?.let { id -> compareLine(runId, id) },
            )
        }
        var marks by rememberSaveable(runId, stateSaver = MarksSaver) { mutableStateOf(ReviewMarks()) }
        ReviewScreen(
            data.copy(moving = moving), marks, onMarks = { marks = it },
            onSave = {
                log.saveReview(runId, marks.excluded, marks.passenger.toList(), marks.passengerDq.toList())
                CoachService.stop(this)
                snapshot = null
                dataVersion++
            },
        )
    }

    /**
     * 같은 코스 지난번과 비교한 한 줄. 동승자 체크 전이라 최종 점수 대신 "차가 본 감점 개수"로 비교합니다.
     * 다시 연습할 이유(무엇이 나아졌나)를 보여 주려는 것입니다.
     */
    private fun compareLine(runId: Long, course: String): String? {
        val prev = log.previousOnCourse(runId, course) ?: return null
        val before = log.carDeductionCount(prev.id)
        val now = log.carDeductionCount(runId)
        val diff = before - now
        val trend = when {
            diff > 0 -> "지난번보다 ${diff}개 줄었어요"
            diff < 0 -> "지난번보다 ${-diff}개 늘었어요"
            else -> "지난번과 같아요"
        }
        val score = prev.score?.let { " (지난번 ${it}점)" } ?: ""
        return "같은 코스에서 차가 본 감점이 $trend: ${before}개 → ${now}개$score"
    }

    /** 0 = 뽑기, 1..n = 코스, 마지막 = 코스 없이. 뽑기는 완주 횟수가 가장 적은 코스 중에서 고릅니다. */
    private fun pickCourse(choice: Int): String? = when {
        choice == 0 -> {
            // 뽑기는 앱 기본 코스 안에서만(내 기록 코스는 직접 고름)
            val pool = courses.filter { it.id !in CourseLoader.localIds }
            val done = log.courseDoneCounts()
            val least = pool.minOfOrNull { done[it.id] ?: 0 }
            pool.filter { (done[it.id] ?: 0) == least }.randomOrNull()?.id
        }
        choice in 1..courses.size -> courses[choice - 1].id
        else -> null
    }

    /** "현대시험장 A코스(좌회전 출발)" → "현대시험장 A코스" */
    private fun shortName(name: String) = name.substringBefore("(").trim()

    /** 에뮬레이터에서는 위치를 adb로 넣으므로 모의 위치라고 밝힙니다. */
    private val isEmulator: Boolean
        get() = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")

    private companion object {
        const val PERMISSION_REQUEST = 1
        const val OFTEN_LIMIT = 3

        /** 화면 회전·창 크기 변경에도 체크한 것이 남도록 저장합니다. */
        val MarksSaver = Saver<ReviewMarks, ArrayList<String>>(
            save = { m ->
                arrayListOf(
                    m.excluded.joinToString(","),
                    m.passenger.joinToString(",") { it.name },
                    m.passengerDq.joinToString(",") { it.name },
                )
            },
            restore = { l ->
                fun names(s: String) = s.split(",").filter { it.isNotBlank() }
                ReviewMarks(
                    excluded = names(l[0]).map { it.toInt() }.toSet(),
                    passenger = names(l[1]).mapNotNull { n -> PassengerItem.entries.firstOrNull { it.name == n } }.toSet(),
                    passengerDq = names(l[2]).mapNotNull { n -> PassengerDisqualification.entries.firstOrNull { it.name == n } }.toSet(),
                )
            },
        )
    }
}
