package com.listenbuddy.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class AudioPlayer(
    sampleRate: Int,
    channelCount: Int
) {

    private val audioTrack: AudioTrack

    init {
        val channelConfig = if (channelCount == 2)
            AudioFormat.CHANNEL_OUT_STEREO
        else
            AudioFormat.CHANNEL_OUT_MONO

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build(),
            minBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack.play()
    }

    fun play(pcm: ByteArray) {
        audioTrack.write(pcm, 0, pcm.size)
    }

    fun release() {
        audioTrack.stop()
        audioTrack.release()
    }
}
