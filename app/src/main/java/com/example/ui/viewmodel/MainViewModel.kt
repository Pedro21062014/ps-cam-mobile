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

import android.util.Base64
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.example.webrtc.WebRtcManager
import com.google.firebase.database.ValueEventListener

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

    private val _targetDeviceId = MutableStateFlow("")
    val targetDeviceId: StateFlow<String> = _targetDeviceId.asStateFlow()

    private val sirenSynthesizer = SirenSynthesizer()
    var httpStreamServer: HttpStreamServer? = null
        private set

    private var motionResetJob: Job? = null
    private var heartbeatJob: Job? = null
    private var remoteCommandListener: ValueEventListener? = null
    private var lastMotionEventTime = 0L
    private var lastCloudFrameTime = 0L

    val localDeviceId: String by lazy {
        val sanitizedModel = Build.MODEL.replace("[^a-zA-Z0-9]".toRegex(), "_").lowercase()
        "cam_${sanitizedModel}_8080"
    }

    private val _pairingPin = MutableStateFlow(generateRandomPin())
    val pairingPin: StateFlow<String> = _pairingPin.asStateFlow()

    val currentPin: String
        get() = _pairingPin.value

    val rawPin: String
        get() = _pairingPin.value.replace("-", "")

    val sessionId: String
        get() = "pscam_$rawPin"

    fun generateNewPin(): String {
        val newPin = generateRandomPin()
        _pairingPin.value = newPin
        httpStreamServer?.pairingPin = newPin
        if (_appMode.value == AppMode.CAMERA_HOST) {
            publishToCloudCatalog()
        }
        return newPin
    }

    private fun generateRandomPin(): String {
        val num = (100000..999999).random()
        val s = num.toString()
        return "${s.substring(0, 3)}-${s.substring(3)}"
    }

    var webRtcManager: WebRtcManager? = null
        private set
    private var sessionSubscription: FirebaseManager.WebRtcSessionSubscription? = null

    init {
        refreshLocalIp()

        // Auto-save discovered cameras to Room DB so other device automatically identifies and saves them
        viewModelScope.launch(Dispatchers.IO) {
            cloudCameras.collect { list ->
                list.forEach { cam ->
                    if (cam.id.isNotEmpty() && cam.id != localDeviceId) {
                        deviceDao.insertDevice(
                            SavedDeviceEntity(
                                id = cam.id,
                                deviceName = cam.deviceName,
                                ipAddress = if (cam.ipAddress.isNotEmpty()) cam.ipAddress else cam.id,
                                port = cam.port,
                                isOnline = cam.isOnline,
                                lastSeen = cam.lastSeen
                            )
                        )
                    }
                }
            }
        }
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
            generateNewPin()
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
        val devName = "PS Cam - ${Build.MODEL.ifEmpty { "Dispositivo" }}"
        val currentIp = _localIp.value
        val (battery, _) = firebaseManager.getBatteryInfo()

        if (httpStreamServer == null) {
            httpStreamServer = HttpStreamServer(
                port = 8080,
                deviceId = localDeviceId,
                deviceName = devName,
                pairingPin = currentPin,
                ipAddress = currentIp
            ) { cmd, _ ->
                handleRemoteCommand(cmd)
            }.apply {
                isFlashOn = _isFlashOn.value
                isSirenOn = _isSirenOn.value
                isMotionDetected = _isMotionDetected.value
                batteryLevel = battery
                start(viewModelScope)
            }
        } else {
            httpStreamServer?.deviceId = localDeviceId
            httpStreamServer?.deviceName = devName
            httpStreamServer?.pairingPin = currentPin
            httpStreamServer?.ipAddress = currentIp
            httpStreamServer?.batteryLevel = battery
        }
    }

    fun stopStreamingServer() {
        httpStreamServer?.stop()
        httpStreamServer = null
        webRtcManager?.destroy()
        webRtcManager = null
        sessionSubscription?.remove()
        sessionSubscription = null
    }

    private fun publishToCloudCatalog() {
        val currentIp = _localIp.value
        val devName = "PS Cam - ${Build.MODEL.ifEmpty { "Dispositivo" }}"
        val user = currentUser.value
        val (battery, isCharging) = firebaseManager.getBatteryInfo()
        val userEmail = if (user != null && !user.isAnonymous && !user.email.isNullOrEmpty() && !user.email!!.contains("guest")) {
            user.email!!
        } else {
            "pssom.com.br@gmail.com"
        }

        val camera = CloudCameraDevice(
            id = localDeviceId,
            userId = user?.uid ?: "user_pssom",
            userEmail = userEmail,
            deviceName = devName,
            ipAddress = currentIp,
            port = 8080,
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            quality = _videoQuality.value.name,
            motionDetected = _isMotionDetected.value,
            streamUrl = "http://$currentIp:8080/video",
            batteryLevel = battery,
            isCharging = isCharging,
            pin = currentPin,
            rawPin = rawPin,
            sessionId = sessionId,
            type = "webrtc"
        )

        // 1. Publish in Camera catalog
        firebaseManager.publishCamera(camera)

        // 2. Register 6-Digit Pairing PIN in both Firestore & Realtime DB (/pins/{pin})
        firebaseManager.registerPairingPin(
            pin = currentPin,
            cameraId = localDeviceId,
            deviceName = devName,
            ipAddress = currentIp,
            port = 8080,
            sessionId = sessionId,
            type = "webrtc"
        )

        // 3. Initialize WebRTC Session in Firestore & Realtime DB (/sessions/{sessionId})
        val initialCommands = mapOf(
            "flash" to _isFlashOn.value,
            "siren" to _isSirenOn.value,
            "camera" to if (_cameraFacing.value == CameraFacing.BACK) "environment" else "user",
            "quality" to _videoQuality.value.name
        )
        firebaseManager.initWebRtcSession(
            sessionId = sessionId,
            hostId = localDeviceId,
            pin = currentPin,
            initialCommands = initialCommands
        )

        // 4. Start WebRTC Transmitter (PeerJS over STUN)
        if (webRtcManager == null) {
            webRtcManager = WebRtcManager(
                context = getApplication(),
                onCommandReceived = { cmd, valStr ->
                    handleRemoteCommand(cmd, valStr)
                }
            )
        }
        webRtcManager?.start(
            sessionId = currentPin,
            initialFacing = if (_cameraFacing.value == CameraFacing.BACK) "environment" else "user",
            initialQuality = _videoQuality.value.name
        )

        // 5. Observe WebRTC Session in Firestore (/sessions/{sessionId})
        sessionSubscription?.remove()
        sessionSubscription = firebaseManager.observeSession(sessionId) { peerId, commands ->
            viewModelScope.launch(Dispatchers.Main) {
                // If a web monitor connected with its peerId, initiate or confirm call
                if (!peerId.isNullOrEmpty() && peerId != sessionId && peerId != localDeviceId) {
                    webRtcManager?.callMonitor(peerId)
                }

                // Handle commands from /sessions/{sessionId}
                commands["flash"]?.let { f ->
                    val shouldFlash = when (f) {
                        is Boolean -> f
                        is Number -> f.toInt() != 0
                        is String -> f.equals("true", ignoreCase = true) || f == "1"
                        else -> false
                    }
                    setFlash(shouldFlash)
                }

                commands["siren"]?.let { s ->
                    val shouldSiren = when (s) {
                        is Boolean -> s
                        is Number -> s.toInt() != 0
                        is String -> s.equals("true", ignoreCase = true) || s == "1"
                        else -> false
                    }
                    setSiren(shouldSiren)
                }

                commands["camera"]?.let { c ->
                    val str = c.toString().lowercase()
                    if (str == "user" || str == "front") {
                        setCameraFacing(CameraFacing.FRONT)
                    } else if (str == "environment" || str == "back") {
                        setCameraFacing(CameraFacing.BACK)
                    }
                }

                commands["quality"]?.let { q ->
                    val str = q.toString().uppercase()
                    if (str.contains("HD")) {
                        setVideoQuality(VideoQuality.HD)
                    } else if (str.contains("SD")) {
                        setVideoQuality(VideoQuality.SD)
                    }
                }
            }
        }

        // 6. Listen for remote commands in Realtime DB (/commands/{deviceId})
        remoteCommandListener?.let { firebaseManager.removeLiveStreamListener(localDeviceId, it) }
        remoteCommandListener = firebaseManager.observeRemoteCommands(localDeviceId) { cmd ->
            handleRemoteCommand(cmd)
        }

        // 7. Start heartbeat to keep device online in cloud catalog
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
        remoteCommandListener?.let { firebaseManager.removeLiveStreamListener(localDeviceId, it) }
        remoteCommandListener = null
        sessionSubscription?.remove()
        sessionSubscription = null
        webRtcManager?.destroy()
        webRtcManager = null
        firebaseManager.setCameraOffline(localDeviceId)
    }

    fun updateLiveFrame(jpegBytes: ByteArray) {
        httpStreamServer?.updateFrame(jpegBytes)

        // Broadcast to Cloud / Firebase Realtime DB at ~10 FPS (~100ms) for Web Dashboard
        val now = System.currentTimeMillis()
        if (now - lastCloudFrameTime > 100) { // 10 FPS
            lastCloudFrameTime = now
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            firebaseManager.publishLiveFrame(
                cameraId = localDeviceId,
                pin = currentPin,
                rawPin = rawPin,
                base64Jpeg = base64
            )
        }
    }

    fun removeCloudCamera(cameraId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            firebaseManager.removeCamera(cameraId)
        }
    }

    private fun handleRemoteCommand(command: String, valStr: String = "") {
        viewModelScope.launch(Dispatchers.Main) {
            val normalized = command.trim().uppercase()
            when {
                normalized == "FLASH_TOGGLE" || normalized == "FLASH" -> toggleFlash()
                normalized == "FLASH_ON" -> setFlash(true)
                normalized == "FLASH_OFF" -> setFlash(false)
                normalized == "SIREN_TOGGLE" || normalized == "SIREN" -> toggleSiren()
                normalized == "SIREN_ON" -> setSiren(true)
                normalized == "SIREN_OFF" -> setSiren(false)
                normalized == "CAMERA_SWITCH" || normalized == "SWITCH" -> toggleCameraFacing()
                normalized == "CAMERA_FRONT" || normalized == "FRONT" -> setCameraFacing(CameraFacing.FRONT)
                normalized == "CAMERA_BACK" || normalized == "BACK" -> setCameraFacing(CameraFacing.BACK)
                normalized == "QUALITY_HD" || normalized == "HD" -> setVideoQuality(VideoQuality.HD)
                normalized == "QUALITY_SD" || normalized == "SD" -> setVideoQuality(VideoQuality.SD)
                normalized == "RECORD" -> toggleRecording()
            }
        }
    }

    fun setFlash(on: Boolean) {
        _isFlashOn.value = on
        httpStreamServer?.isFlashOn = on
        try {
            val cameraManager = getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (cameraId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, on)
            }
        } catch (_: Exception) {}
    }

    fun setSiren(on: Boolean) {
        _isSirenOn.value = on
        httpStreamServer?.isSirenOn = on
        if (on) {
            sirenSynthesizer.startSiren(viewModelScope)
            vibrateDevice()
        } else {
            sirenSynthesizer.stopSiren()
        }
    }

    fun setCameraFacing(facing: CameraFacing) {
        _cameraFacing.value = facing
        webRtcManager?.switchCamera(if (facing == CameraFacing.BACK) "environment" else "user")
    }

    fun setVideoQuality(quality: VideoQuality) {
        _videoQuality.value = quality
        webRtcManager?.setQuality(quality.name)
    }

    fun toggleFlash() {
        setFlash(!_isFlashOn.value)
    }

    fun toggleSiren() {
        setSiren(!_isSirenOn.value)
    }

    fun stopSiren() {
        setSiren(false)
    }

    fun toggleCameraFacing() {
        val next = if (_cameraFacing.value == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
        setCameraFacing(next)
    }

    fun toggleQuality() {
        val next = if (_videoQuality.value == VideoQuality.HD) VideoQuality.SD else VideoQuality.HD
        setVideoQuality(next)
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

    fun startViewer(
        hostIp: String,
        port: Int = 8080,
        deviceName: String = "Câmera Remota",
        deviceId: String = ""
    ) {
        _targetHostIp.value = hostIp
        _targetPort.value = port
        _targetDeviceName.value = deviceName
        _targetDeviceId.value = if (deviceId.isNotEmpty()) deviceId else {
            // Check if hostIp is already a camera ID
            if (hostIp.startsWith("cam_")) hostIp else ""
        }

        viewModelScope.launch(Dispatchers.IO) {
            val dbId = if (deviceId.isNotEmpty()) deviceId else "$hostIp:$port"
            deviceDao.insertDevice(
                SavedDeviceEntity(
                    id = dbId,
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

    fun connectByCode(input: String, onComplete: (Boolean, String) -> Unit) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "Código não pode ser vazio")
            return
        }

        // 1. If it looks like an IP or URL
        if (trimmed.contains(".") && !trimmed.startsWith("cam_")) {
            val cleanIp = trimmed.replace("http://", "").replace("https://", "")
            val parts = cleanIp.split(":")
            val ip = parts[0]
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 8080 else 8080
            startViewer(hostIp = ip, port = port, deviceName = "Câmera $ip")
            onComplete(true, "Conectando ao IP $ip...")
            return
        }

        // 2. Resolve via 6-digit PIN or Camera ID
        firebaseManager.resolveCameraByPin(trimmed) { found ->
            if (found != null) {
                startViewer(
                    hostIp = found.ipAddress.ifEmpty { found.id },
                    port = found.port,
                    deviceName = found.deviceName,
                    deviceId = found.id
                )
                onComplete(true, "Câmera ${found.deviceName} encontrada!")
            } else {
                // If it's a device id directly
                if (trimmed.startsWith("cam_")) {
                    startViewer(
                        hostIp = trimmed,
                        port = 8080,
                        deviceName = "Câmera Nuvem",
                        deviceId = trimmed
                    )
                    onComplete(true, "Conectando à câmera $trimmed...")
                } else {
                    onComplete(false, "Código ou PIN não encontrado. Verifique se a câmera transmissora está ativa.")
                }
            }
        }
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

    fun signOut() {
        firebaseManager.signOut()
    }

    fun clearAllSavedDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            deviceDao.clearAllDevices()
        }
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
