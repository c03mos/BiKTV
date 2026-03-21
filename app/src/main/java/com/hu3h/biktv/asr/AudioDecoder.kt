package com.hu3h.biktv.asr

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

object AudioDecoder {
    data class PcmData(
        val samples: FloatArray,
        val sampleRate: Int
    )

    fun decodeToMonoPcm(file: File): PcmData {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var audioTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrack = i
                format = f
                break
            }
        }
        if (audioTrack < 0 || format == null) {
            extractor.release()
            error("No audio track found")
        }
        extractor.selectTrack(audioTrack)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = ArrayList<Float>(sampleRate * 30)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        val timeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, timeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                val chunk = ByteArray(info.size)
                outputBuffer.get(chunk)
                outputBuffer.clear()
                // PCM 16-bit
                val samples = ShortArray(chunk.size / 2)
                var i = 0
                var s = 0
                while (i < chunk.size) {
                    val lo = chunk[i].toInt() and 0xFF
                    val hi = chunk[i + 1].toInt()
                    samples[s] = ((hi shl 8) or lo).toShort()
                    i += 2
                    s += 1
                }
                // downmix
                if (channelCount == 1) {
                    samples.forEach { out.add(it / 32768.0f) }
                } else {
                    var idx = 0
                    while (idx + channelCount - 1 < samples.size) {
                        var sum = 0f
                        for (c in 0 until channelCount) {
                            sum += samples[idx + c] / 32768.0f
                        }
                        out.add(sum / channelCount)
                        idx += channelCount
                    }
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        return PcmData(out.toFloatArray(), sampleRate)
    }

    fun resampleTo16k(pcm: PcmData, targetRate: Int = 16000): FloatArray {
        if (pcm.sampleRate == targetRate) return pcm.samples
        val src = pcm.samples
        val ratio = targetRate.toDouble() / pcm.sampleRate.toDouble()
        val outLen = max(1, (src.size * ratio).toInt())
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i / ratio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val s0 = src[min(idx, src.size - 1)]
            val s1 = src[min(idx + 1, src.size - 1)]
            out[i] = (s0 * (1.0 - frac) + s1 * frac).toFloat()
        }
        return out
    }
}
