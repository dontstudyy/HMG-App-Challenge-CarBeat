package com.dontstudyy.carbeat.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dontstudyy.carbeat.game.Song

/**
 * Media3 ExoPlayer로 assets의 곡을 재생한다. 판정 기준 시각은 [positionSec](재생 위치)이다.
 * 반드시 메인 스레드에서 만들고 사용한다.
 */
class SongPlayer(ctx: Context, private val song: Song) {
    private val player: ExoPlayer = ExoPlayer.Builder(ctx).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }

    fun prepare() {
        player.setMediaItem(MediaItem.fromUri("asset:///songs/${song.audio}"))
        player.prepare()
    }

    fun play() = player.play()

    fun pause() = player.pause()

    val positionSec: Double get() = player.currentPosition / 1000.0

    val isPlaying: Boolean get() = player.isPlaying

    val ended: Boolean get() = player.playbackState == Player.STATE_ENDED

    /** 파일에서 읽은 실제 길이(초). 아직 모르면 null */
    val durationSec: Double? get() = player.duration.takeIf { it != C.TIME_UNSET }?.let { it / 1000.0 }

    fun release() = player.release()
}
