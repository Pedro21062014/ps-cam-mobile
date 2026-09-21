package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class SirenSynthesizer {
    private var audioTrack: AudioTrack? = null
    private var sirenJob: Job? = null
    private var isPlaying = false

    fun startSiren(scope: CoroutineScope) {
        if (isPlaying) return
        isPlaying = true

        sirenJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            val buffer = ShortArray(1024)
            var phase = 0.0
            var time = 0.0

            try {
                while (isActive && isPlaying) {
                    // Modulate frequency between 500Hz and 1300Hz like a security siren
                    val sweepCycle = 0.6 // seconds per sweep
                    val progress = (time % sweepCycle) / sweepCycle
                    val frequency = if (progress < 0.5) {
                        500.0 + (1300.0 - 500.0) * (progress * 2.0)
                    } else {
                        1300.0 - (1300.0 - 500.0) * ((progress - 0.5) * 2.0)
                    }

                    for (i in buffer.indices) {
                        val sample = sin(phase) * 0.85 * Short.MAX_VALUE
                        buffer[i] = sample.toInt().toShort()
                        phase += 2.0 * Math.PI * frequency / sampleRate
                        if (phase > 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI
                        }
                        time += 1.0 / sampleRate
                    }

                    audioTrack?.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopInternal()
            }
        }
    }

    fun stopSiren() {
        isPlaying = false
        sirenJob?.cancel()
        sirenJob = null
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
