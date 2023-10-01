package com.rick.companiondevicepairing

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class DeviceRepository {
    private val _isDeviceInRange = MutableLiveData<Boolean>()
    val isDeviceInRange: LiveData<Boolean> get() = _isDeviceInRange

    fun deviceAppeared() {
        _isDeviceInRange.postValue(true)
    }

    fun deviceDisappeared() {
        _isDeviceInRange.postValue(false)
    }
}



