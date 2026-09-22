package com.example.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

class MotionAnalyzer(
    var sensitivity: Float = 0.20f, // 0.05 (High) to 0.40 (Low)
    private val onMotion: (Float) -> Unit,
    private val onFrameCaptured: ((ByteArray) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private var previousGrid: IntArray? = null
    private val gridWidth = 32
    private val gridHeight = 24
    private var lastAnalysisTime = 0L
    private var lastFrameCaptureTime = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val image = imageProxy.image

        if (image == null) {
            imageProxy.close()
            return
        }

        try {
            // Compress frame for stream if needed (throttled to ~15-20 fps)
            if (onFrameCaptured != null && (currentTime - lastFrameCaptureTime >= 50)) {
                lastFrameCaptureTime = currentTime
                val jpegBytes = imageProxyToJpeg(imageProxy)
                if (jpegBytes != null) {
                    onFrameCaptured.invoke(jpegBytes)
                }
            }

            // Perform motion analysis every 100ms
            if (currentTime - lastAnalysisTime >= 100) {
                lastAnalysisTime = currentTime
                val plane = image.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val imgWidth = image.width
                val imgHeight = image.height

                val currentGrid = IntArray(gridWidth * gridHeight)
                val stepX = imgWidth / gridWidth
                val stepY = imgHeight / gridHeight

                for (gy in 0 until gridHeight) {
                    val y = (gy * stepY).coerceAtMost(imgHeight - 1)
                    val rowOffset = y * rowStride
                    for (gx in 0 until gridWidth) {
                        val x = (gx * stepX).coerceAtMost(imgWidth - 1)
                        val index = rowOffset + x * pixelStride
                        if (index < buffer.limit()) {
                            currentGrid[gy * gridWidth + gx] = buffer.get(index).toInt() and 0xFF
                        }
                    }
                }

                val prev = previousGrid
                if (prev != null && prev.size == currentGrid.size) {
                    var diffCount = 0
                    var totalDiff = 0L
                    val diffThreshold = (sensitivity * 255).toInt().coerceIn(10, 100)

                    for (i in currentGrid.indices) {
                        val diff = abs(currentGrid[i] - prev[i])
                        totalDiff += diff
                        if (diff > diffThreshold) {
                            diffCount++
                        }
                    }

                    val changedPercent = (diffCount.toFloat() / currentGrid.size) * 100f
                    if (changedPercent >= 6.0f) {
                        onMotion(changedPercent)
                    }
                }
                previousGrid = currentGrid
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            val out = ByteArrayOutputStream()
            // Quality 65 for fast smooth streaming
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 65, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }
}
