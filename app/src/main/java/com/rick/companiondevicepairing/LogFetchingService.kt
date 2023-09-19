//package com.rick.companiondevicepairing
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.os.IBinder
//import org.greenrobot.eventbus.EventBus
//
//
//class LogFetchingService : Service() {
//    override fun onBind(intent: Intent?): IBinder? {
//        return null
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        startInForeground()
//        // Your logic to continue fetching logs here
//        return START_STICKY
//    }
//
//    private fun startInForeground() {
//        val notificationChannelId = "Log Fetching Service"
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val notificationChannel = NotificationChannel(
//                notificationChannelId,
//                "Log Fetching Notifications",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            manager.createNotificationChannel(notificationChannel)
//        }
//
//        val notification = Notification.Builder(this, notificationChannelId)
//            .setContentTitle("Fetching Logs")
//            .setContentText("The app is currently fetching logs from the device.")
//            .setSmallIcon(R.drawable.ic_baseline_bluetooth_searching_24) // Your icon here
//            .build()
//
//        startForeground(1, notification)
//    }
//
//    fun fetchLogs(newLog: String): List<String> {
//        // Mocked fetching logic, adjust as needed.
//        return newLog.split(";").filter {
//            it.isNotEmpty() && it != "UnwantedLogEntry"
//        }
//    }
//
//    fun fetchAndSendLogs(newLog: String) {
//        val logs = fetchLogs(newLog)
//        EventBus.getDefault().post(LogsEvent(logs))
//    }
//
//    data class LogsEvent(val logs: List<String>)
//
//
//
//}
