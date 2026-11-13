package com.dontstudyy.carbeat.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 박자 보정용 메트로놈. 한 박 길이의 버퍼(맨 앞에 짧은 클릭)를 반복 재생한다.
 * [beat0HeardNanos]는 첫 박이 실제로 들린 시각(elapsedRealtimeNanos)으로, 오디오 타임스탬프로 구한다.
 * 이렇게 하면 출력 지연이 보정값에 포함되어, 곡을 들으며 칠 때의 지연과 같은 기준이 된다.
 */
class Metronome(private val bpm: Double) {
    private val sampleRate = 44100
    private val beatFrames = (60.0 / bpm * sampleRate).toInt()
    private var track: AudioTrack? = null
    private var playCallNanos = 0L

    val beatSec: Double get() = 60.0 / bpm

    private fun buildBuffer(): ShortArray {
        val buf = ShortArray(beatFrames)
        val clickFrames = (0.04 * sampleRate).toInt()
        for (i in 0 until clickFrames) {
            val env = exp(-i / (0.008 * sampleRate))
            buf[i] = (sin(2 * PI * 1800.0 * i / sampleRate) * env * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
        return buf
    }

    fun start() {
        stop()
        val buf = buildBuffer()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(buf, 0, buf.size)
        t.setLoopPoints(0, buf.size, -1)
        playCallNanos = SystemClock.elapsedRealtimeNanos()
        t.play()
        track = t
    }

    /** 첫 박이 들린 시각. 타임스탬프를 못 얻으면 재생 호출 시각으로 대신한다(오차 있음). */
    fun beat0HeardNanos(): Long {
        val t = track ?: return playCallNanos
        val ts = AudioTimestamp()
        if (!t.getTimestamp(ts)) return playCallNanos
        // 프레임 0이 들린 시각 = 기준 시각 - 그 프레임까지 걸린 시간
        return ts.nanoTime - (ts.framePosition * 1_000_000_000L / sampleRate)
    }

    val isTimestampAvailable: Boolean
        get() = track?.let { AudioTimestamp().let { ts -> it.getTimestamp(ts) } } ?: false

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }
}
