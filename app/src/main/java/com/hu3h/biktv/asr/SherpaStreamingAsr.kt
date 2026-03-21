package com.hu3h.biktv.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class SrtSegment(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SherpaStreamingAsr {
    private const val MODEL_DIR = "sherpa-onnx/zipformer-small-zh-en-int8"
    private const val SAMPLE_RATE = 16000
    private const val FEATURE_DIM = 80

    private var recognizer: OnlineRecognizer? = null
    private val initialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (initialized.get()) return
        synchronized(this) {
            if (initialized.get()) return
            val featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM)
            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$MODEL_DIR/encoder.int8.onnx",
                    decoder = "$MODEL_DIR/decoder.int8.onnx",
                    joiner = "$MODEL_DIR/joiner.int8.onnx"
                ),
                tokens = "$MODEL_DIR/tokens.txt",
                numThreads = 2,
                provider = "cpu",
                modelType = "zipformer"
            )
            val config = OnlineRecognizerConfig(
                featConfig = featConfig,
                modelConfig = modelConfig,
                decodingMethod = "greedy_search",
                maxActivePaths = 4,
                enableEndpoint = true
            )
            recognizer = OnlineRecognizer(context.assets, config)
            initialized.set(true)
        }
    }

    fun transcribeToSrt(context: Context, audioFile: File): String {
        init(context)
        val rec = recognizer ?: error("Recognizer not initialized")
        val pcm = AudioDecoder.decodeToMonoPcm(audioFile)
        val samples = AudioDecoder.resampleTo16k(pcm, SAMPLE_RATE)

        val stream: OnlineStream = rec.createStream()
        val chunkSize = SAMPLE_RATE / 5 // 200ms
        var idx = 0
        var segmentIndex = 1
        val segments = mutableListOf<SrtSegment>()
        var lastText = ""
        var lastTimeMs = 0L

        while (idx < samples.size) {
            val end = kotlin.math.min(samples.size, idx + chunkSize)
            val chunk = samples.copyOfRange(idx, end)
            stream.acceptWaveform(chunk, SAMPLE_RATE)
            while (rec.isReady(stream)) {
                rec.decode(stream)
            }
            val result = rec.getResult(stream)
            if (result.text.isNotBlank() && result.text != lastText) {
                lastText = result.text
            }
            if (rec.isEndpoint(stream)) {
                val text = result.text.trim()
                if (text.isNotBlank()) {
                    val startMs = lastTimeMs
                    val endMs = ((end.toDouble() / SAMPLE_RATE) * 1000).toLong()
                    segments.add(SrtSegment(segmentIndex++, startMs, endMs, text))
                    lastTimeMs = endMs
                }
                rec.reset(stream)
            }
            idx = end
        }
        // flush
        val finalResult = rec.getResult(stream).text.trim()
        if (finalResult.isNotBlank()) {
            val endMs = ((samples.size.toDouble() / SAMPLE_RATE) * 1000).toLong()
            segments.add(SrtSegment(segmentIndex, lastTimeMs, endMs, finalResult))
        }

        return buildSrt(segments)
    }

    private fun buildSrt(segments: List<SrtSegment>): String {
        val sb = StringBuilder()
        segments.forEach { seg ->
            sb.append(seg.index).append("\n")
            sb.append(formatTime(seg.startMs)).append(" --> ").append(formatTime(seg.endMs)).append("\n")
            sb.append(seg.text).append("\n\n")
        }
        return sb.toString()
    }

    private fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / 3_600_000
        val minutes = (total % 3_600_000) / 60_000
        val seconds = (total % 60_000) / 1000
        val millis = total % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
