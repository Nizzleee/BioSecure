package com.biosecure.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScanState {
    IDLE,
    SUCCESS,
    ERROR
}

class ScanViewModel : ViewModel() {

    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private var timerJob: Job? = null

    init {
        startTimer()
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                _currentTime.value = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                delay(1000L)
            }
        }
    }

    fun onScanSuccess() {
        _scanState.value = ScanState.SUCCESS
    }

    fun onScanError() {
        _scanState.value = ScanState.ERROR
    }

    fun resetScan() {
        _scanState.value = ScanState.IDLE
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        resetScan()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
