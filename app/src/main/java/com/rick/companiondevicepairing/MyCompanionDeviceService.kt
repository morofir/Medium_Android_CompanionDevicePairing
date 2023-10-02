package com.rick.companiondevicepairing
//
//import android.companion.CompanionDeviceService
//import android.os.Build
//import android.util.Log
//import androidx.annotation.RequiresApi
//import androidx.lifecycle.Observer
//
//@RequiresApi(Build.VERSION_CODES.S)
//class MyCompanionDeviceService : CompanionDeviceService() {
//
//    private lateinit var sharedDeviceLogViewModel: DeviceLogViewModel
//    private val observer = Observer<Boolean> { isConnected ->
//        Log.d("MyCompanionDeviceService", "Device connected: $isConnected")
//    }
//
//
//
//    // Method to set ViewModel
//    fun setVMFromCompanionService(deviceLogViewModel: DeviceLogViewModel) {
//        this.sharedDeviceLogViewModel = deviceLogViewModel
//        sharedDeviceLogViewModel.isConnected.observeForever(observer)
//    }
//
//    override fun onDeviceAppeared(deviceID: String) {
//        Log.e("DEBUG", "DEVICE APPEARED $deviceID")
//        ForegroundService.foundDevice(this, deviceID)
//    }
//
//    override fun onDeviceDisappeared(deviceID: String) {
//        Log.e("DEBUG", "DEVICE DISAPPEARED $deviceID")
//        ForegroundService.lostDevice(this, deviceID)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        sharedDeviceLogViewModel.isConnected.removeObserver(observer)
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.e("DEBUG", "SERVICE CREATED")
//    }
//
//
//
//}