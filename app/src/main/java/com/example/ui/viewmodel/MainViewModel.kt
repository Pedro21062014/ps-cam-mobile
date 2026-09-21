package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.HttpStreamServer
import com.example.data.PSCamDatabase
import com.example.data.SavedDeviceEntity
import com.example.data.SurveillanceEventEntity
import com.example.firebase.CloudCameraDevice
import com.example.firebase.FirebaseManager
import com.example.model.AppMode
import com.example.model.CameraFacing
import com.example.model.VideoQuality
import com.example.util.NetworkUtils
import com.example.util.SirenSynthesizer
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PSCamDatabase.getDatabase(application)
    private val deviceDao = db.deviceDao()
    private val surveillanceDao = db.surveillanceDao()
    val firebaseManager = FirebaseManager.getInstance(application)

    // Room persistence
    val savedDevices: StateFlow<List<SavedDeviceEntity>> = deviceDao.getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val surveillanceEvents: StateFlow<List<SurveillanceEventEntity>> = surveillanceDao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Firebase state
    val currentUser: StateFlow<FirebaseUser?> = firebaseManager.currentUser
    val cloudCameras: StateFlow<List<CloudCameraDevice>> = firebaseManager.cloudCameras

    // Theme state (default light theme, toggleable to dark theme)
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Navigation App Mode
    private val _appMode = MutableStateFlow(AppMode.DASHBOARD)
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    // Camera Host State
    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _isSirenOn = MutableStateFlow(false)
    val isSirenOn: StateFlow<Boolean> = _isSirenOn.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _cameraFacing = MutableStateFlow(CameraFacing.BACK)
    val cameraFacing: StateFlow<CameraFacing> = _cameraFacing.asStateFlow()

    private val _videoQuality = MutableStateFlow(VideoQuality.HD)
    val videoQuality: StateFlow<VideoQuality> = _videoQuality.asStateFlow()

    private val _isBlackScreen = MutableStateFlow(false)
    val isBlackScreen: StateFlow<Boolean> = _isBlackScreen.asStateFlow()

    private val _motionScore = MutableStateFlow(0f)
    val motionScore: StateFlow<Float> = _motionScore.asStateFlow()

    private val _isMotionDetected = MutableStateFlow(false)
    val isMotionDetected: StateFlow<Boolean> = _isMotionDetected.asStateFlow()

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    // Settings
    private val _motionSensitivity = MutableStateFlow(0.20f)
    val motionSensitivity: StateFlow<Float> = _motionSensitivity.asStateFlow()

    private val _autoSirenOnMotion = MutableStateFlow(false)
    val autoSirenOnMotion: StateFlow<Boolean> = _autoSirenOnMotion.asStateFlow()

    // Viewer State
    private val _targetHostIp = MutableStateFlow("")
    val targetHostIp: StateFlow<String> = _targetHostIp.asStateFlow()

    private val _targetPort = MutableStateFlow(8080)
    val targetPort: StateFlow<Int> = _targetPort.asStateFlow()

    private val _targetDeviceName = MutableStateFlow("Câmera Remota")
    val targetDeviceName: StateFlow<String> = _targetDeviceName.asStateFlow()

    private val sirenSynthesizer = SirenSynthesizer()
    var httpStreamServer: HttpStreamServer? = null
        private set

    private var motionResetJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastMotionEventTime = 0L

    private val localDeviceId: String by lazy {
        val sanitizedModel = Build.MODEL.replace("[^a-zA-Z0-9]".toRegex(), "_").lowercase()
        "cam_${sanitizedModel}_8080"
    }

    init {
        refreshLocalIp()
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun setDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
    }

    fun refreshLocalIp() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = NetworkUtils.getLocalIpAddress()
            _localIp.value = ip
        }
    }

    fun setAppMode(mode: AppMode) {
        _appMode.value = mode
        if (mode == AppMode.CAMERA_HOST) {
            startStreamingServer()
            publishToCloudCatalog()
        } else if (mode == AppMode.DASHBOARD) {
            stopStreamingServer()
            setCloudCameraOffline()
            _isBlackScreen.value = false
            stopSiren()
            _isFlashOn.value = false
        }
    }

    fun startStreamingServer() {
        if (httpStreamServer == null) {
            httpStreamServer = HttpStreamServer(port = 8080) { cmd, _ ->
                handleRemoteCommand(cmd)
            }.apply {
                start(viewModelScope)
            }
        }
    }

    fun stopStreamingServer() {
        httpStreamServer?.stop()
        httpStreamServer = null
    }

    private fun publishToCloudCatalog() {
        val currentIp = _localIp.value
        val devName = "PS Cam - ${Build.MODEL.ifEmpty { "Dispositivo" }}"
        val user = currentUser.value

        val camera = CloudCameraDevice(
            id = localDeviceId,
            userId = user?.uid ?: "",
            userEmail = user?.email ?: (if (user?.isAnonymous == true) "Convidado" else ""),
            deviceName = devName,
            ipAddress = currentIp,
            port = 8080,
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            quality = _videoQuality.value.name,
            motionDetected = _isMotionDetected.value,
            streamUrl = "http://$currentIp:8080"
        )

        firebaseManager.publishCamera(camera)

        // Start heartbeat to keep device online in the cloud catalog
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10000)
                firebaseManager.updateCameraHeartbeat(localDeviceId, _isMotionDetected.value)
            }
        }
    }

    private fun setCloudCameraOffline() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        firebaseManager.setCameraOffline(localDeviceId)
    }

    fun removeCloudCamera(cameraId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            firebaseManager.removeCamera(cameraId)
        }
    }

    private fun handleRemoteCommand(command: String) {
        viewModelScope.launch(Dispatchers.Main) {
            when (command.lowercase()) {
                "flash" -> toggleFlash()
                "siren" -> toggleSiren()
                "switch" -> toggleCameraFacing()
                "record" -> toggleRecording()
            }
        }
    }

    fun toggleFlash() {
        val next = !_isFlashOn.value
        _isFlashOn.value = next
        httpStreamServer?.isFlashOn = next
    }

    fun toggleSiren() {
        val next = !_isSirenOn.value
        _isSirenOn.value = next
        httpStreamServer?.isSirenOn = next
        if (next) {
            sirenSynthesizer.startSiren(viewModelScope)
            vibrateDevice()
        } else {
            sirenSynthesizer.stopSiren()
        }
    }

    fun stopSiren() {
        _isSirenOn.value = false
        httpStreamServer?.isSirenOn = false
        sirenSynthesizer.stopSiren()
    }

    fun toggleCameraFacing() {
        _cameraFacing.value = if (_cameraFacing.value == CameraFacing.BACK) {
            CameraFacing.FRONT
        } else {
            CameraFacing.BACK
        }
    }

    fun toggleQuality() {
        _videoQuality.value = if (_videoQuality.value == VideoQuality.HD) {
            VideoQuality.SD
        } else {
            VideoQuality.HD
        }
    }

    fun toggleBlackScreen() {
        _isBlackScreen.value = !_isBlackScreen.value
    }

    fun setBlackScreen(active: Boolean) {
        _isBlackScreen.value = active
    }

    fun toggleRecording() {
        val next = !_isRecording.value
        _isRecording.value = next
        if (!next) {
            viewModelScope.launch(Dispatchers.IO) {
                surveillanceDao.insertEvent(
                    SurveillanceEventEntity(
                        title = "Gravação Manual",
                        type = "RECORDING",
                        durationSeconds = 60,
                        note = "Clipe de vigilância salvo"
                    )
                )
            }
        }
    }

    fun onMotionDetected(score: Float) {
        _motionScore.value = score
        _isMotionDetected.value = true
        httpStreamServer?.isMotionDetected = true

        val now = System.currentTimeMillis()
        if (now - lastMotionEventTime > 5000) {
            lastMotionEventTime = now
            vibrateDevice()

            viewModelScope.launch(Dispatchers.IO) {
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                surveillanceDao.insertEvent(
                    SurveillanceEventEntity(
                        title = "Alerta de Movimento - $timeStr",
                        type = "MOTION",
                        motionScore = score,
                        note = "Intensidade: ${"%.1f".format(score)}%"
                    )
                )
            }

            if (_autoSirenOnMotion.value && !_isSirenOn.value) {
                toggleSiren()
            }
        }

        // Notify Firebase cloud catalog of motion
        firebaseManager.updateCameraHeartbeat(localDeviceId, isMotion = true)

        motionResetJob?.cancel()
        motionResetJob = viewModelScope.launch {
            delay(2500)
            _isMotionDetected.value = false
            _motionScore.value = 0f
            httpStreamServer?.isMotionDetected = false
            firebaseManager.updateCameraHeartbeat(localDeviceId, isMotion = false)
        }
    }

    fun setSensitivity(value: Float) {
        _motionSensitivity.value = value
    }

    fun setAutoSirenOnMotion(enabled: Boolean) {
        _autoSirenOnMotion.value = enabled
    }

    fun startViewer(hostIp: String, port: Int = 8080, deviceName: String = "Câmera Remota") {
        _targetHostIp.value = hostIp
        _targetPort.value = port
        _targetDeviceName.value = deviceName

        viewModelScope.launch(Dispatchers.IO) {
            val deviceId = "$hostIp:$port"
            deviceDao.insertDevice(
                SavedDeviceEntity(
                    id = deviceId,
                    deviceName = deviceName,
                    ipAddress = hostIp,
                    port = port,
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
            )
        }
        _appMode.value = AppMode.VIEWER
    }

    fun deleteDevice(device: SavedDeviceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceDao.deleteDevice(device)
        }
    }

    fun deleteEvent(event: SurveillanceEventEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            surveillanceDao.deleteEvent(event)
            event.filePath?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            surveillanceDao.clearAllEvents()
        }
    }

    fun saveSnapshot(bitmap: Bitmap, source: String = "Câmera Local") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val filename = "pscam_snap_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                surveillanceDao.insertEvent(
                    SurveillanceEventEntity(
                        title = "Snapshot - $timeStr",
                        type = "SNAPSHOT",
                        filePath = file.absolutePath,
                        note = "Origem: $source"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setMotionSensitivity(value: Float) {
        _motionSensitivity.value = value.coerceIn(0.01f, 1.0f)
    }

    private fun vibrateDevice() {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(150)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        setCloudCameraOffline()
        stopStreamingServer()
        stopSiren()
    }
}
