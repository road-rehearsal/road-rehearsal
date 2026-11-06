package com.roadpractice.coach

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * 연습 흐름을 소유하는 포그라운드 서비스.
 * 내비가 앞에 오면 화면만 있는 앱은 약 10초 뒤 동결되므로(실측) 신호 수신과 판정을 여기에 둡니다.
 * 심사에서 허용되는지는 미확인입니다.
 */
class CoachService : Service() {
    private var hub: SignalHub? = null
    private var tts: TtsSpeaker? = null
    private var queue: SpeechQueue? = null
    private var session: CoachSession? = null
    private var log: ExamLog? = null
    private var runId: Long = NO_RUN
    private var lastLine = ""
    private var lastFraction = -1.0
    private var lastNotice = ""
    private var lastGuide: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("준비됨"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val mode = intent?.getStringExtra(EXTRA_MODE)?.let { CoachMode.valueOf(it) } ?: CoachMode.PRACTICE
        if (tts == null) {
            tts = TtsSpeaker(applicationContext).also { queue = SpeechQueue(it) }
        }
        if (log == null) log = ExamLog(applicationContext)
        val course = runCatching { CourseLoader.find(applicationContext, intent?.getStringExtra(EXTRA_COURSE)) }
            .onFailure { Log.w(TAG, "course load failed: ${it.message}") }.getOrNull()
        session = CoachSession(mode, say = { queue?.say(it) }, course = course?.let { CourseTracker(it) })
        runId = NO_RUN
        lastGuide = null
        Log.i(TAG, "MODE $mode course=${course?.id ?: "-"}")
        publish(Snapshot(mode, hub?.state, ExamResult(emptyList(), emptyList()), started = false, ended = false, runId = NO_RUN))
        if (hub == null) {
            hub = SignalHub(applicationContext) { onVehicleState(it) }.also { it.start() }
        }
        return START_NOT_STICKY
    }

    private fun onVehicleState(state: VehicleState) {
        val line = state.summary()
        if (line != lastLine) {
            lastLine = line
            Log.i(TAG, "STATE $line")
        }
        val current = session ?: return
        current.onState(state).forEach { event ->
            when (event) {
                is ExamEvent.Started -> {
                    runId = log?.startRun(current.mode, System.currentTimeMillis(), current.course?.course?.id) ?: NO_RUN
                    Log.i(TAG, "EXAM_START mode=${current.mode} run=$runId")
                }
                is ExamEvent.Deduction -> {
                    if (runId != NO_RUN) log?.addCar(runId, event.item)
                    Log.i(TAG, "ITEM ${event.item.number} ${event.item.title} -${event.item.points} score=${current.result.score}")
                }
                is ExamEvent.Disqualified -> {
                    if (runId != NO_RUN) log?.addCarDisqualification(runId, event.reason)
                    Log.i(TAG, "DISQUALIFY ${event.reason.title}")
                }
                is ExamEvent.Guide -> {
                    lastGuide = event.text
                    if (event.course == CourseEvent.Done && runId != NO_RUN) log?.markCourseDone(runId)
                    Log.i(TAG, "GUIDE ${event.course} \"${event.text}\"")
                }
                is ExamEvent.Ended -> {
                    if (runId != NO_RUN) log?.endRun(runId, System.currentTimeMillis())
                    // 시험 중에는 말하지 않지만 끝난 뒤 알리는 것은 별표 26 비고 마와 맞습니다.
                    // 내비 등 다른 화면이 앞에 있어도 결과표로 돌아올 수 있게 알림을 띄웁니다.
                    queue?.say(END_SENTENCE)
                    notifyEnded()
                    Log.i(TAG, "EXAM_END run=$runId score=${current.result.score} passLine=${current.result.passedLine}")
                }
            }
        }
        updateNotification(current)
        val tracker = current.course
        tracker?.let { if (it.fraction != lastFraction) { lastFraction = it.fraction; Log.i(TAG, "COURSE at=${it.alongM.toInt()}m ${"%.3f".format(it.fraction)}") } }
        publish(Snapshot(current.mode, state, current.result, current.started, current.ended, runId, tracker?.course?.name, tracker?.fraction, lastGuide))
    }

    private fun publish(snapshot: Snapshot) {
        latest = snapshot
        listener?.invoke(snapshot)
    }

    override fun onDestroy() {
        hub?.stop()
        hub = null
        queue?.clear()
        tts?.release()
        tts = null
        session = null
        log?.close()
        log = null
        latest = null
        listener?.invoke(null)
        super.onDestroy()
    }

    /** 알림을 누르면 앱 화면으로 돌아옵니다. 내비가 앞에 있을 때 돌아오는 길입니다. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()
    }

    /** 진행 알림. 감점은 넣지 않고(시험 모드 침묵) 모드와 코스 진행만 보여 줍니다. 바뀔 때만 다시 올립니다. */
    private fun updateNotification(s: CoachSession) {
        val mode = if (s.mode == CoachMode.EXAM) "시험 모드" else "연습 모드"
        val course = s.course?.let { " · ${it.course.name} ${(it.fraction * 10).toInt() * 10}%" } ?: ""
        val text = when {
            s.ended -> "$mode · 끝남. 눌러서 결과표 보기"
            s.started -> "$mode 주행 중$course"
            else -> "$mode 준비됨"
        }
        if (text == lastNotice) return
        lastNotice = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun notifyEnded() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(END_CHANNEL_ID, "주행 끝", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.notify(
            END_NOTIFICATION_ID,
            Notification.Builder(this, END_CHANNEL_ID)
                .setContentTitle("주행이 끝났어요")
                .setContentText("눌러서 결과표를 확인하세요")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
    }

    /** 화면에 보여 줄 현재 상황. */
    data class Snapshot(
        val mode: CoachMode,
        val state: VehicleState?,
        val result: ExamResult,
        val started: Boolean,
        val ended: Boolean,
        val runId: Long,
        val courseName: String? = null,
        val courseFraction: Double? = null,
        /** 마지막 코스 안내 문장. 감점 문장은 넣지 않습니다(시험 모드 침묵). */
        val lastGuide: String? = null,
    )

    companion object {
        private const val TAG = "RoadCoach"
        private const val CHANNEL_ID = "coach"
        private const val NOTIFICATION_ID = 1
        private const val END_CHANNEL_ID = "coach_end"
        private const val END_NOTIFICATION_ID = 2
        const val END_SENTENCE = "주행이 끝났어요. 결과표는 앱에서 확인해요"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_COURSE = "course"
        const val NO_RUN = -1L

        /** 화면이 다시 켜졌을 때 바로 보여 줄 마지막 상황. */
        @Volatile
        var latest: Snapshot? = null
            private set

        /** 메인 스레드에서 호출됩니다. null은 서비스가 멈췄다는 뜻입니다. */
        var listener: ((Snapshot?) -> Unit)? = null

        fun start(context: Context, mode: CoachMode, courseId: String? = null) {
            context.startForegroundService(
                Intent(context, CoachService::class.java).putExtra(EXTRA_MODE, mode.name).putExtra(EXTRA_COURSE, courseId),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoachService::class.java))
        }
    }
}
