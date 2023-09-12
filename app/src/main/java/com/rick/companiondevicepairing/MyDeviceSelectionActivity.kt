@file:Suppress("OverrideDeprecatedMigration", "OverrideDeprecatedMigration")

package com.rick.companiondevicepairing

import DeviceLogViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.os.Parcelable
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider


private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val REQUEST_CODE_BT_PERMISSIONS = 1001

class MyDeviceSelectionActivity : AppCompatActivity() {

    companion object {
        private val TAG = MyDeviceSelectionActivity::class.java.simpleName
    }

    private val deviceManager: CompanionDeviceManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(CompanionDeviceManager::class.java)
    }
    private lateinit var viewModel: DeviceLogViewModel


    lateinit var txtDeviceStatus: TextView
    lateinit var txtLogStatus: TextView
    lateinit var scanButton: Button
    val foundDevices: MutableLiveData<List<ScanResult>> = MutableLiveData()
    private var bluetoothGatt: BluetoothGatt? = null
    var isConnected = MutableLiveData<Boolean>()
    val logs: MutableLiveData<List<String>> = MutableLiveData(listOf())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection_device_my)
        viewModel = ViewModelProvider(this).get(DeviceLogViewModel::class.java)

        txtDeviceStatus = findViewById(R.id.txtDeviceStatus)
        txtLogStatus = findViewById(R.id.txtLogStatus)

        scanButton = findViewById(R.id.btn_scan)

        checkAndRequestPermissions()


        scanButton.setOnClickListener {
            if (isConnected.value == true) {
                bluetoothGatt?.disconnect()
                isConnected.postValue(false)
            } else {
                initiateDeviceScan()
            }
        }
    }

    // Permission during runtime
    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        val bluetoothPermission = Manifest.permission.BLUETOOTH
        val bluetoothAdminPermission = Manifest.permission.BLUETOOTH_ADMIN
        val bluetoothConnectPermission = "android.permission.BLUETOOTH_CONNECT"

        if (ContextCompat.checkSelfPermission(this, bluetoothPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(bluetoothPermission)
        }
        if (ContextCompat.checkSelfPermission(this, bluetoothAdminPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(bluetoothAdminPermission)
        }
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, bluetoothConnectPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(bluetoothConnectPermission)
        }


        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_CODE_BT_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_BT_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // All permissions are granted, proceed with your functionality
                } else {
                    // Show rationale and request permissions again or close the app
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("SetTextI18n")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected.postValue(true)
                    addLog("Device connected")
                    runOnUiThread {
                        txtDeviceStatus.text = "Device is now connected"
                        scanButton.text = "Disconnect"
                    }

                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected.postValue(false)
                    addLog("Device Disconnected")
                    runOnUiThread {
                        txtDeviceStatus.text = "Device is disconnected"
                        scanButton.text = "Scan"
                    }

                }
            }
        }

    }

    private fun initiateDeviceScan() {
        val myServiceUUID = "2A38F000-59C8-492B-9358-0E4E38FB0058"
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(myServiceUUID))
            .build()

        val deviceFilter: BluetoothLeDeviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(scanFilter)
            .build()

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
                }
            }, null
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_DEVICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val deviceObject = data?.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
            if (deviceObject is ScanResult) {
                val device = deviceObject.device
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
            }
        }
    }

    private fun addLog(message: String) {
        val currentLogs = logs.value?.toMutableList() ?: mutableListOf()
        currentLogs.add(message)
        logs.postValue(currentLogs)
        runOnUiThread {
            txtLogStatus.text = currentLogs.joinToString("\n")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
    }
}
