package com.serifpersia.heartmonitor.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serifpersia.heartmonitor.viewmodel.AppState
import com.serifpersia.heartmonitor.viewmodel.HeartRateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(viewModel: HeartRateViewModel, onNavigateToHistory: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heart Rate Monitor") },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (uiState.appState) {
                AppState.INSTRUCTIONS -> InstructionsView(onStart = { viewModel.startSession() })
                AppState.CALIBRATING -> StatusView("Calibrating...", "Keep your finger still.")
                AppState.MONITORING -> LiveDataView(viewModel)
                AppState.SESSION_ENDED -> SessionEndedView(
                    onRestart = { viewModel.resetToInstructions() },
                    onShowHistory = onNavigateToHistory
                )
                else -> {}
            }
            if (showSettingsDialog) {
                SettingsDialog(
                    currentAge = viewModel.getAge(),
                    onDismiss = { showSettingsDialog = false },
                    onSetAge = {
                        viewModel.setAge(it)
                        showSettingsDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun LiveDataView(viewModel: HeartRateViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val pulseScale by animateFloatAsState(if (uiState.isPulsing) 1.2f else 1.0f, tween(100), label = "")
    val heartColor by animateColorAsState(if (uiState.isPulsing) Color.White else uiState.heartRateZone?.color ?: MaterialTheme.colorScheme.primary, tween(100), label = "")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Heartbeat",
                tint = heartColor,
                modifier = Modifier.size(50.dp).scale(pulseScale)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = uiState.heartRate,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "bpm",
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 24.dp, start = 4.dp)
            )
        }

        uiState.heartRateZone?.let { zone ->
            Text(
                text = zone.name,
                color = zone.color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
        }

        HeartRateGraph(dataPoints = uiState.smoothedDataPoints, graphColor = uiState.heartRateZone?.color ?: MaterialTheme.colorScheme.primary)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Avg", uiState.avgBpm?.toString() ?: "-")
            StatItem("Min", uiState.minBpm?.toString() ?: "-")
            StatItem("Max", uiState.maxBpm?.toString() ?: "-")
            StatItem("Time", formatDuration(uiState.sessionDuration))
        }

        Button(
            onClick = { viewModel.stopSession() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
            Spacer(Modifier.width(8.dp))
            Text("Stop Session", fontSize = 18.sp)
        }
    }
}

@Composable
fun InstructionsView(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        Text("Instructions", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Place your finger firmly over the device's heart rate sensor. Press 'Start' and remain still for an accurate reading.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Start", fontSize = 18.sp)
        }
    }
}

@Composable
fun SessionEndedView(onRestart: () -> Unit, onShowHistory: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Session Saved", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onShowHistory, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("View History", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Start New Session", fontSize = 18.sp)
        }
    }
}

@Composable
fun StatusView(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HeartRateGraph(dataPoints: List<Float>, graphColor: Color, modifier: Modifier = Modifier) {
    val normalizedLinePath = remember(dataPoints) {
        if (dataPoints.size < 2) {
            Path()
        } else {
            val maxVal = dataPoints.maxOrNull() ?: 0f
            val minVal = dataPoints.minOrNull() ?: 0f
            val range = (maxVal - minVal).coerceAtLeast(1f)
            Path().apply {
                moveTo(0f, 1f - ((dataPoints[0] - minVal) / range))
                for (i in 1 until dataPoints.size) {
                    val x = i.toFloat() / (dataPoints.size - 1)
                    val y = 1f - ((dataPoints[i] - minVal) / range)
                    lineTo(x, y)
                }
            }
        }
    }

    val normalizedFillPath = remember(dataPoints) {
        if (dataPoints.size < 2) {
            Path()
        } else {
            val maxVal = dataPoints.maxOrNull() ?: 0f
            val minVal = dataPoints.minOrNull() ?: 0f
            val range = (maxVal - minVal).coerceAtLeast(1f)
            Path().apply {
                moveTo(0f, 1f)
                lineTo(0f, 1f - ((dataPoints[0] - minVal) / range))
                for (i in 1 until dataPoints.size) {
                    val x = i.toFloat() / (dataPoints.size - 1)
                    val y = 1f - ((dataPoints[i] - minVal) / range)
                    lineTo(x, y)
                }
                lineTo(1f, 1f)
                close()
            }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp).padding(vertical = 16.dp)) {
        val matrix = androidx.compose.ui.graphics.Matrix()
        matrix.scale(size.width, size.height)

        val scaledLinePath = Path()
        scaledLinePath.addPath(normalizedLinePath)
        scaledLinePath.transform(matrix)

        val scaledFillPath = Path()
        scaledFillPath.addPath(normalizedFillPath)
        scaledFillPath.transform(matrix)

        drawPath(
            path = scaledFillPath,
            brush = Brush.verticalGradient(colors = listOf(graphColor.copy(alpha = 0.4f), graphColor.copy(alpha = 0.0f)))
        )
        drawPath(path = scaledLinePath, color = graphColor, style = Stroke(width = 5f))
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

@Composable
fun SettingsDialog(currentAge: Int, onDismiss: () -> Unit, onSetAge: (Int) -> Unit) {
    var ageInput by remember { mutableStateOf(currentAge.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Your Age") },
        text = {
            OutlinedTextField(
                value = ageInput,
                onValueChange = { ageInput = it },
                label = { Text("Age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            Button(onClick = { 
                val age = ageInput.toIntOrNull()
                if (age != null) {
                    onSetAge(age)
                }
            }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}