package com.example.firebase

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CloudCameraDevice(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val deviceName: String = "PS Cam",
    val ipAddress: String = "",
    val port: Int = 8080,
    val isOnline: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis(),
    val quality: String = "HD 720p",
    val motionDetected: Boolean = false,
    val streamUrl: String = "",
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val pin: String = "",
    val rawPin: String = "",
    val sessionId: String = "",
    val type: String = "webrtc"
) {
    fun toMap(): Map<String, Any> {
        val stream = if (streamUrl.isNotEmpty()) streamUrl else "http://$ipAddress:$port"
        return mapOf(
            "id" to id,
            "deviceId" to id,
            "userId" to userId,
            "userEmail" to userEmail,
            "name" to deviceName,
            "deviceName" to deviceName,
            "ip" to ipAddress,
            "ipAddress" to ipAddress,
            "port" to port,
            "status" to if (isOnline) "online" else "offline",
            "isOnline" to isOnline,
            "lastSeen" to lastSeen,
            "timestamp" to lastSeen,
            "updatedAt" to lastSeen,
            "quality" to quality,
            "motionDetected" to motionDetected,
            "streamUrl" to stream,
            "webUrl" to stream,
            "battery" to batteryLevel,
            "batteryLevel" to batteryLevel,
            "isCharging" to isCharging,
            "pin" to pin,
            "rawPin" to rawPin,
            "code" to rawPin,
            "sessionId" to sessionId,
            "type" to type
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): CloudCameraDevice {
            val ip = (map["ipAddress"] as? String) ?: (map["ip"] as? String) ?: ""
            val name = (map["deviceName"] as? String) ?: (map["name"] as? String) ?: "PS Cam"
            val stream = (map["streamUrl"] as? String) ?: (map["webUrl"] as? String) ?: ""
            val onlineVal = map["isOnline"] as? Boolean ?: (map["status"] == "online")
            val p = (map["pin"] as? String) ?: ""
            val rp = (map["rawPin"] as? String) ?: (map["code"] as? String) ?: p.replace("-", "")
            val sId = (map["sessionId"] as? String) ?: ""
            val t = (map["type"] as? String) ?: "webrtc"

            return CloudCameraDevice(
                id = id,
                userId = map["userId"] as? String ?: "",
                userEmail = map["userEmail"] as? String ?: "",
                deviceName = name,
                ipAddress = ip,
                port = (map["port"] as? Number)?.toInt() ?: 8080,
                isOnline = onlineVal,
                lastSeen = (map["lastSeen"] as? Number)?.toLong() ?: (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                quality = map["quality"] as? String ?: "HD 720p",
                motionDetected = map["motionDetected"] as? Boolean ?: false,
                streamUrl = stream,
                batteryLevel = (map["battery"] as? Number)?.toInt() ?: (map["batteryLevel"] as? Number)?.toInt() ?: 100,
                isCharging = map["isCharging"] as? Boolean ?: false,
                pin = p,
                rawPin = rp,
                sessionId = sId,
                type = t
            )
        }
    }
}

class FirebaseManager private constructor(private val context: Context) {

    private val auth: FirebaseAuth
    private val firestore: FirebaseFirestore
    private val realtimeDb: FirebaseDatabase

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _cloudCameras = MutableStateFlow<List<CloudCameraDevice>>(emptyList())
    val cloudCameras: StateFlow<List<CloudCameraDevice>> = _cloudCameras.asStateFlow()

    private var catalogListener: ListenerRegistration? = null
    private var rtdbListener: ValueEventListener? = null

    init {
        // Initialize Firebase with the provided configuration
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAaJPo4L5Xq29HO6jgX3psqxbWNZrpKriU")
                .setApplicationId("1:190762565052:android:83d54e500b3d29dc03ffb9")
                .setProjectId("ps-cam")
                .setDatabaseUrl("https://ps-cam-default-rtdb.firebaseio.com")
                .setStorageBucket("ps-cam.firebasestorage.app")
                .setGcmSenderId("190762565052")
                .build()
            FirebaseApp.initializeApp(context, options)
        } else {
            FirebaseApp.getInstance()
        }

        auth = FirebaseAuth.getInstance(app)
        firestore = FirebaseFirestore.getInstance(app)
        realtimeDb = FirebaseDatabase.getInstance(app, "https://ps-cam-default-rtdb.firebaseio.com")

        _currentUser.value = auth.currentUser
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            startObservingCatalog()
        }

        if (auth.currentUser == null) {
            autoAuthenticate()
        } else {
            startObservingCatalog()
        }
    }

    private fun autoAuthenticate() {
        auth.signInWithEmailAndPassword("pscam_guest_device@pssom.com.br", "pscam_device_pass_2026")
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Auto authenticated: ${it.user?.uid}")
                _currentUser.value = it.user
                startObservingCatalog()
            }
            .addOnFailureListener {
                auth.createUserWithEmailAndPassword("pscam_guest_device@pssom.com.br", "pscam_device_pass_2026")
                    .addOnSuccessListener { res ->
                        _currentUser.value = res.user
                        startObservingCatalog()
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirebaseManager", "Auto auth failed: ${e.message}")
                        startObservingCatalog()
                    }
            }
    }

    fun getBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            Pair(batteryPct, isCharging)
        } catch (_: Exception) {
            Pair(100, false)
        }
    }

    fun signIn(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            onResult(false, "Informe e-mail e senha")
            return
        }
        auth.signInWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener {
                _currentUser.value = it.user
                startObservingCatalog()
                onResult(true, null)
            }
            .addOnFailureListener {
                Log.e("FirebaseManager", "Sign in failed: ${it.message}")
                onResult(false, it.localizedMessage ?: "Erro ao autenticar")
            }
    }

    fun signUp(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || pass.length < 6) {
            onResult(false, "A senha deve ter pelo menos 6 caracteres")
            return
        }
        auth.createUserWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener {
                _currentUser.value = it.user
                startObservingCatalog()
                onResult(true, null)
            }
            .addOnFailureListener {
                Log.e("FirebaseManager", "Sign up failed: ${it.message}")
                onResult(false, it.localizedMessage ?: "Erro ao criar conta")
            }
    }

    fun signInAnonymously(onResult: (Boolean, String?) -> Unit) {
        auth.signInAnonymously()
            .addOnSuccessListener {
                _currentUser.value = it.user
                startObservingCatalog()
                onResult(true, null)
            }
            .addOnFailureListener {
                Log.e("FirebaseManager", "Anonymous sign-in failed: ${it.message}")
                onResult(false, it.localizedMessage ?: "Erro ao entrar como convidado")
            }
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
        startObservingCatalog()
    }

    fun startObservingCatalog() {
        catalogListener?.remove()
        val user = _currentUser.value

        // Listen on Firestore "cameras"
        val collection = firestore.collection("cameras")
        catalogListener = collection.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Log.w("FirebaseManager", "Firestore listen error: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val list = snapshots.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    CloudCameraDevice.fromMap(doc.id, data)
                }.filter { camera ->
                    if (user != null && !user.isAnonymous && camera.userId.isNotEmpty()) {
                        camera.userId == user.uid || camera.userEmail.equals(user.email, ignoreCase = true)
                    } else {
                        true
                    }
                }
                _cloudCameras.value = list
            }
        }

        // Also sync Realtime Database if Firestore is empty
        try {
            val rtdbRef = realtimeDb.getReference("cameras")
            rtdbListener?.let { rtdbRef.removeEventListener(it) }
            rtdbListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_cloudCameras.value.isEmpty() && snapshot.exists()) {
                        val items = mutableListOf<CloudCameraDevice>()
                        for (child in snapshot.children) {
                            val map = child.value as? Map<String, Any?> ?: continue
                            items.add(CloudCameraDevice.fromMap(child.key ?: "", map))
                        }
                        if (items.isNotEmpty()) {
                            _cloudCameras.value = items
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("FirebaseManager", "RTDB cancel: ${error.message}")
                }
            }
            rtdbRef.addValueEventListener(rtdbListener as ValueEventListener)
        } catch (e: Exception) {
            Log.w("FirebaseManager", "RTDB listen error: ${e.message}")
        }
    }

    fun publishCamera(camera: CloudCameraDevice, onComplete: ((Boolean) -> Unit)? = null) {
        val user = _currentUser.value
        val (battery, isCharging) = getBatteryInfo()

        val effectiveUserId = when {
            user != null && user.uid.isNotEmpty() -> user.uid
            camera.userId.isNotEmpty() -> camera.userId
            else -> "user_pssom"
        }
        val effectiveEmail = when {
            user != null && !user.isAnonymous && !user.email.isNullOrEmpty() -> user.email!!
            camera.userEmail.isNotEmpty() -> camera.userEmail
            else -> "pssom.com.br@gmail.com"
        }

        val cameraToSave = camera.copy(
            userId = effectiveUserId,
            userEmail = effectiveEmail,
            batteryLevel = battery,
            isCharging = isCharging,
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )

        val cameraId = if (cameraToSave.id.isNotEmpty()) cameraToSave.id else firestore.collection("cameras").document().id
        val finalCamera = cameraToSave.copy(id = cameraId)
        val payload = finalCamera.toMap()

        // 1. Write to Firestore /cameras/{id}
        firestore.collection("cameras").document(cameraId)
            .set(payload, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FirebaseManager", "Camera published to Firestore: $cameraId")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseManager", "Firestore publish error: ${e.message}")
                onComplete?.invoke(false)
            }

        // 2. Also write to Firestore /users/{userId}/cameras/{id}
        if (effectiveUserId.isNotEmpty()) {
            firestore.collection("users").document(effectiveUserId).collection("cameras").document(cameraId)
                .set(payload, SetOptions.merge())
        }
        // Write to Firestore /devices/{id}
        firestore.collection("devices").document(cameraId)
            .set(payload, SetOptions.merge())

        // 3. Dual write to Realtime Database /cameras/{id}, /users/{userId}/cameras/{id} and /devices/{id}
        try {
            realtimeDb.getReference("cameras").child(cameraId).setValue(payload)
            if (effectiveUserId.isNotEmpty()) {
                realtimeDb.getReference("users").child(effectiveUserId).child("cameras").child(cameraId).setValue(payload)
            }
            realtimeDb.getReference("devices").child(cameraId).setValue(payload)
            if (cameraToSave.pin.isNotEmpty()) {
                realtimeDb.getReference("rooms").child(cameraToSave.pin).child("camera").setValue(payload)
            }
            if (cameraToSave.rawPin.isNotEmpty() && cameraToSave.rawPin != cameraToSave.pin) {
                realtimeDb.getReference("rooms").child(cameraToSave.rawPin).child("camera").setValue(payload)
            }
        } catch (e: Exception) {
            Log.w("FirebaseManager", "RTDB write error: ${e.message}")
        }
    }

    fun updateCameraHeartbeat(cameraId: String, isMotion: Boolean = false) {
        if (cameraId.isEmpty()) return
        val (battery, isCharging) = getBatteryInfo()
        val timestamp = System.currentTimeMillis()

        val updates = mutableMapOf<String, Any>(
            "lastSeen" to timestamp,
            "timestamp" to timestamp,
            "updatedAt" to timestamp,
            "isOnline" to true,
            "status" to "online",
            "motionDetected" to isMotion,
            "battery" to battery,
            "batteryLevel" to battery,
            "isCharging" to isCharging
        )

        firestore.collection("cameras").document(cameraId)
            .set(updates, SetOptions.merge())

        val user = _currentUser.value
        if (user != null && user.uid.isNotEmpty()) {
            firestore.collection("users").document(user.uid).collection("cameras").document(cameraId)
                .set(updates, SetOptions.merge())
        }

        try {
            realtimeDb.getReference("cameras").child(cameraId).updateChildren(updates)
            if (user != null && user.uid.isNotEmpty()) {
                realtimeDb.getReference("users").child(user.uid).child("cameras").child(cameraId).updateChildren(updates)
            }
        } catch (_: Exception) {}
    }

    fun setCameraOffline(cameraId: String) {
        if (cameraId.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        val updates = mapOf<String, Any>(
            "isOnline" to false,
            "status" to "offline",
            "lastSeen" to timestamp,
            "updatedAt" to timestamp,
            "motionDetected" to false
        )

        firestore.collection("cameras").document(cameraId)
            .set(updates, SetOptions.merge())

        val user = _currentUser.value
        if (user != null && user.uid.isNotEmpty()) {
            firestore.collection("users").document(user.uid).collection("cameras").document(cameraId)
                .set(updates, SetOptions.merge())
        }

        try {
            realtimeDb.getReference("cameras").child(cameraId).updateChildren(updates)
        } catch (_: Exception) {}
    }

    fun removeCamera(cameraId: String) {
        if (cameraId.isEmpty()) return
        firestore.collection("cameras").document(cameraId).delete()
        val user = _currentUser.value
        if (user != null && user.uid.isNotEmpty()) {
            firestore.collection("users").document(user.uid).collection("cameras").document(cameraId).delete()
        }
        try {
            realtimeDb.getReference("cameras").child(cameraId).removeValue()
        } catch (_: Exception) {}
    }

    // P2P / Cloud Internet Live Frame Broadcast
    fun publishLiveFrame(cameraId: String, pin: String = "", rawPin: String = "", base64Jpeg: String) {
        if (cameraId.isEmpty()) return
        try {
            val frameData = if (base64Jpeg.startsWith("data:image")) base64Jpeg else "data:image/jpeg;base64,$base64Jpeg"
            val timestamp = System.currentTimeMillis()
            val payload = mapOf(
                "frame" to frameData,
                "data" to frameData,
                "rawFrame" to base64Jpeg,
                "timestamp" to timestamp,
                "t" to timestamp,
                "updatedAt" to timestamp,
                "deviceId" to cameraId,
                "pin" to pin,
                "rawPin" to rawPin
            )

            val streamRef = realtimeDb.getReference("live_streams").child(cameraId)
            streamRef.setValue(payload)

            if (rawPin.isNotEmpty()) {
                realtimeDb.getReference("live_streams").child(rawPin).setValue(payload)
            }
            if (pin.isNotEmpty() && pin != rawPin) {
                realtimeDb.getReference("live_streams").child(pin).setValue(payload)
            }
        } catch (_: Exception) {}
    }

    // Observe Live Stream over Internet (P2P / Cloud Stream)
    fun observeLiveStream(cameraId: String, onFrameReceived: (String) -> Unit): ValueEventListener? {
        if (cameraId.isEmpty()) return null
        return try {
            val streamRef = realtimeDb.getReference("live_streams").child(cameraId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val frameStr = snapshot.child("frame").value as? String
                    if (!frameStr.isNullOrEmpty()) {
                        onFrameReceived(frameStr)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            streamRef.addValueEventListener(listener)
            listener
        } catch (e: Exception) {
            null
        }
    }

    fun removeLiveStreamListener(cameraId: String, listener: ValueEventListener) {
        try {
            realtimeDb.getReference("live_streams").child(cameraId).removeEventListener(listener)
        } catch (_: Exception) {}
    }

    // Send Remote Command over Internet (Flash, Siren, Switch, Record)
    fun sendRemoteCommand(cameraId: String, command: String) {
        if (cameraId.isEmpty()) return
        try {
            val cmdRef = realtimeDb.getReference("commands").child(cameraId)
            cmdRef.setValue(
                mapOf(
                    "command" to command,
                    "t" to System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}
    }

    // Camera Host: Listen for remote commands from Internet (supports FLASH_TOGGLE, SIREN_TOGGLE, CAMERA_SWITCH, etc.)
    fun observeRemoteCommands(cameraId: String, onCommandReceived: (String) -> Unit): ValueEventListener? {
        if (cameraId.isEmpty()) return null
        return try {
            val cmdRef = realtimeDb.getReference("commands").child(cameraId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val rawVal = snapshot.value
                    if (rawVal is String && rawVal.isNotEmpty()) {
                        onCommandReceived(rawVal)
                        return
                    }

                    val cmd = snapshot.child("command").value as? String
                        ?: snapshot.child("action").value as? String
                        ?: snapshot.child("cmd").value as? String

                    if (!cmd.isNullOrEmpty()) {
                        onCommandReceived(cmd)
                        return
                    }

                    // Direct boolean triggers
                    val flash = snapshot.child("flash").value as? Boolean
                    val siren = snapshot.child("siren").value as? Boolean
                    val cameraSwitch = snapshot.child("camera").value as? String
                    if (flash != null) onCommandReceived(if (flash) "FLASH_ON" else "FLASH_OFF")
                    if (siren != null) onCommandReceived(if (siren) "SIREN_ON" else "SIREN_OFF")
                    if (!cameraSwitch.isNullOrEmpty()) onCommandReceived("CAMERA_SWITCH")
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            cmdRef.addValueEventListener(listener)
            listener
        } catch (e: Exception) {
            null
        }
    }

    // WebRTC Session Synchronization (/sessions/{sessionId})
    data class WebRtcSessionSubscription(
        val firestoreRegistration: ListenerRegistration?,
        val rtdbListener: ValueEventListener?,
        val sessionId: String,
        private val rtdbRef: com.google.firebase.database.DatabaseReference?
    ) {
        fun remove() {
            try { firestoreRegistration?.remove() } catch (_: Exception) {}
            try { rtdbListener?.let { rtdbRef?.removeEventListener(it) } } catch (_: Exception) {}
        }
    }

    fun initWebRtcSession(
        sessionId: String,
        hostId: String,
        pin: String,
        initialCommands: Map<String, Any>
    ) {
        if (sessionId.isBlank()) return
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val payload = mapOf(
            "hostId" to hostId,
            "deviceId" to hostId,
            "peerId" to sessionId,
            "pin" to pin,
            "status" to "waiting",
            "type" to "webrtc",
            "commands" to initialCommands,
            "updatedAt" to isoDate,
            "timestamp" to System.currentTimeMillis()
        )

        try {
            firestore.collection("sessions").document(sessionId).set(payload, SetOptions.merge())
            realtimeDb.getReference("sessions").child(sessionId).setValue(payload)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error init session: ${e.message}")
        }
    }

    fun observeSession(
        sessionId: String,
        onSessionUpdate: (peerId: String?, commands: Map<String, Any?>) -> Unit
    ): WebRtcSessionSubscription {
        var rtdbListener: ValueEventListener? = null
        var fsListener: ListenerRegistration? = null
        val rtdbRef = realtimeDb.getReference("sessions").child(sessionId)

        try {
            fsListener = firestore.collection("sessions").document(sessionId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("FirebaseManager", "Session listen error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val peerId = snapshot.getString("peerId")
                        @Suppress("UNCHECKED_CAST")
                        val commands = snapshot.get("commands") as? Map<String, Any?> ?: emptyMap()
                        onSessionUpdate(peerId, commands)
                    }
                }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Firestore session observe error: ${e.message}")
        }

        try {
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val peerId = snapshot.child("peerId").value as? String
                        @Suppress("UNCHECKED_CAST")
                        val cmdObj = snapshot.child("commands").value as? Map<String, Any?> ?: emptyMap()
                        onSessionUpdate(peerId, cmdObj)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            rtdbRef.addValueEventListener(l)
            rtdbListener = l
        } catch (_: Exception) {}

        return WebRtcSessionSubscription(fsListener, rtdbListener, sessionId, rtdbRef)
    }

    // Register 6-Digit Pairing PIN for Web & Mobile instant pairing (both formatted '489-123' and raw '489123')
    fun registerPairingPin(
        pin: String,
        cameraId: String,
        deviceName: String,
        ipAddress: String,
        port: Int,
        sessionId: String = "",
        type: String = "webrtc"
    ) {
        val cleanPin = pin.replace("-", "").trim()
        if (cleanPin.isEmpty() || cameraId.isEmpty()) return

        val formattedPin = if (cleanPin.length == 6 && !pin.contains("-")) {
            "${cleanPin.substring(0, 3)}-${cleanPin.substring(3)}"
        } else pin

        val effectiveSessionId = sessionId.ifEmpty { "pscam_$cleanPin" }
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val (battery, isCharging) = getBatteryInfo()
        val user = _currentUser.value
        val userEmail = if (user != null && !user.isAnonymous && !user.email.isNullOrEmpty() && !user.email!!.contains("guest")) {
            user.email!!
        } else {
            "pssom.com.br@gmail.com"
        }
        val userId = user?.uid ?: "user_pssom"

        val timestampNow = System.currentTimeMillis()
        val payload = mapOf(
            "id" to cameraId,
            "deviceId" to cameraId,
            "name" to deviceName,
            "deviceName" to deviceName,
            "ipAddress" to ipAddress,
            "ip" to ipAddress,
            "port" to port,
            "streamUrl" to "http://$ipAddress:$port/video",
            "snapshotUrl" to "http://$ipAddress:$port/snapshot",
            "pin" to formattedPin,
            "rawPin" to cleanPin,
            "code" to cleanPin,
            "type" to type,
            "sessionId" to effectiveSessionId,
            "peerId" to effectiveSessionId,
            "status" to "online",
            "isOnline" to true,
            "battery" to battery,
            "batteryLevel" to battery,
            "batteryCharging" to isCharging,
            "isCharging" to isCharging,
            "userId" to userId,
            "userEmail" to userEmail,
            "updatedAt" to timestampNow,
            "updatedAtIso" to isoDate,
            "timestamp" to timestampNow
        )

        try {
            // Register both raw PIN ('489123') and formatted PIN ('489-123') in Firestore /pins
            firestore.collection("pins").document(cleanPin).set(payload, SetOptions.merge())
                .addOnSuccessListener { Log.d("FirebaseManager", "PIN $cleanPin registered in Firestore /pins") }
                .addOnFailureListener { e -> Log.e("FirebaseManager", "Error /pins/$cleanPin: ${e.message}") }

            firestore.collection("pins").document(formattedPin).set(payload, SetOptions.merge())

            // Also register in Firestore /cameras and /devices so any query by PIN or ID succeeds
            firestore.collection("cameras").document(cleanPin).set(payload, SetOptions.merge())
            firestore.collection("cameras").document(formattedPin).set(payload, SetOptions.merge())
            firestore.collection("cameras").document(cameraId).set(payload, SetOptions.merge())
            firestore.collection("devices").document(cleanPin).set(payload, SetOptions.merge())
            firestore.collection("devices").document(cameraId).set(payload, SetOptions.merge())

            // Also write to user cameras path
            firestore.collection("users").document(userId).collection("cameras").document(cameraId).set(payload, SetOptions.merge())

            // Realtime Database catalog writes (/pins, /cameras, /devices)
            realtimeDb.getReference("pins").child(cleanPin).setValue(payload)
            realtimeDb.getReference("pins").child(formattedPin).setValue(payload)
            realtimeDb.getReference("cameras").child(cleanPin).setValue(payload)
            realtimeDb.getReference("cameras").child(formattedPin).setValue(payload)
            realtimeDb.getReference("cameras").child(cameraId).setValue(payload)
            realtimeDb.getReference("devices").child(cleanPin).setValue(payload)
            realtimeDb.getReference("devices").child(cameraId).setValue(payload)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error registering PIN: ${e.message}")
        }
    }

    // Resolve 6-Digit Pairing PIN or Camera ID from Web or Mobile Viewer
    fun resolveCameraByPin(code: String, onResult: (CloudCameraDevice?) -> Unit) {
        val cleanCode = code.replace("-", "").trim()
        if (cleanCode.isEmpty()) {
            onResult(null)
            return
        }

        // 1. Check if it matches a PIN in Realtime Database
        realtimeDb.getReference("pins").child(cleanCode).get().addOnSuccessListener { snapshot ->
            val devId = snapshot.child("deviceId").value as? String
            val name = snapshot.child("deviceName").value as? String ?: "Câmera Pareada"
            val ip = snapshot.child("ipAddress").value as? String ?: ""
            val port = (snapshot.child("port").value as? Long)?.toInt() ?: 8080

            if (!devId.isNullOrEmpty()) {
                onResult(
                    CloudCameraDevice(
                        id = devId,
                        deviceName = name,
                        ipAddress = ip,
                        port = port,
                        isOnline = true
                    )
                )
            } else {
                // 2. Check if the code is directly a Camera ID in cameras collection
                realtimeDb.getReference("cameras").child(cleanCode).get().addOnSuccessListener { camSnap ->
                    if (camSnap.exists()) {
                        val cName = camSnap.child("deviceName").value as? String ?: "Câmera Remota"
                        val cIp = camSnap.child("ipAddress").value as? String ?: ""
                        val cPort = (camSnap.child("port").value as? Long)?.toInt() ?: 8080
                        onResult(
                            CloudCameraDevice(
                                id = cleanCode,
                                deviceName = cName,
                                ipAddress = cIp,
                                port = cPort,
                                isOnline = true
                            )
                        )
                    } else {
                        // 3. Check Firestore pins
                        firestore.collection("pins").document(cleanCode).get().addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                val fId = doc.getString("deviceId") ?: cleanCode
                                val fName = doc.getString("deviceName") ?: "Câmera Pareada"
                                val fIp = doc.getString("ipAddress") ?: ""
                                val fPort = doc.getLong("port")?.toInt() ?: 8080
                                onResult(
                                    CloudCameraDevice(
                                        id = fId,
                                        deviceName = fName,
                                        ipAddress = fIp,
                                        port = fPort,
                                        isOnline = true
                                    )
                                )
                            } else {
                                onResult(null)
                            }
                        }.addOnFailureListener {
                            onResult(null)
                        }
                    }
                }.addOnFailureListener {
                    onResult(null)
                }
            }
        }.addOnFailureListener {
            onResult(null)
        }
    }

    companion object {
        @Volatile
        private var instance: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

