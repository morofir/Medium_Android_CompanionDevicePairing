package com.rick.companiondevicepairing

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


import java.util.*

class DeviceLogViewModel(application: Application) : AndroidViewModel(application), LifecycleObserver {


    var isConnected = MutableLiveData<Boolean>()
    val logs = MutableLiveData<List<String>>(mutableListOf())
    var bluetoothGatt: BluetoothGatt? = null
    private val context = getApplication<Application>().applicationContext
    private val _logsInternal = mutableListOf<String>()
    var lastLogText = MutableLiveData<String>("N/A")
    var previousConnectionState: Boolean? = null
    private var lastLoggedState: String? = null
    private var isLoggingActive = false
    private var lastConnectedDevice: BluetoothDevice? = null
    val lastLogTimes: MutableLiveData<List<String>> = MutableLiveData(emptyList())
    private var reconnectJob: Job? = null


    init {
        val mediatorLiveData = MediatorLiveData<Boolean>()
        mediatorLiveData.addSource(isConnected) { isConnected ->
            if (previousConnectionState == true && isConnected == false) {
                // The device has been disconnected; initiate reconnection.
                initiateReconnection()
            }
            previousConnectionState = isConnected
        }
    }
    override fun onCleared() {
        reconnectJob?.cancel()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart(){
        addLog("App State: ON_START")
        startPeriodicLogging()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnterBackground() {
        addLog("App State: ON_STOP")

        // We'll keep logging in the background for demonstration
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroyed() {
        addLog("App State: TERMINATED")
    }

    private fun initiateReconnection() {
        // Cancel any ongoing reconnection job to avoid multiple simultaneous reconnections
        reconnectJob?.cancel()

        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            var retryCount = 0
            while (!isConnected.value!! && retryCount < MAX_RECONNECT_ATTEMPTS) {
                delay(RECONNECT_INTERVAL_MS)
                lastConnectedDevice?.let { device ->
                    connectGatt(device)
                }
                retryCount++
            }
        }
    }


    private fun startPeriodicLogging() {
        if (isLoggingActive) return
        isLoggingActive = true

        CoroutineScope(Dispatchers.IO).launch {
            while (isLoggingActive) {
                delay(1000) // Wait for 1 second

                val deviceState = if (isConnected.value == true) "Connected" else "Disconnected"

                // Check if the state has changed since the last log entry
                if (deviceState != lastLoggedState) {
                    lastLoggedState = deviceState
                    val timestamp = getCurrentTimestamp()
                    addLog("$timestamp: Device State: $deviceState")
                }
            }
        }
    }

    fun logConnectionTime(time: String) {
        val currentList = lastLogTimes.value?.toMutableList() ?: mutableListOf()
        currentList.add(time)
        lastLogTimes.value = currentList
    }


    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    fun startService(context: Context) {
        val serviceIntent = Intent(context, ForegroundService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopService(context: Context) {
        val serviceIntent = Intent(context, ForegroundService::class.java)
        context.stopService(serviceIntent)
    }

     private val gattCallback = object : BluetoothGattCallback() {
         override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
             when (newState) {
                 BluetoothProfile.STATE_CONNECTED -> {
                     addLog("Device connected")
                     isConnected.postValue(true)
                     previousConnectionState = isConnected.value
                     if (ActivityCompat.checkSelfPermission(
                             context,
                             Manifest.permission.BLUETOOTH_CONNECT
                         ) != PackageManager.PERMISSION_GRANTED
                     ) {
                         // TODO: Consider calling
                         //    ActivityCompat#requestPermissions
                         // here to request the missing permissions, and then overriding
                         //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                         //                                          int[] grantResults)
                         // to handle the case where the user grants the permission. See the documentation
                         // for ActivityCompat#requestPermissions for more details.
                         return
                     }
                     gatt.discoverServices()
                 }
                 BluetoothProfile.STATE_CONNECTING -> {
                     addLog("Device connecting ...")
                 }
                 BluetoothProfile.STATE_DISCONNECTING -> {
                     addLog("Device disconnecting ...")
                 }
                 BluetoothProfile.STATE_DISCONNECTED -> {
                     addLog("Device Disconnected")
                     isConnected.postValue(false)
                     previousConnectionState = isConnected.value
                 }
             }
         }

         override fun onCharacteristicChanged(
             gatt: BluetoothGatt,
             characteristic: BluetoothGattCharacteristic
         ) {
             if (characteristic.uuid == UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_MG) ||
                 characteristic.uuid == UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_DP)
             ) {
                 val logData = String(characteristic.value, Charsets.UTF_8)

                 // Log the detailed raw data to Android's system logs for debugging
                 Log.d(
                     "Bluetooth",
                     "Raw data: ${characteristic.value.joinToString(", ") { it.toString() }}"
                 )

                 // Combine the placeholder for raw binary data and the actual log data into a single entry
                 val combinedLog = "binary data"
                 updateLogs(combinedLog)

             }
         }

         override fun onCharacteristicRead(
             gatt: BluetoothGatt,
             characteristic: BluetoothGattCharacteristic,
             status: Int
         ) {
             if (characteristic.uuid == UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_MG) ||
                 characteristic.uuid == UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_DP)
             ) {
                 val logData = characteristic.value?.let { String(it, Charsets.UTF_8) }
                 if (logData != null) {
                     updateLogs(logData)
                 }
             }
         }


