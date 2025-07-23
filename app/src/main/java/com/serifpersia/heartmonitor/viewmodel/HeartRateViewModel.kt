package com.serifpersia.heartmonitor.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serifpersia.heartmonitor.data.AppDatabase
import com.serifpersia.heartmonitor.data.HeartRateDao
import com.serifpersia.heartmonitor.data.HeartRateSession
import com.serifpersia.heartmonitor.data.HeartRateData
import com.serifpersia.heartmonitor.data.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class AppState {
    PERMISSION_DENIED,
    INSTRUCTIONS,
    CALIBRATING,
    MONITORING,
    SESSION_ENDED
}

data class HeartRateZone(val name: String, val color: Color)

data class HeartRateUiState(
    val appState: AppState = AppState.INSTRUCTIONS,
    val heartRate: String = "0",
    val isPulsing: Boolean = false,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val avgBpm: Int? = null,
    val sessionDuration: Int = 0,
    val smoothedDataPoints: List<Float> = emptyList(),
    val heartRateZone: HeartRateZone? = null
)

class HeartRateViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val userSettings = UserSettings(application)

    private val heartRateDao: HeartRateDao = AppDatabase.getDatabase(application).heartRateDao()
    val history = heartRateDao.getAllSessions()

    private val rawPpgSensor: Sensor? = sensorManager.getSensorList(Sensor.TYPE_ALL).find { it.stringType.contains("hrm_led_ir", ignoreCase = true) }
    private val heartRateBpmSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState = _uiState.asStateFlow()

    private var sessionJob: Job? = null
    private var ppgData = mutableListOf<Float>()
    private var bpmHistory = mutableListOf<Int>()
    private var lastPeakTimestamp = 0L
    private var isRising = false
    private var previousSmoothedValue = 0f
    private var lastVibrationTime = 0L
    private var estimatedBeatInterval = 600L

    fun startSession() {
        if (rawPpgSensor == null || heartRateBpmSensor == null) {
            _uiState.update { it.copy(appState = AppState.INSTRUCTIONS) }
            return
        }
        resetSessionState()
        _uiState.update { it.copy(appState = AppState.CALIBRATING) }
        sensorManager.registerListener(this, rawPpgSensor, SensorManager.SENSOR_DELAY_FASTEST)
        sensorManager.registerListener(this, heartRateBpmSensor, SensorManager.SENSOR_DELAY_NORMAL)

        sessionJob = viewModelScope.launch {
            launch {
                delay(5000)
                _uiState.update { it.copy(appState = AppState.MONITORING) }
            }
            while (true) {
                delay(1000)
                _uiState.update { it.copy(sessionDuration = it.sessionDuration + 1) }
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sensorManager.unregisterListener(this)
        viewModelScope.launch {
            val finalState = _uiState.value
            if (finalState.avgBpm != null && finalState.minBpm != null && finalState.maxBpm != null && finalState.sessionDuration > 5) {
                val session = HeartRateSession(
                    timestamp = System.currentTimeMillis(),
                    durationSeconds = finalState.sessionDuration,
                    avgBpm = finalState.avgBpm,
                    minBpm = finalState.minBpm,
                    maxBpm = finalState.maxBpm
                )
                heartRateDao.insertSession(session)
            }
        }
        _uiState.update { it.copy(appState = AppState.SESSION_ENDED) }
    }

    fun resetToInstructions() {
        resetSessionState()
        _uiState.update { it.copy(appState = AppState.INSTRUCTIONS) }
    }

    fun deleteSession(session: HeartRateSession) {
        viewModelScope.launch {
            heartRateDao.deleteSession(session.id)
        }
    }

    private fun resetSessionState() {
        ppgData.clear()
        bpmHistory.clear()
        _uiState.value = HeartRateUiState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || _uiState.value.appState == AppState.INSTRUCTIONS || _uiState.value.appState == AppState.SESSION_ENDED) return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> processHeartRateBpm(event.values.firstOrNull()?.toInt() ?: 0)
        }
        if (event.sensor == rawPpgSensor) {
            processRawPpg(event.values.firstOrNull() ?: 0f)
        }
    }

    private fun processHeartRateBpm(bpm: Int) {
        if (bpm <= 0 || _uiState.value.appState != AppState.MONITORING) return

        bpmHistory.add(bpm)
        if (bpmHistory.size > 200) bpmHistory.removeAt(0)

        _uiState.update {
            it.copy(
                heartRate = bpm.toString(),
                minBpm = bpmHistory.minOrNull(),
                maxBpm = bpmHistory.maxOrNull(),
                avgBpm = if (bpmHistory.isNotEmpty()) bpmHistory.average().roundToInt() else null,
                heartRateZone = getHeartRateZone(bpm)
            )
        }
    }

    private fun processRawPpg(rawValue: Float) {
        if (rawValue < 100000f) return

        ppgData.add(rawValue)
        if (ppgData.size > 100) ppgData.removeAt(0)

        if (ppgData.size >= 5) {
            val smoothedValue = ppgData.takeLast(5).average().toFloat()
            val currentSmoothedData = (_uiState.value.smoothedDataPoints + smoothedValue).takeLast(100)
            _uiState.update { it.copy(smoothedDataPoints = currentSmoothedData) }

            if (_uiState.value.appState == AppState.MONITORING) {
                val amplitude = (currentSmoothedData.maxOrNull() ?: 0f) - (currentSmoothedData.minOrNull() ?: 0f)
                if (amplitude > 2000f) {
                    if (isRising && smoothedValue < previousSmoothedValue) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastPeakTimestamp > 300L) {
                            lastPeakTimestamp = currentTime
                            triggerPulseAndVibration()
                        }
                    }
                    isRising = smoothedValue > previousSmoothedValue
                    previousSmoothedValue = smoothedValue
                }
            }
        }
    }

    private fun triggerPulseAndVibration() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPulsing = true) }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastVibrationTime >= estimatedBeatInterval * 0.8) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
                lastVibrationTime = currentTime
            }
            delay(150)
            _uiState.update { it.copy(isPulsing = false) }
        }
    }

    private fun getHeartRateZone(bpm: Int): HeartRateZone? {
        val age = userSettings.getAge()
        val ageGroup = HeartRateData.ageGroups.find { age in it.ageRange } ?: return null
        val zoneData = ageGroup.zones.find { bpm in it.range } ?: return null
        return HeartRateZone(zoneData.name, zoneData.color)
    }

    fun getAge(): Int {
        return userSettings.getAge()
    }

    fun setAge(age: Int) {
        userSettings.setAge(age)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }
}