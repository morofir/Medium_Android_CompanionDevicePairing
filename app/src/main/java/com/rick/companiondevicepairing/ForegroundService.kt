package com.rick.companiondevicepairing
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer


class ForegroundService : LifecycleService() {

    private lateinit var deviceLogViewModel: DeviceLogViewModel

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super<LifecycleService>.onCreate()
        deviceLogViewModel = (applicationContext as MyApplication).deviceLogViewModel

        // Observe ViewModel LiveData
        deviceLogViewModel.isConnected.observe(this, Observer { isConnected ->
            updateNotification(isConnected)
        })
    }

    override fun onBind(intent: Intent): IBinder? {
        super<LifecycleService>.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super<LifecycleService>.onStartCommand(intent, flags, startId)
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Relivion POC application")
            .setContentText("Relivion Connected!")
            .setSmallIcon(R.drawable.ic_baseline_bluetooth_searching_24)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BLE Channel"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance)
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(isConnected: Boolean) {
        val text = if (isConnected) "Relivion Connected!" else "Relivion Disconnected"

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Relivion POC application")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_baseline_bluetooth_searching_24)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    companion object {
        private val presentDevices = mutableSetOf<String>()

        fun foundDevice(context: Context, address: String) {
            presentDevices += address
            val intent = Intent(context, ForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun lostDevice(context: Context, address: String) {
            presentDevices -= address

            val intent = Intent(context, ForegroundService::class.java)
            context.stopService(intent)
        }

        private const val channelId = "BLE_NOTIFICATION_CHANNEL"
        const val NOTIFICATION_ID = 12345
    }

}
