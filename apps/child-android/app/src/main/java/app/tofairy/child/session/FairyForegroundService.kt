package app.tofairy.child.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.tofairy.child.R
import app.tofairy.child.sensing.SensingGate

/**
 * 명시적으로 시작된 Fairy Session의 장시간 실행 표시 (CLAUDE.md §4).
 *  - 케이스 A: 명시적으로 시작한 장시간 Fairy Session 동안 구동.
 *  - 케이스 B: 명시적인 공유폰 요정 모드 세션 동안만 시작/종료.
 * 포그라운드 알림으로 구동을 투명하게 드러낸다(아이/부모 모두에게).
 */
class FairyForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY 재생성이나 임의 호출로 세션 경계를 우회하지 않는다.
        if (SensingGate.activeSessionIdentity == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForegroundCompat()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("요정이 함께 있어요")
            .setSmallIcon(android.R.drawable.star_on)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "요정 동행",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "fairy_companion"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            check(SensingGate.activeSessionIdentity != null) {
                "FairyForegroundService requires an explicit active Fairy Session"
            }
            context.startForegroundService(Intent(context, FairyForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FairyForegroundService::class.java))
        }
    }
}
