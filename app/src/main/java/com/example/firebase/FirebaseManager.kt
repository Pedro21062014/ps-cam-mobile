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
    val isCharging: Boolean = false
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
            "isCharging" to isCharging
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): CloudCameraDevice {
            val ip = (map["ipAddress"] as? String) ?: (map["ip"] as? String) ?: ""
            val name = (map["deviceName"] as? String) ?: (map["name"] as? String) ?: "PS Cam"
            val stream = (map["streamUrl"] as? String) ?: (map["webUrl"] as? String) ?: ""
            val onlineVal = map["isOnline"] as? Boolean ?: (map["status"] == "online")

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
                isCharging = map["isCharging"] as? Boolean ?: false
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
                .setApplicationId("1:190762565052:web:83d54e500b3d29dc03ffb9")
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

        startObservingCatalog()
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

        val cameraToSave = camera.copy(
            userId = user?.uid ?: camera.userId,
            userEmail = user?.email ?: (if (user?.isAnonymous == true) "Convidado" else camera.userEmail),
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

        // 2. Also write to Firestore /users/{userId}/cameras/{id} if user logged in
        if (user != null && user.uid.isNotEmpty()) {
            firestore.collection("users").document(user.uid).collection("cameras").document(cameraId)
                .set(payload, SetOptions.merge())
        }

        // 3. Dual write to Realtime Database /cameras/{id} for direct web realtime compatibility
        try {
            realtimeDb.getReference("cameras").child(cameraId).setValue(payload)
            if (user != null && user.uid.isNotEmpty()) {
                realtimeDb.getReference("users").child(user.uid).child("cameras").child(cameraId).setValue(payload)
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

