package com.rick.companiondevicepairing
import MyLogAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.*
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
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi


private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val REQUEST_CODE = 1
private const val REQUEST_CODE_BT_PERMISSIONS = 1001
const val  Relivion_CUSTOM_SERVICE_ID_MG = "2a38f000-59c8-492b-9358-0e4e38fb0056"
const val  DEVICE_LOGS_CHARACTERISTIC_ID_MG = "2a38f002-59c8-492b-9358-0e4e38fb0056"
const val RELIVION_CUSTOM_SERVICE_ID_DP = "2a38f000-59c8-492b-9358-0e4e38fb0058"
const val DEVICE_LOGS_CHARACTERISTIC_ID_DP = "2a38f002-59c8-492b-9358-0e4e38fb0058"


class MyDeviceSelectionActivity : AppCompatActivity() {

    lateinit var viewModel: DeviceLogViewModel
    lateinit var txtDeviceStatus: TextView
    lateinit var scanButton: Button
    lateinit var shareButton: Button

    private val logAdapter = MyLogAdapter()
    lateinit var deviceManager: CompanionDeviceManager
//    private var myCompanionDeviceService: MyCompanionDeviceService? = null


    override fun onStop() {
        super.onStop()
//        viewModel.stopService(this)
    }


    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selection_device_my)


        // Access the globally shared ViewModel
        viewModel = (application as MyApplication).deviceLogViewModel
        deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

        // Check and request permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, request for permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND,
                    Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
                ),
                REQUEST_CODE
            )
        } else {
//            // Permissions are already granted, proceed with your logic
//            startMyCompanionService()
//            // Initialize MyCompanionDeviceService
//            myCompanionDeviceService = MyCompanionDeviceService().apply {
//                // You can pass shared ViewModel or other dependencies here
//                this.setVMFromCompanionService(viewModel)
//            }

        }

        viewModel = ViewModelProvider(this).get(DeviceLogViewModel::class.java)
        lifecycle.addObserver(viewModel)

        txtDeviceStatus = findViewById(R.id.txtDeviceStatus)
        scanButton = findViewById(R.id.btn_scan)
        shareButton = findViewById(R.id.btn_share)

        val logRecyclerView: RecyclerView = findViewById(R.id.logRecyclerView)
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        logRecyclerView.adapter = logAdapter

        gattService()
        checkAndRequestPermissions()
        checkBatteryOptimization(this)

        val bondStateFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(viewModel.bondStateReceiver, bondStateFilter)

        val bondedDevices = BluetoothAdapter.getDefaultAdapter().bondedDevices
        bondedDevices.forEach { device ->
            Log.d("BondedDevice", "Device name: ${device.name}, Address: ${device.address}")
        }


        // Automatically start device scan when app is created.
        if (viewModel.isConnected.value != true) {
            initiateDeviceScan()
        }

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
            viewModel.counter.value = logList.size

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
            if (viewModel.previousConnectionState != connected) {
                viewModel.previousConnectionState = connected
                if (connected) {
                    viewModel.startService(this)  // Start the foreground service when connected
                    viewModel.saveLogsToFile(viewModel.logs.value ?: listOf())
                } else {
                    viewModel.stopService(this)  // Stop the foreground service when disconnected
                }
            }

            txtDeviceStatus.text =
                if (connected) "Device is now connected" else "Device is disconnected"
            scanButton.text = if (connected) "Disconnect" else "Scan"
        }

        viewModel.lastLogTimes.observe(this) { times ->
            val timesString = times.joinToString(", ")
            findViewById<TextView>(R.id.txtLastLogTime).text =
                "Last connection Times:\n$timesString"
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

//    @RequiresApi(Build.VERSION_CODES.S)
//    private fun startMyCompanionService() {
//        val intent = Intent(this, MyCompanionDeviceService::class.java)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            startService(intent)
//        }
//    }

    @RequiresApi(Build.VERSION_CODES.S)
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
            REQUEST_CODE -> {  // The request code for companion permissions
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // All companion permissions are granted, proceed with your functionality
//                    startMyCompanionService()
                } else {
                    // Show rationale and request permissions again or close the app
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

// The rest of your code remains the same.


    private fun initiateDeviceScan() {

        val bondedDevices = BluetoothAdapter.getDefaultAdapter().bondedDevices

        val bondedDeviceMg = bondedDevices.find { device ->
            device.uuids?.any { it.uuid.toString() == Relivion_CUSTOM_SERVICE_ID_MG } ?: false
        }

        val bondedDeviceDp = bondedDevices.find { device ->
            device.uuids?.any { it.uuid.toString() == RELIVION_CUSTOM_SERVICE_ID_DP } ?: false
        }

        if (bondedDeviceMg != null) {
            viewModel.handleBondedDevice(bondedDeviceMg)
        } else if (bondedDeviceDp != null) {
            viewModel.handleBondedDevice(bondedDeviceDp)
        } else {
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
                        Log.e("OnFailure", error.toString())
                    }
                }, null
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELECT_DEVICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val deviceObject = data?.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)


            deviceObject?.let {
                    viewModel.handleActivityResult(it)
                }

            deviceObject?.let {
                if (it is BluetoothDevice && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    it.createBond()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        deviceManager.startObservingDevicePresence(it.address)
                    }
                }
            }
        }
    }


        override fun onDestroy() {
            super.onDestroy()
            unregisterReceiver(viewModel.bondStateReceiver)
        }

    }


