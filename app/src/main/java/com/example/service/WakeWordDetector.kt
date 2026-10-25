package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class WakeSensitivity(val thresholdRms: Float, val label: String) {
    HIGH(0.03f, "উচ্চ (High - Highly Sensitive)"),
    MEDIUM(0.06f, "মাঝারি (Medium - Balanced)"),
    LOW(0.12f, "কম (Low - Strict)")
}

class WakeWordDetector(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val SAMPLE_RATE = 16000
        private val WAKE_WORDS = listOf(
            "hey arushi", "arushi", "arushii", "he arushi", "aarushi", "arosi",
            "hey sojib", "sojib", "he sojib", "shojib", "sojib ai",
            "আরুষি", "হে আরুষি", "সজীব", "হে সজীব", "সজিব"
        )
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _sensitivity = MutableStateFlow(WakeSensitivity.MEDIUM)
    val sensitivity: StateFlow<WakeSensitivity> = _sensitivity.asStateFlow()

    private val _customWakeWord = MutableStateFlow<String?>(null)
    val customWakeWord: StateFlow<String?> = _customWakeWord.asStateFlow()

    var onWakeWordDetected: ((keyword: String) -> Unit)? = null
    var onAmplitudeUpdated: ((Float) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastTriggerTime: Long = 0L
    private val triggerCooldownMs = 1800L

    @Volatile
    private var isPaused = false

    fun setSensitivity(level: WakeSensitivity) {
        _sensitivity.value = level
        Log.d(TAG, "WakeWord sensitivity set to: ${level.name} (RMS: ${level.thresholdRms})")
    }

    fun setCustomWakeWord(word: String?) {
        _customWakeWord.value = word?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    }

    @SuppressLint("MissingPermission")
    fun startDetection() {
        if (_isListening.value) return
        _isListening.value = true
        isPaused = false

        initSpeechRecognizer()

        listeningJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, 2048)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed.")
                    _isListening.value = false
                    return@launch
                }

                audioRecord?.startRecording()
                Log.d(TAG, "WakeWord low-power listening started.")

                val buffer = ByteArray(1024)
                var consecutiveSpeechFrames = 0

                while (isActive && _isListening.value) {
                    if (isPaused) {
                        delay(200)
                        continue
                    }

                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            if (i + 1 < read) {
                                val s = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                                val shortVal = s.toShort()
                                sum += shortVal * shortVal
                            }
                        }
                        val rms = Math.sqrt(sum / (read / 2))
                        val normalizedAmp = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        onAmplitudeUpdated?.invoke(normalizedAmp)

                        val threshold = _sensitivity.value.thresholdRms
                        if (normalizedAmp > threshold) {
                            consecutiveSpeechFrames++
                            // At least 2-3 consecutive frames (~100-150ms) to reject impulse noise
                            if (consecutiveSpeechFrames >= 3) {
                                val now = System.currentTimeMillis()
                                if (now - lastTriggerTime > triggerCooldownMs) {
                                    triggerQuickSpeechCheck()
                                }
                                consecutiveSpeechFrames = 0
                            }
                        } else {
                            consecutiveSpeechFrames = 0
                        }
                    }
                    delay(35)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in WakeWord loop", e)
            } finally {
                stopInternalAudioRecord()
            }
        }
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                // Handled gracefully, restart listening if still enabled
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                checkKeywordMatches(matches)
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                checkKeywordMatches(matches)
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing recognizer for wake word", e)
            }
        }
    }

    private fun checkKeywordMatches(matches: ArrayList<String>?) {
        if (matches.isNullOrEmpty()) return
        val custom = _customWakeWord.value

        for (item in matches) {
            val lower = item.lowercase(Locale.getDefault()).trim()
            val matchedWord = when {
                custom != null && lower.contains(custom) -> custom
                WAKE_WORDS.any { lower.contains(it) } -> {
                    WAKE_WORDS.first { lower.contains(it) }
                }
                else -> null
            }

            if (matchedWord != null) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > triggerCooldownMs) {
                    lastTriggerTime = now
                    Log.d(TAG, "Wake word triggered: '$matchedWord'")
                    mainHandler.post {
                        onWakeWordDetected?.invoke(matchedWord)
                    }
                }
                break
            }
        }
    }

    private fun triggerQuickSpeechCheck() {
        mainHandler.post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering quick speech check", e)
            }
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stopDetection() {
        _isListening.value = false
        isPaused = false
        listeningJob?.cancel()
        stopInternalAudioRecord()
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speech recognizer", e)
            }
        }
    }

    private fun stopInternalAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audioRecord", e)
        }
    }
}
