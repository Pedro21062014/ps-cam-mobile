package com.example.model

enum class AppMode {
    DASHBOARD,
    CAMERA_HOST,
    VIEWER,
    SCANNER,
    TIMELINE
}

enum class VideoQuality {
    SD,
    HD
}

enum class CameraFacing {
    BACK,
    FRONT
}

data class CameraCommand(
    val flashlight: Boolean = false,
    val playSound: Boolean = false,
    val recording: Boolean = false,
    val facing: CameraFacing = CameraFacing.BACK,
    val quality: VideoQuality = VideoQuality.HD
)

data class MotionEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val score: Float = 0f,
    val description: String = "Movimento Detectado"
)
