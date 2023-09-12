import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.os.Parcelable
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class DeviceLogViewModel(application: Application) : AndroidViewModel(application) {

    var isConnected = MutableLiveData<Boolean>()
    val logs: MutableLiveData<List<String>> = MutableLiveData(listOf())
    var bluetoothGatt: BluetoothGatt? = null
    private val context = getApplication<Application>().applicationContext

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected.postValue(true)
                    addLog("Device connected")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected.postValue(false)
                    addLog("Device Disconnected")
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
}