         override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
             listOf(
                 Relivion_CUSTOM_SERVICE_ID_MG,
                 RELIVION_CUSTOM_SERVICE_ID_DP
             ).forEach { serviceId ->
                 val service = gatt.getService(UUID.fromString(serviceId))
                 if (service != null) {
                     val characteristicId = if (serviceId == Relivion_CUSTOM_SERVICE_ID_MG) {
                         DEVICE_LOGS_CHARACTERISTIC_ID_MG
                     } else {
                         DEVICE_LOGS_CHARACTERISTIC_ID_DP
                     }
                     val characteristic = service.getCharacteristic(UUID.fromString(characteristicId))
                     if (ActivityCompat.checkSelfPermission(
                             context,
                             Manifest.permission.BLUETOOTH_CONNECT
                         ) != PackageManager.PERMISSION_GRANTED
                     ) {
                         // TODO: Consider calling
                         //    ActivityCompat#requestPermissions
                         // here to request the missing permissions, and then overriding
                         //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                         //                                          int[] grantResults)
                         // to handle the case where the user grants the permission. See the documentation
                         // for ActivityCompat#requestPermissions for more details.
                         return
                     }
                     if (characteristic != null && gatt.setCharacteristicNotification(characteristic, true)) {
                         addLog("Logs characteristic notification enabled for service $serviceId")

                         val descriptor = characteristic.getDescriptor(
                             UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                         )
                         descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                         gatt.writeDescriptor(descriptor)
                     } else {
                         addLog("Failed to get characteristic for service $serviceId")
                     }
                 } else {
                     addLog("Service $serviceId not found")
                 }
             }
         }
     }

    fun addLog(message: String) {
        _logsInternal.add(message)
        logs.postValue(_logsInternal.toList())  // Using postValue here
    }


    fun handleBondedDevice(bondedDevice: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        if (bondedDevice.bondState != BluetoothDevice.BOND_BONDED) {
            bondedDevice.createBond()
            // Once the bond is created, the device's bond state will change.
            // You can then initiate the connection or whatever logic you want to handle post-bonding.
        } else {
            // If you're not connected or you're connected to a different device, initiate a connection.
            if (isConnected.value != true || lastConnectedDevice?.address != bondedDevice.address) {
                lastConnectedDevice = bondedDevice
                connectGatt(bondedDevice)
            } else {
                // If already connected to the same device, trigger log reading
                readLogsFromDevice()
            }
        }
    }

    fun readLogsFromDevice() {
        // Identify the correct characteristic that you wish to read from.
        // This example uses DEVICE_LOGS_CHARACTERISTIC_ID_MG for demonstration.
        val serviceUUID = UUID.fromString(Relivion_CUSTOM_SERVICE_ID_MG)
        val characteristicUUID = UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_MG)

        val service = bluetoothGatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)


        if (characteristic != null) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothGatt?.readCharacteristic(characteristic)
        }
    }



    private fun connectGatt(device: BluetoothDevice) {
        // If we're already connected or attempting a connection, disconnect first
        if (isConnected.value == true || bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothGatt?.disconnect()
        }
        // save logs to file and Clear the logs list
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        saveLogsToFile(_logsInternal)
        logs.postValue(listOf("-------New Connection-------"))
        logConnectionTime(timestamp)

        // Close any existing connection
        bluetoothGatt?.apply {
            close()
            bluetoothGatt = null
        }
        // Connect to GATT server with auto-reconnect set to true
        bluetoothGatt = device.connectGatt(context, true, gattCallback) // Set autoConnect to true
    }

    fun disconnectGatt() {
        reconnectJob?.cancel()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        bluetoothGatt?.disconnect()
    }

    fun handleActivityResult(deviceObject: Parcelable) {
        if (deviceObject is ScanResult) {
            val device = deviceObject.device
            lastConnectedDevice = device

            connectGatt(device)
        } else {
            Toast.makeText(context, "Failed to connect", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateLogs(newLog: String) {
//        val fetchedLogs = logFetchingService.fetchLogs(newLog)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logWithTimestamp = "$timestamp: Device State: $newLog"

        // Adding the log to both _logsInternal and LiveData
        addLog(logWithTimestamp)

        lastLogText.postValue(logWithTimestamp)  // Using postValue

        // If there are new logs, save them to the file
//        if (fetchedLogs.isNotEmpty()) {
//            saveLogsToFile(_logsInternal)
//        }
    }


     val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            if (characteristic?.uuid == UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_MG)) {
                val logData = characteristic?.value?.toString(Charsets.UTF_8)
                if (logData != null) {
                   updateLogs(logData)
                }
            }
        }
    }



    val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action!!
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                Toast.makeText(context, "bondState ${bondState}", Toast.LENGTH_LONG).show()

                when (bondState) {
                    BluetoothDevice.BOND_BONDING -> {

                        // Show a message or UI element indicating bonding in progress.
                    }
                    BluetoothDevice.BOND_BONDED -> {
                        // The device is now bonded. You can initiate a connection or handle post-bonding logic here.
                    }
                    BluetoothDevice.BOND_NONE -> {
                        // Bonding failed or the device is now unbonded. Handle accordingly.
                    }
                }
            }
        }
    }

    // LOG FILES
    fun saveLogsToFile(logs: List<String>) {
        val logsString = logs.joinToString("\n")
        try {
            val logsFile = File(context.getExternalFilesDir(null), "device_logs.txt")
            val fos = FileOutputStream(logsFile)
            fos.write(logsString.toByteArray())
            fos.close()

            Toast.makeText(context, "Logs saved to ${logsFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(context, "Failed to save logs", Toast.LENGTH_SHORT).show()
        }
    }



    fun shareLogFile(activity: AppCompatActivity) {
        val logsFile = File(activity.getExternalFilesDir(null), "device_logs.txt")

        if (!logsFile.exists()) {
            Toast.makeText(activity, "Log file doesn't exist", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            // If you don't have permission, request it
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Device Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val uri: Uri = FileProvider.getUriForFile(
                activity,
                "com.rick.companiondevicepairing.fileprovider",
                logsFile
            )
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        activity.startActivity(Intent.createChooser(intent, "Share logs via"))
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 101
        const val MAX_RECONNECT_ATTEMPTS = 5 // you can adjust this
        const val RECONNECT_INTERVAL_MS = 5000L // 5 seconds, you can adjust this as needed
    }

}

