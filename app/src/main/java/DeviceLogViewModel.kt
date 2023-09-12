import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

import java.util.*

class DeviceLogViewModel(application: Application) : AndroidViewModel(application) {

    private  val myServiceUUID = "2A38F000-59C8-492B-9358-0E4E38FB0058"
    private val myLogsCharacteristicUUID = UUID.fromString("2a38f002-59c8-492b-9358-0e4e38fb0058")

    var isConnected = MutableLiveData<Boolean>()
    val logs = MutableLiveData<List<String>>(mutableListOf())
    var bluetoothGatt: BluetoothGatt? = null
    private val context = getApplication<Application>().applicationContext
    private val _logsInternal = mutableListOf<String>()



    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected.postValue(true)
                    addLog("Device connected")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected.postValue(false)
                    addLog("Device Disconnected")
                }
            }
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(UUID.fromString(myServiceUUID))
            val characteristic = service.getCharacteristic(myLogsCharacteristicUUID)

            if (gatt.setCharacteristicNotification(characteristic, true)) {
                addLog("Logs characteristic notification enabled")

                val descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }

        // Handle incoming notifications
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == myLogsCharacteristicUUID) {
                val logData = String(characteristic.value, Charsets.UTF_8)
                Log.d("Bluetooth", "Raw data: ${characteristic.value.joinToString(", ") { it.toString() }}")

                updateLogs(logData)
            }
        }


        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid == myLogsCharacteristicUUID) {
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

    fun connectGatt(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
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
        val currentLogs = logs.value?.toMutableList() ?: mutableListOf()
        _logsInternal.add(newLog)

        currentLogs.add(newLog)
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
            if (characteristic?.uuid == UUID.fromString(myServiceUUID)) {
                val logData = characteristic?.value?.toString(Charsets.UTF_8)
                if (logData != null) {
                   updateLogs(logData)
                }
            }
        }
    }

}
