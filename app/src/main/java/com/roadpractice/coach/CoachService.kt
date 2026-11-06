package com.roadpractice.coach

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
    private var lastLine = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val mode = intent?.getStringExtra(EXTRA_MODE)?.let { CoachMode.valueOf(it) } ?: CoachMode.PRACTICE
        if (tts == null) {
            tts = TtsSpeaker(applicationContext).also { queue = SpeechQueue(it) }
        }
        session = CoachSession(mode, say = { queue?.say(it) })
        Log.i(TAG, "MODE $mode")
        publish(Snapshot(mode, hub?.state, ExamResult(emptyList(), emptyList()), started = false, ended = false))
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
                is ExamEvent.Started -> Log.i(TAG, "EXAM_START mode=${current.mode}")
                is ExamEvent.Deduction -> Log.i(TAG, "ITEM ${event.item.number} ${event.item.title} -${event.item.points} score=${current.result.score}")
                is ExamEvent.Disqualified -> Log.i(TAG, "DISQUALIFY ${event.reason.title}")
                is ExamEvent.Ended -> Log.i(TAG, "EXAM_END score=${current.result.score} passLine=${current.result.passedLine}")
            }
        }
        publish(Snapshot(current.mode, state, current.result, current.started, current.ended))
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
        latest = null
        listener?.invoke(null)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .build()
    }

    /** 화면에 보여 줄 현재 상황. */
    data class Snapshot(
        val mode: CoachMode,
        val state: VehicleState?,
        val result: ExamResult,
        val started: Boolean,
        val ended: Boolean,
    )

    companion object {
        private const val TAG = "RoadCoach"
        private const val CHANNEL_ID = "coach"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_MODE = "mode"

        /** 화면이 다시 켜졌을 때 바로 보여 줄 마지막 상황. */
        @Volatile
        var latest: Snapshot? = null
            private set

        /** 메인 스레드에서 호출됩니다. null은 서비스가 멈췄다는 뜻입니다. */
        var listener: ((Snapshot?) -> Unit)? = null

        fun start(context: Context, mode: CoachMode) {
            context.startForegroundService(Intent(context, CoachService::class.java).putExtra(EXTRA_MODE, mode.name))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoachService::class.java))
        }
    }
}
