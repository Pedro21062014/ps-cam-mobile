package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.AppMode
import com.example.ui.screens.CameraTransmitterScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.QRScannerScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.screens.ViewerMonitorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PsCamTheme
import com.example.ui.theme.PsSkyPrimary
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Prevent screen from sleeping while using PS Cam surveillance
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val appMode by viewModel.appMode.collectAsState()
                val colors = PsCamTheme.colors

                // Handle system back button
                if (appMode != AppMode.DASHBOARD) {
                    BackHandler {
                        viewModel.setAppMode(AppMode.DASHBOARD)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                ) {
                    when (appMode) {
                        AppMode.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                        AppMode.CAMERA_HOST -> CameraPermissionWrapper {
                            CameraTransmitterScreen(viewModel = viewModel)
                        }
                        AppMode.VIEWER -> ViewerMonitorScreen(viewModel = viewModel)
                        AppMode.SCANNER -> CameraPermissionWrapper {
                            QRScannerScreen(viewModel = viewModel)
                        }
                        AppMode.TIMELINE -> TimelineScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPermissionWrapper(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = PsCamTheme.colors
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    if (hasPermission) {
        content()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Permissões de Câmera e Microfone",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "O PS Cam precisa de acesso à câmera e microfone para transmissão em tempo real (< 200ms) e segurança.",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Conceder Permissão", color = if (colors.isDark) colors.background else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
