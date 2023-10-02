package com.rick.companiondevicepairing
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import java.util.*


class ForegroundService : LifecycleService() {

    private lateinit var deviceLogViewModel: DeviceLogViewModel
    private var currentProgress = 0


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super<LifecycleService>.onCreate()
        deviceLogViewModel = (applicationContext as MyApplication).deviceLogViewModel

        // Start observing logs
        deviceLogViewModel.logs.observe(this) { logs ->
            updateNotification(logs.isNotEmpty(), logs.size, currentProgress) // Here, progress is set to 0 initially
        }

        // Start posting progress updates
        progressHandler.post(progressRunnable)
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

    private var progressHandler: Handler = Handler(Looper.getMainLooper())
    private val progressRunnable: Runnable = object : Runnable {
        override fun run() {
            currentProgress += 1
            if (currentProgress > 100) {
                currentProgress = 0  // Reset to 0 if it crosses 100
            }
            updateNotification(
                deviceLogViewModel.isConnected.value ?: false,
                deviceLogViewModel.logs.value?.size ?: 0,
                currentProgress
            )
            progressHandler.postDelayed(this, 1000)  // Update every second
        }
    }


    private fun updateNotification(isConnected: Boolean, logSize: Int, progress: Int) {
        val text = if (isConnected) "Relivion Connected!" else "Relivion Disconnected"

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Relivion POC application")
            .setContentText("$text ($progress)")
            .setSmallIcon(R.drawable.ic_baseline_bluetooth_searching_24)
            .setProgress(100, progress, false) 
            .setOnlyAlertOnce(true)

        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())  // This line updates the existing notification
    }


    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacks(progressRunnable)  // Stop when the service is destroyed
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
