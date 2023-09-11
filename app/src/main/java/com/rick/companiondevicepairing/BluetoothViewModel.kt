import android.bluetooth.*
import android.bluetooth.le.ScanResult

import android.companion.CompanionDeviceManager

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.content.Context

class BluetoothViewModel : ViewModel() {
    private lateinit var deviceManager: CompanionDeviceManager
    private var bluetoothGatt: BluetoothGatt? = null
    var isConnected = MutableLiveData<Boolean>()
    val foundDevices: MutableLiveData<List<ScanResult>> = MutableLiveData()
    val logs: MutableLiveData<List<String>> = MutableLiveData(listOf())

    init {
        isConnected.value = false
    }

    fun init(context: Context) {
        deviceManager = context.getSystemService(CompanionDeviceManager::class.java)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected.postValue(true)
                    addLog("Connected to GATT server.")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected.postValue(false)
                    addLog("Disconnected from GATT server.")
                }
            }
        }

        // ... (Same as what you had for other overridden methods)
    }

    private fun addLog(message: String) {
        val currentLogs = logs.value?.toMutableList() ?: mutableListOf()
        currentLogs.add(message)
        logs.postValue(currentLogs)
    }

    // ... (Move all other Bluetooth logic methods here like readCharacteristic(), writeCharacteristic() etc.)

}
