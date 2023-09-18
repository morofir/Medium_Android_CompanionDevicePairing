package com.rick.companiondevicepairing

import MyLogAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.core.view.size


private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val REQUEST_CODE_BT_PERMISSIONS = 1001
val  Relivion_CUSTOM_SERVICE_ID_MG = "2a38f000-59c8-492b-9358-0e4e38fb0056"
val  DEVICE_LOGS_CHARACTERISTIC_ID_MG = "2a38f002-59c8-492b-9358-0e4e38fb0056"

val RELIVION_CUSTOM_SERVICE_ID_DP = "2a38f000-59c8-492b-9358-0e4e38fb0058"
val DEVICE_LOGS_CHARACTERISTIC_ID_DP = "2a38f002-59c8-492b-9358-0e4e38fb0058"




class MyDeviceSelectionActivity : AppCompatActivity() {

    private lateinit var viewModel: DeviceLogViewModel

    lateinit var txtDeviceStatus: TextView
    lateinit var scanButton: Button
    lateinit var shareButton: Button

    val logAdapter = MyLogAdapter()



    private val deviceManager: CompanionDeviceManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(CompanionDeviceManager::class.java)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) { //TODO https://stackoverflow.com/questions/67166985/android-ble-automatic-reconnections-after-pairing
        //todo Create boded device, use Overview of initiating a BLE connection on Android
        //The typical flow for initiating a BLE connection in apps can be broken down into roughly two types:
        //Automa tic connection. The app connects autonomously to a device from returned scan results based on specific heuristics, e.g. we’re scanning for devices advertising certain private service UUIDs and there is only one such device after scanning for a few seconds on low latency mode.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection_device_my)
        viewModel = ViewModelProvider(this).get(DeviceLogViewModel::class.java)
        txtDeviceStatus = findViewById(R.id.txtDeviceStatus)
        scanButton = findViewById(R.id.btn_scan)

        shareButton = findViewById(R.id.btn_share)



        val logRecyclerView: RecyclerView = findViewById(R.id.logRecyclerView)
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        logRecyclerView.adapter = logAdapter


        gattService()

        checkAndRequestPermissions()

        checkBatteryOptimization(this)


        scanButton.setOnClickListener {
            if (viewModel.isConnected.value == true) {
                viewModel.disconnectGatt()
                viewModel.isConnected.postValue(false)
            } else {
                initiateDeviceScan()
            }
        }
        viewModel.logs.observe(this) { logList ->
            logAdapter.submitListItem(logList)
            findViewById<TextView>(R.id.counter).text = "count: ${logList.size}"

            if (logList.isNotEmpty()) {
                shareButton.visibility = View.VISIBLE
                logRecyclerView.post {
                    logRecyclerView.scrollToPosition(logList.size - 1)
                }
            }
            logAdapter.notifyDataSetChanged()
        }

        shareButton.setOnClickListener {
            viewModel.shareLogFile(this)
        }



        viewModel.isConnected.observe(this) { connected ->

            // Check if the connection state is changed
            if(viewModel.previousConnectionState != connected) {
                viewModel.previousConnectionState = connected
                viewModel.saveLogsToFile(viewModel.logs.value ?: listOf())
            }


            txtDeviceStatus.text =
                if (connected) "Device is now connected" else "Device is disconnected"
            scanButton.text = if (connected) "Disconnect" else "Scan"

        }

        viewModel.lastLogTime.observe(this) { time ->
            findViewById<TextView>(R.id.txtLastLogTime).text = "Last Log Time: $time"
        }
    }


    private fun gattService() {
        // Initialize your BluetoothGattServer and set the callback
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val gattServer = bluetoothManager.openGattServer(this, viewModel.gattServerCallback)

        // MG Service and Characteristic
        createAndAddServiceToGattServer(
            gattServer,
            Relivion_CUSTOM_SERVICE_ID_MG,
            DEVICE_LOGS_CHARACTERISTIC_ID_MG
        )

        // DP Service and Characteristic
        createAndAddServiceToGattServer(
            gattServer,
            RELIVION_CUSTOM_SERVICE_ID_DP,
            DEVICE_LOGS_CHARACTERISTIC_ID_DP
        )
    }

    private fun createAndAddServiceToGattServer(
        gattServer: BluetoothGattServer?,
        serviceId: String,
        characteristicId: String
    ) {
        val serviceUUID = UUID.fromString(serviceId)
        val characteristicUUID = UUID.fromString(characteristicId)

        // Create and add service
        val service = BluetoothGattService(
            serviceUUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val characteristic = BluetoothGattCharacteristic(
            characteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Descriptor for enabling notifications
        val configDescriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )
        characteristic.addDescriptor(configDescriptor)

        service.addCharacteristic(characteristic)

        gattServer?.addService(service)
    }



    // Permission during runtime
    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        val bluetoothPermission = Manifest.permission.BLUETOOTH
        val bluetoothAdminPermission = Manifest.permission.BLUETOOTH_ADMIN
        val bluetoothConnectPermission = "android.permission.BLUETOOTH_CONNECT"

        if (ContextCompat.checkSelfPermission(
                this,
                bluetoothPermission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(bluetoothPermission)
        }
        if (ContextCompat.checkSelfPermission(
                this,
                bluetoothAdminPermission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(bluetoothAdminPermission)
        }
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
                this,
                bluetoothConnectPermission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(bluetoothConnectPermission)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                REQUEST_CODE_BT_PERMISSIONS
            )
        }
    }

    fun checkBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = context.packageName
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                context.startActivity(intent)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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

    private fun initiateDeviceScan() {
        // For MG service
        val scanFilterMg = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(Relivion_CUSTOM_SERVICE_ID_MG))
            .build()

        val deviceFilterMg: BluetoothLeDeviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(scanFilterMg)
            .build()

        // For DP service
        val scanFilterDp = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(RELIVION_CUSTOM_SERVICE_ID_DP))
            .build()

        val deviceFilterDp: BluetoothLeDeviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(scanFilterDp)  // Use the correct scan filter
            .build()

        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilterMg)
            .addDeviceFilter(deviceFilterDp)
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
            val deviceObject =
                data?.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
            deviceObject?.let { viewModel.handleActivityResult(it) }
        }
    }


}
