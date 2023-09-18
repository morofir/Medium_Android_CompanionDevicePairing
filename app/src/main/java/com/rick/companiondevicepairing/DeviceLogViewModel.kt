package com.rick.companiondevicepairing

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanResult
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


import java.util.*

class DeviceLogViewModel(application: Application) : AndroidViewModel(application) {


    var isConnected = MutableLiveData<Boolean>()
    val logs = MutableLiveData<List<String>>(mutableListOf())
    var bluetoothGatt: BluetoothGatt? = null
    private val context = getApplication<Application>().applicationContext
    private val _logsInternal = mutableListOf<String>()
    var lastLogTime = MutableLiveData<String>("N/A")
    var lastLogText = MutableLiveData<String>("N/A")
    val myLogsCharacteristicUUID = UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_MG)
    var previousConnectionState: Boolean? = null





    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {

                    isConnected.postValue(true)
                    addLog("Device connected")
                    previousConnectionState = isConnected.value
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    addLog("Device connecting ...")
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    addLog("Device disconnecting ...")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {

                    isConnected.postValue(false)
                    addLog("Device Disconnected")
                    previousConnectionState = isConnected.value
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


        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_MG) ||
                characteristic.uuid == UUID.fromString(DEVICE_LOGS_CHARACTERISTIC_ID_DP)
            ) {
                val logData = String(characteristic.value, Charsets.UTF_8)
                Log.d(
                    "Bluetooth",
                    "Raw data: ${characteristic.value.joinToString(", ") { it.toString() }}"
                )

                updateLogs(logData)
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
    }


    fun addLog(message: String) {
        val currentLogs = logs.value?.toMutableList() ?: mutableListOf()
        currentLogs.add(message)
        logs.postValue(currentLogs)
    }

    private fun connectGatt(device: BluetoothDevice) {
        // save logs to file and Clear the logs list
        saveLogsToFile(_logsInternal)
        _logsInternal.clear()
        logs.postValue(mutableListOf()) // Clearing LiveData

        // Close any existing connection
        bluetoothGatt?.apply {
            close()
            bluetoothGatt = null
        }
        // Connect to GATT server with auto-reconnect set to true
        bluetoothGatt = device.connectGatt(context, true, gattCallback) // Set autoConnect to true
    }

    fun disconnectGatt() {
        bluetoothGatt?.disconnect()
    }

    fun handleActivityResult(deviceObject: Parcelable) {
        if (deviceObject is ScanResult) {
            val device = deviceObject.device
            connectGatt(device)
        } else {
            Toast.makeText(context, "Failed to connect", Toast.LENGTH_SHORT).show()
        }
    }
    fun updateLogs(newLog: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logWithTimestamp = "$timestamp: $newLog"

        val currentLogs = logs.value?.toMutableList() ?: mutableListOf()
        _logsInternal.add(logWithTimestamp)

        // Updating last log time and text
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        lastLogTime.postValue(currentTime)
        lastLogText.postValue(logWithTimestamp)

        currentLogs.add(logWithTimestamp)
        logs.postValue(currentLogs)
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
    }




}
