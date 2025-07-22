package com.example.heartmonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.heartmonitor.ui.theme.HeartMonitorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BpmScreenWithPermissionCheck()
                }
            }
        }
    }
}

@Composable
fun BpmScreenWithPermissionCheck() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    if (hasPermission) {
        BpmScreen()
    } else {
        PermissionDeniedScreen()
    }
}

private const val MAX_DATA_POINTS = 100
private const val BPM_HISTORY_MAX_SIZE = 200
private const val FINGER_ON_SENSOR_THRESHOLD = 100000f
private const val MIN_TIME_BETWEEN_PEAKS_MS = 300L
private const val MIN_SIGNAL_AMPLITUDE = 2000f
private const val CALIBRATION_DURATION_MS = 10000L
private const val MOVING_AVERAGE_WINDOW = 5

@Composable
fun BpmScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val rawPpgSensor: Sensor? = remember {
        sensorManager.getSensorList(Sensor.TYPE_ALL).find {
            it.stringType.contains("hrm_led_ir", ignoreCase = true)
        }
    }
    val heartRateBpmSensor: Sensor? = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    }

    var heartRate by remember { mutableStateOf("N/A") }
    val graphDataPoints = remember { mutableStateListOf<Float>() }
    val smoothedDataPoints = remember { mutableStateListOf<Float>() }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var isPulsing by remember { mutableStateOf(false) }
    var minBpm by remember { mutableStateOf<Int?>(null) }
    var maxBpm by remember { mutableStateOf<Int?>(null) }
    var avgBpm by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(isPulsing) {
        if (isPulsing) {
            delay(200)
            isPulsing = false
        }
    }

    DisposableEffect(rawPpgSensor, heartRateBpmSensor) {
        val processingData = object {
            var isRising = false
            var previousRawValue = 0f
            var lastPeakTimestamp = 0L
            var lastVibrationTime = 0L
            var calibrationStartTime = 0L
            var isCalibrating = true
            var estimatedBeatInterval = 600L

            val ppgData = mutableListOf<Float>()
            val smoothedPpgData = mutableListOf<Float>()
            val bpmHistory = mutableListOf<Int>()

            var currentHeartRate: String = "N/A"
            var currentStatus: String = "Initializing..."
            var currentMinBpm: Int? = null
            var currentMaxBpm: Int? = null
            var currentAvgBpm: Int? = null
        }

        val sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_HEART_RATE -> {
                        val bpm = event.values.firstOrNull()?.toInt() ?: 0
                        if (bpm > 0) {
                            processingData.currentHeartRate = bpm.toString()
                            processingData.bpmHistory.add(bpm)
                            if (processingData.bpmHistory.size > BPM_HISTORY_MAX_SIZE) {
                                processingData.bpmHistory.removeAt(0)
                            }
                            processingData.currentMinBpm = processingData.bpmHistory.minOrNull()
                            processingData.currentMaxBpm = processingData.bpmHistory.maxOrNull()
                            processingData.currentAvgBpm = processingData.bpmHistory.average().roundToInt()

                            if (processingData.isCalibrating) {
                                processingData.estimatedBeatInterval = (60000 / bpm).toLong()
                            }
                        }
                    }
                }

                if (event.sensor == rawPpgSensor) {
                    val rawValue = event.values.firstOrNull() ?: 0f

                    if (rawValue < FINGER_ON_SENSOR_THRESHOLD) {
                        if (processingData.ppgData.isNotEmpty()) {
                            processingData.ppgData.clear()
                            processingData.smoothedPpgData.clear()
                            processingData.bpmHistory.clear()
                            processingData.currentHeartRate = "N/A"
                            processingData.currentMinBpm = null; processingData.currentMaxBpm = null; processingData.currentAvgBpm = null
                            processingData.isCalibrating = true
                            processingData.calibrationStartTime = 0L
                        }
                        processingData.currentStatus = "Place finger on sensor."
                        processingData.previousRawValue = 0f
                        return
                    } else {
                        processingData.currentStatus = if (processingData.isCalibrating) "Calibrating..." else "Reading..."
                    }

                    processingData.ppgData.add(rawValue)
                    if (processingData.ppgData.size > MAX_DATA_POINTS) {
                        processingData.ppgData.removeAt(0)
                    }
                    if (processingData.ppgData.size >= MOVING_AVERAGE_WINDOW) {
                        val smoothedValue = processingData.ppgData.takeLast(MOVING_AVERAGE_WINDOW).average().toFloat()
                        processingData.smoothedPpgData.add(smoothedValue)
                        if (processingData.smoothedPpgData.size > MAX_DATA_POINTS) {
                            processingData.smoothedPpgData.removeAt(0)
                        }
                    }

                    if (processingData.isCalibrating) {
                        if (processingData.calibrationStartTime == 0L) {
                            processingData.calibrationStartTime = System.currentTimeMillis()
                        }
                        if (System.currentTimeMillis() - processingData.calibrationStartTime > CALIBRATION_DURATION_MS) {
                            processingData.isCalibrating = false
                        }
                    } else {
                        if (processingData.smoothedPpgData.size > 20) {
                            val maxVal = processingData.smoothedPpgData.maxOrNull() ?: 0f
                            val minVal = processingData.smoothedPpgData.minOrNull() ?: 0f
                            val amplitude = maxVal - minVal

                            if (amplitude > MIN_SIGNAL_AMPLITUDE) {
                                val currentSmoothedValue = processingData.smoothedPpgData.last()
                                if (processingData.isRising && currentSmoothedValue < processingData.previousRawValue) {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - processingData.lastPeakTimestamp > MIN_TIME_BETWEEN_PEAKS_MS) {
                                        isPulsing = true
                                        processingData.lastPeakTimestamp = currentTime
                                        if (currentTime - processingData.lastVibrationTime >= processingData.estimatedBeatInterval * 0.8) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator.vibrate(50)
                                            }
                                            processingData.lastVibrationTime = currentTime
                                        }
                                    }
                                }
                                processingData.isRising = currentSmoothedValue > processingData.previousRawValue
                                processingData.previousRawValue = currentSmoothedValue
                            }
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rawPpgSensor == null || heartRateBpmSensor == null) {
            statusMessage = "Required heart rate sensors not found."
        } else {
            statusMessage = "Place finger on sensor."
            sensorManager.registerListener(sensorEventListener, rawPpgSensor, SensorManager.SENSOR_DELAY_FASTEST)
            sensorManager.registerListener(sensorEventListener, heartRateBpmSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val job = scope.launch {
            while (true) {
                graphDataPoints.clear()
                graphDataPoints.addAll(processingData.ppgData)
                smoothedDataPoints.clear()
                smoothedDataPoints.addAll(processingData.smoothedPpgData)

                heartRate = processingData.currentHeartRate
                statusMessage = processingData.currentStatus
                minBpm = processingData.currentMinBpm
                maxBpm = processingData.currentMaxBpm
                avgBpm = processingData.currentAvgBpm

                delay(16L)
            }
        }

        onDispose {
            job.cancel()
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (isPulsing) 1.1f else 1.0f,
        animationSpec = tween(durationMillis = 100), label = "pulseScale"
    )
    val heartColor by animateColorAsState(
        targetValue = if (isPulsing) Color.White else Color(0xFFF44336),
        animationSpec = tween(durationMillis = 100), label = "heartColor"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Heart Rate Monitor",
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            text = statusMessage,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Heartbeat",
                    tint = heartColor,
                    modifier = Modifier.size(60.dp).scale(pulseScale)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = heartRate,
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "bpm",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 24.dp, start = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "Avg", value = avgBpm?.toString() ?: "-")
            StatItem(label = "Min", value = minBpm?.toString() ?: "-")
            StatItem(label = "Max", value = maxBpm?.toString() ?: "-")
        }

        HeartRateGraph(dataPoints = smoothedDataPoints)
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HeartRateGraph(dataPoints: List<Float>, modifier: Modifier = Modifier) {
    val graphColor = Color(0xFFF44336)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 24.dp)
    ) {
        val maxRaw = dataPoints.maxOrNull() ?: 0f
        val minRaw = dataPoints.minOrNull() ?: 0f

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (dataPoints.size < 2) {
                val centerLineY = size.height / 2
                drawLine(
                    color = graphColor.copy(alpha = 0.5f),
                    start = Offset(0f, centerLineY),
                    end = Offset(size.width, centerLineY),
                    strokeWidth = 4f
                )
                return@Canvas
            }

            val rawRange = (maxRaw - minRaw).coerceAtLeast(1f)
            val linePath = Path()
            val fillPath = Path()

            fun getPoint(index: Int): Offset {
                val x = index.toFloat() / (dataPoints.size - 1) * size.width
                val y = size.height - ((dataPoints[index] - minRaw) / rawRange * size.height)
                return Offset(x, y)
            }

            val firstPoint = getPoint(0)
            linePath.moveTo(firstPoint.x, firstPoint.y)
            fillPath.moveTo(firstPoint.x, size.height)
            fillPath.lineTo(firstPoint.x, firstPoint.y)

            for (i in 0 until dataPoints.size - 1) {
                val p1 = getPoint(i)
                val p2 = getPoint(i + 1)
                val controlPoint1 = Offset((p1.x + p2.x) / 2f, p1.y)
                val controlPoint2 = Offset((p1.x + p2.x) / 2f, p2.y)
                linePath.cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p2.x, p2.y)
                fillPath.cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, p2.x, p2.y)
            }

            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(graphColor.copy(alpha = 0.4f), graphColor.copy(alpha = 0.0f))
                )
            )

            drawPath(
                path = linePath,
                color = graphColor,
                style = Stroke(width = 4f)
            )
        }
    }
}

@Composable
fun PermissionDeniedScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Permission Denied!",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Please grant BODY SENSORS permission in app settings to use the heart rate monitor.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BpmScreenPreview() {
    HeartMonitorTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Heart Rate Monitor", fontSize = 24.sp)
            Text("Reading...", fontSize = 16.sp)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, "Heart", tint = Color(0xFFF44336), modifier = Modifier.size(60.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("72", fontSize = 80.sp, fontWeight = FontWeight.Bold)
                    Text("bpm", fontSize = 24.sp, modifier = Modifier.padding(top = 24.dp, start = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(label = "Avg", value = "75")
                StatItem(label = "Min", value = "68")
                StatItem(label = "Max", value = "81")
            }
            HeartRateGraph(dataPoints = List(100) { 70f + kotlin.random.Random.nextFloat() * 10f })
        }
    }
}