package com.serifpersia.heartmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.serifpersia.heartmonitor.navigation.AppNavigator
import com.serifpersia.heartmonitor.navigation.Routes
import com.serifpersia.heartmonitor.ui.screens.PermissionScreen
import com.serifpersia.heartmonitor.ui.theme.HeartMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartMonitorTheme {
                var hasPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted -> hasPermission = isGranted }
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasPermission) {
                        AppNavigator(startDestination = Routes.MONITOR)
                    } else {
                        LaunchedEffect(Unit) {
                            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                        }
                        PermissionScreen(onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                        })
                    }
                }
            }
        }
    }
}