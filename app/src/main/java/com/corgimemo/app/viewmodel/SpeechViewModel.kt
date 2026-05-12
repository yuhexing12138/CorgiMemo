package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.util.SpeechRecognizerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeechViewModel(private val context: Context) : ViewModel() {

    private val speechHelper = SpeechRecognizerHelper(context)

    private val _isListening = MutableStateFlow(false)
    private val _isProcessing = MutableStateFlow(false)
    private val _resultText = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow("")
    private val _permissionGranted = MutableStateFlow(true)

    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    val resultText: StateFlow<String> = _resultText.asStateFlow()
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    init {
        viewModelScope.launch {
            speechHelper.recognitionState.collect { state ->
                when (state) {
                    SpeechRecognizerHelper.RecognitionState.IDLE -> {
                        _isListening.value = false
                        _isProcessing.value = false
                    }
                    SpeechRecognizerHelper.RecognitionState.LISTENING -> {
                        _isListening.value = true
                        _isProcessing.value = false
                    }
                    SpeechRecognizerHelper.RecognitionState.PROCESSING -> {
                        _isListening.value = false
                        _isProcessing.value = true
                    }
                    SpeechRecognizerHelper.RecognitionState.COMPLETED -> {
                        _isListening.value = false
                        _isProcessing.value = false
                    }
                    SpeechRecognizerHelper.RecognitionState.ERROR -> {
                        _isListening.value = false
                        _isProcessing.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            speechHelper.recognitionResult.collect { result ->
                _resultText.value = result
            }
        }

        viewModelScope.launch {
            speechHelper.errorMessage.collect { error ->
                _errorMessage.value = error
            }
        }
    }

    fun startListening() {
        _resultText.value = ""
        _errorMessage.value = ""
        speechHelper.startListening()
    }

    fun stopListening() {
        speechHelper.stopListening()
    }

    fun resetError() {
        _errorMessage.value = ""
    }

    fun setPermissionGranted(granted: Boolean) {
        _permissionGranted.value = granted
    }

    override fun onCleared() {
        super.onCleared()
        speechHelper.destroy()
    }
}
