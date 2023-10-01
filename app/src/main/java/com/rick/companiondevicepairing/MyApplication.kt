package com.rick.companiondevicepairing
import android.app.Application


class MyApplication : Application() {
    lateinit var deviceLogViewModel: DeviceLogViewModel

    override fun onCreate() {
        super.onCreate()
        deviceLogViewModel = DeviceLogViewModel(this)
    }
}
