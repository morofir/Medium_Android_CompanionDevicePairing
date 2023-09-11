package com.rick.companiondevicepairing

import BluetoothViewModel
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ParcelUuid
import android.os.Parcelable
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.viewModels



private const val SELECT_DEVICE_REQUEST_CODE = 0

class MyDeviceSelectionActivity : AppCompatActivity() {

    companion object {
        private val TAG = MyDeviceSelectionActivity::class.java.simpleName
    }

    private val deviceManager: CompanionDeviceManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(CompanionDeviceManager::class.java)
    }

    private val bluetoothViewModel: BluetoothViewModel by viewModels()



    lateinit var txtDeviceStatus: TextView
    lateinit var txtLogStatus: TextView

    lateinit var scanButton: Button

    val foundDevices: MutableLiveData<List<ScanResult>> = MutableLiveData()
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false

    lateinit var btnRead: Button
    lateinit var btnWrite: Button
    lateinit var editTextWriteValue: EditText


    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    Log.i(TAG, "Connected to GATT server.")
                    // Once connected, you can initiate service discovery
                    runOnUiThread {
                        txtDeviceStatus.text = "Device is now connected"
                        scanButton.text = "Disconnect"
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    Log.i(TAG, "Disconnected from GATT server.")
                    runOnUiThread {
                        txtDeviceStatus.text = "Device is disconnected"
                        scanButton.text = "Scan"
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic?.value
                runOnUiThread {
                    txtDeviceStatus.text = "Characteristic Read: ${data?.contentToString()}"
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    txtDeviceStatus.text = "Characteristic Written Successfully"
                }
            }
        }

    }
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) {
            Log.e(TAG, "Characteristic is null. Cannot read.")
            return
        }
        // Make sure the characteristic has the READ property.
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
            Log.e(TAG, "This characteristic cannot be read.")
            return
        }
        // Trigger the BluetoothGatt.readCharacteristic
        bluetoothGatt?.readCharacteristic(characteristic)
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic?, value: ByteArray) {
        if (characteristic == null) {
            Log.e(TAG, "Characteristic is null. Cannot write.")
            return
        }
        // Make sure the characteristic has the WRITE property.
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0) {
            Log.e(TAG, "This characteristic cannot be written.")
            return
        }
        // Set the value to be written
        characteristic.value = value
        // Trigger the BluetoothGatt.writeCharacteristic
        bluetoothGatt?.writeCharacteristic(characteristic)
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: ")

        setContentView(R.layout.activity_selection_device_my)

        // Initialize UI components

        txtDeviceStatus = findViewById(R.id.txtDeviceStatus)
        scanButton = findViewById(R.id.btn_scan)

        bluetoothViewModel.init(this)


        val isSupportCdp =
            packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
        Log.d(TAG, "isSupportCdp: $isSupportCdp")


        // Observe LiveData
        bluetoothViewModel.isConnected.observe(this, Observer { isConnected ->
            if (isConnected) {
                txtDeviceStatus.text = "Device is now connected"
                scanButton.text = "Disconnect"
            } else {
                txtDeviceStatus.text = "Device is disconnected"
                scanButton.text = "Scan"
            }
        })

        bluetoothViewModel.logs.observe(this, Observer { logs ->
            txtLogStatus.text = logs.toString()

        })


        scanButton.setOnClickListener {
            if (isConnected) {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                isConnected = false
                txtDeviceStatus.text = "Device is disconnected"
                scanButton.text = "Scan"
            } else {
                initiateDeviceScan()
            }
        }
    }

    private fun initiateDeviceScan() {
        txtDeviceStatus.text = "Not Connected"
        Log.d(TAG, "Initiating Device Scan.")

        val isSupportCdp =
            packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
        Log.d(TAG, "isSupportCdp: $isSupportCdp")

        // To skip filtering based on name and supported feature flags (UUIDs),
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        val myServiceUUID = "2A38F000-59C8-492B-9358-0E4E38FB0058" //PLV3 UUId
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(myServiceUUID))
            .build()


        val deviceFilter: BluetoothLeDeviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(scanFilter)
            .build()


        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(false)
            .build()

        deviceManager.associate(
            pairingRequest,
            object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    startIntentSenderForResult(
                        chooserLauncher,
                        SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0
                    )
                }

                override fun onFailure(error: CharSequence?) {
                    Log.d("onFailure", "onFailure")
                }
            }, null
        )
    }


    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            // Handle scan result
            val currentList = foundDevices.value ?: emptyList()
            foundDevices.value = currentList + listOfNotNull(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            // Handle batch scan results
        }

        override fun onScanFailed(errorCode: Int) {
            // Handle failure
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            SELECT_DEVICE_REQUEST_CODE -> when (resultCode) {
                RESULT_OK -> {
                    // User has chosen to pair with the Bluetooth device.
                    val deviceObject = data?.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
                    if (deviceObject is ScanResult) {
                        val device = deviceObject.device

                        // Check device type here
                        val deviceType = when (device.type) {
                            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                            BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                            BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
                            else -> "Really Unknown"
                        }

                        Log.d(TAG, "Device successfully paired: ${device.name} of type $deviceType")
                         // Update the TextView here
                        txtDeviceStatus.text = "${device.name} is now connected"
                        bluetoothGatt = device.connectGatt(this, false, gattCallback)
                    } else {
                        txtDeviceStatus.text = "Unexpected object type: $deviceObject\")"
                        Log.d(TAG, "Unexpected object type: $deviceObject")
                    }
                }
            }        else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
    }
}