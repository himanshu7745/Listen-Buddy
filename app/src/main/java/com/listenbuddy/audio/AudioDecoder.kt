package com.listenbuddy.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

class AudioDecoder(
    private val context: Context,
    private val uri: Uri
) {

    private lateinit var extractor: MediaExtractor
    private lateinit var codec: MediaCodec

    var sampleRate = 0
    var channelCount = 0

    @Volatile
    private var isReleased = false

    fun prepare() {
        extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime!!.startsWith("audio/")) {
                extractor.selectTrack(i)
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                break
            }
        }
    }

    fun decode(onPcmReady: (ByteArray) -> Unit) {

        val bufferInfo = MediaCodec.BufferInfo()
        var isEOS = false

        while (!isReleased) {

            if (!isEOS) {
                try {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        if (isReleased) break

                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: break
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0,
                                0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                } catch (e: IllegalStateException) {
                    break
                }
            }

            try {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)

                if (outputIndex >= 0) {
                    if (isReleased) break

                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: break
                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmData)
                    outputBuffer.clear()

                    if (bufferInfo.size > 0 && !isReleased) {
                        onPcmReady(pcmData)
                    }

                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            } catch (e: IllegalStateException) {
                break
            }
        }
    }

    fun release() {
        isReleased = true

        try {
            if (::codec.isInitialized) {
                codec.stop()
                codec.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (::extractor.isInitialized) {
                extractor.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}