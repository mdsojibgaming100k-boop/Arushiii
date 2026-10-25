package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface VoiceEngineState {
    data object Idle : VoiceEngineState
    data class Listening(val partialText: String = "") : VoiceEngineState
    data object Thinking : VoiceEngineState
    data class Speaking(val text: String) : VoiceEngineState
    data class Error(val message: String) : VoiceEngineState
}

class VoiceAssistantEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceAssistantEngine"
    }

    private val _engineState = MutableStateFlow<VoiceEngineState>(VoiceEngineState.Idle)
    val engineState: StateFlow<VoiceEngineState> = _engineState.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var onCommandResult: ((userInput: String, replyText: String) -> Unit)? = null

    init {
        initTts()
        initSpeechRecognizer()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                val bnBd = Locale("bn", "BD")
                val res = textToSpeech?.setLanguage(bnBd)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale("bn", "IN"))
                }
                textToSpeech?.setPitch(1.22f) // Sweet natural female pitch
                textToSpeech?.setSpeechRate(0.96f) // Natural Bangladeshi conversational speed

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _engineState.value = VoiceEngineState.Speaking(utteranceId.orEmpty())
                    }

                    override fun onDone(utteranceId: String?) {
                        _engineState.value = VoiceEngineState.Idle
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _engineState.value = VoiceEngineState.Idle
                    }
                })
                Log.d(TAG, "TextToSpeech initialized successfully with sweet female pitch.")
            }
        }
    }

    private fun initSpeechRecognizer() {
        scope.launch(Dispatchers.Main) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _engineState.value = VoiceEngineState.Listening("শুনছি, বলুন...")
                        }

                        override fun onBeginningOfSpeech() {
                            _amplitude.value = 0.5f
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            _amplitude.value = norm
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _amplitude.value = 0f
                            _engineState.value = VoiceEngineState.Thinking
                        }

                        override fun onError(error: Int) {
                            _amplitude.value = 0f
                            Log.e(TAG, "Speech recognition error code: $error")
                            if (_engineState.value !is VoiceEngineState.Speaking) {
                                _engineState.value = VoiceEngineState.Idle
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            _amplitude.value = 0f
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognized = matches?.firstOrNull()?.trim()
                            if (!recognized.isNullOrBlank()) {
                                handleUserVoiceInput(recognized)
                            } else {
                                _engineState.value = VoiceEngineState.Idle
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()
                            if (!text.isNullOrBlank()) {
                                _engineState.value = VoiceEngineState.Listening(text)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        }
    }

    fun startListening() {
        stopSpeaking()
        scope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("bn-IN", "en-US", "hi-IN"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
                _engineState.value = VoiceEngineState.Listening("শুনছি, বলুন...")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                _engineState.value = VoiceEngineState.Error("মাইক্রোফোন চালু করা যায়নি।")
            }
        }
    }

    fun stopListening() {
        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognition", e)
            }
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        scope.launch(Dispatchers.Main) {
            try {
                val cleanText = text.replace(Regex("[*#_`•\\[\\]]"), " ")
                textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "arushi_utterance")
                _engineState.value = VoiceEngineState.Speaking(cleanText)
            } catch (e: Exception) {
                Log.e(TAG, "Error in TTS speak", e)
            }
        }
    }

    fun stopSpeaking() {
        try {
            textToSpeech?.stop()
            if (_engineState.value is VoiceEngineState.Speaking) {
                _engineState.value = VoiceEngineState.Idle
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    private fun handleUserVoiceInput(userInput: String) {
        _engineState.value = VoiceEngineState.Thinking
        scope.launch(Dispatchers.IO) {
            onCommandResult?.invoke(userInput, "")
        }
    }

    /**
     * Ask Gemini for intelligent Bangladeshi female response
     */
    suspend fun queryGeminiAi(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", """
                            You are Arushi (আরুষি), a real human-like, sweet, warm, modern Bangladeshi girl and the official voice of SOJIB AI.
                            The user said: "$prompt"
                            
                            Instructions:
                            1. Reply directly to the user in sweet, natural conversational Bengali (বাংলা).
                            2. Keep it concise (1 to 2 short sweet sentences), suitable for spoken audio.
                            3. Never sound robotic. Sound like a helpful Bangladeshi girl.
                        """.trimIndent())
                    }))
                }))
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val candidates = json.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!text.isNullOrBlank()) {
                    return@withContext text.trim()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Gemini REST", e)
        }
        return@withContext "আমি শুনেছি: '$prompt'। আপনার কাজটি সম্পন্ন করার চেষ্টা করছি!"
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying VoiceAssistantEngine", e)
        }
    }
}
