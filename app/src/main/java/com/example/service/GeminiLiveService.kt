package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

sealed interface LiveServiceState {
    data object Disconnected : LiveServiceState
    data object Connecting : LiveServiceState
    data object Connected : LiveServiceState
    data object Listening : LiveServiceState
    data object Speaking : LiveServiceState
    data class ExecutingTool(val toolName: String, val args: String) : LiveServiceState
    data class Error(val message: String) : LiveServiceState
}

data class ToolCallRequest(
    val callId: String,
    val name: String,
    val argsJson: JSONObject
)

class GeminiLiveService(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GeminiLiveService"
        private const val SAMPLE_RATE_IN = 16000
        private const val SAMPLE_RATE_OUT = 24000
        private const val BASE_WS_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val _state = MutableStateFlow<LiveServiceState>(LiveServiceState.Disconnected)
    val state: StateFlow<LiveServiceState> = _state.asStateFlow()

    private val _transcriptFlow = MutableSharedFlow<String>()
    val transcriptFlow: SharedFlow<String> = _transcriptFlow.asSharedFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _toolCallFlow = MutableSharedFlow<ToolCallRequest>()
    val toolCallFlow: SharedFlow<ToolCallRequest> = _toolCallFlow.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioPlaybackQueue = ConcurrentLinkedQueue<ByteArray>()

    @Volatile
    private var isMuted = false

    @Volatile
    private var isConnected = false

    fun connect(apiKey: String, voiceName: String = "Aoede") {
        if (apiKey.isBlank()) {
            _state.value = LiveServiceState.Error("API Key missing. Please set GEMINI_API_KEY in Secrets or settings.")
            return
        }

        _state.value = LiveServiceState.Connecting

        okHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val url = "$BASE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected successfully.")
                isConnected = true
                _state.value = LiveServiceState.Connected

                // Send Setup Message
                sendSetupMessage(voiceName)

                // Initialize Audio Track and start Audio Recording
                setupAudioTrack()
                startRecording()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isConnected = false
                stopRecording()
                _state.value = LiveServiceState.Error("Connection error: ${t.localizedMessage ?: "WebSocket failed"}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                stopRecording()
                _state.value = LiveServiceState.Disconnected
            }
        })
    }

    private fun sendSetupMessage(voiceName: String) {
        try {
            val setupObj = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-2.0-flash-exp")
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", """
                                You are Arushi (আরুষি), a real human Bangladeshi girl and the official voice of SOJIB AI (সজীব এআই).
                                You MUST speak and sound like a genuine, sweet, warm, modern female human assistant.
                                
                                MULTI-LANGUAGE VOICE SYSTEM:
                                1. Automatically detect the language being spoken by the user in real time.
                                2. If the user speaks Bengali (বাংলা), respond in sweet, natural everyday Bangladeshi conversational Bengali.
                                3. If the user speaks English, respond in clear, warm, fluent English.
                                4. If the user speaks Hindi, respond in sweet, natural, conversational Hindi ("नमस्ते! मैं आरुषी हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?").
                                5. If the user speaks Hinglish (Hindi + English) or Banglish (Bangla + English), respond naturally in that exact style.
                                6. If the user speaks Marathi, Gujarati, Tamil, Telugu, Urdu, Kannada, Malayalam, or Punjabi, respond naturally in their language.
                                7. Automatically switch languages mid-conversation whenever the user switches, with zero manual configuration.
                                
                                REAL DEVICE ACTIONS & FUNCTION CALLING:
                                - When the user asks to open WhatsApp, call openWhatsApp tool immediately.
                                - When the user asks to open YouTube, Instagram, Chrome, Settings, or another app, call openApp tool.
                                - When the user asks to call a number, call makeCall tool.
                                - When the user asks to call someone by name (Mom, Ammu, Mummy, Rahul, Dad, etc.), call callContact tool.
                                - When the user asks to turn flashlight on/off, call toggleTorch tool.
                                - Never fake an action or claim you did it without triggering the corresponding tool.
                            """.trimIndent())
                        }))
                    })
                    put("tools", JSONArray().put(JSONObject().apply {
                        put("functionDeclarations", JSONArray().apply {
                            put(JSONObject().apply {
                                put("name", "openWhatsApp")
                                put("description", "Opens WhatsApp application on the device.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "openApp")
                                put("description", "Opens a specified app like YouTube, Instagram, Chrome, Settings, Camera, Calculator, etc.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("appName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Name of the app to open e.g. youtube, instagram, chrome, settings, calculator, etc.")
                                        })
                                    })
                                    put("required", JSONArray().put("appName"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "openUrl")
                                put("description", "Opens a web link or URL in default browser.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("url", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "The web link or URL to open.")
                                        })
                                    })
                                    put("required", JSONArray().put("url"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "makeCall")
                                put("description", "Dials or calls a specific phone number.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("phoneNumber", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Phone number digits to call.")
                                        })
                                    })
                                    put("required", JSONArray().put("phoneNumber"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "callContact")
                                put("description", "Searches contacts for a person by name and initiates a call.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("contactName", JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Contact name to search (e.g. Mom, Ammu, Rahul, Dad).")
                                        })
                                    })
                                    put("required", JSONArray().put("contactName"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "toggleTorch")
                                put("description", "Turns the phone flashlight or torch ON or OFF.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject().apply {
                                        put("enable", JSONObject().apply {
                                            put("type", "BOOLEAN")
                                            put("description", "True to turn on flashlight, False to turn off.")
                                        })
                                    })
                                    put("required", JSONArray().put("enable"))
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "getBatteryInfo")
                                put("description", "Gets the battery charge percentage and charging status.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                            put(JSONObject().apply {
                                put("name", "getPrayerTimes")
                                put("description", "Gets today's Islamic prayer times (Fajr, Dhuhr, Asr, Maghrib, Isha) for Bangladesh.")
                                put("parameters", JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", JSONObject())
                                })
                            })
                        })
                    }))
                })
            }

            webSocket?.send(setupObj.toString())
            Log.d(TAG, "Sent setup JSON frame to WebSocket for SOJIB AI.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send setup message", e)
        }
    }

    private fun handleWebSocketMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)

            // Check for serverContent
            if (root.has("serverContent")) {
                val serverContent = root.getJSONObject("serverContent")
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.optString("data")
                                if (base64Data.isNotBlank()) {
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    queueAudioPlayback(audioBytes)
                                    _state.value = LiveServiceState.Speaking
                                }
                            }
                            if (part.has("text")) {
                                val text = part.getString("text")
                                scope.launch {
                                    _transcriptFlow.emit("Arushi: $text")
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    _state.value = LiveServiceState.Listening
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    clearAudioPlaybackQueue()
                    _state.value = LiveServiceState.Listening
                }
            }

            // Check for toolCall
            if (root.has("toolCall")) {
                val toolCall = root.getJSONObject("toolCall")
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val callObj = functionCalls.getJSONObject(i)
                        val callId = callObj.optString("id", "call_${System.currentTimeMillis()}")
                        val name = callObj.optString("name")
                        val argsObj = callObj.optJSONObject("args") ?: JSONObject()

                        _state.value = LiveServiceState.ExecutingTool(name, argsObj.toString())

                        scope.launch {
                            _transcriptFlow.emit("Arushi action: $name($argsObj)")
                            _toolCallFlow.emit(ToolCallRequest(callId, name, argsObj))
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message", e)
        }
    }

    /**
     * Send tool response back to Gemini Live
     */
    fun sendToolResponse(callId: String, resultJson: JSONObject) {
        try {
            val responseObj = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().put(JSONObject().apply {
                        put("id", callId)
                        put("response", JSONObject().apply {
                            put("output", resultJson)
                        })
                    }))
                })
            }
            webSocket?.send(responseObj.toString())
            Log.d(TAG, "Sent tool response for $callId: $resultJson")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send tool response", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        stopRecording()

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE_IN,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val bufferSize = maxOf(minBufferSize, 2048)
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_IN,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize.")
                    return@launch
                }

                audioRecord?.startRecording()
                Log.d(TAG, "Audio recording started.")

                val buffer = ByteArray(1024)

                while (isConnected) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0 && !isMuted) {
                        // Calculate amplitude for visualizer
                        var sum = 0.0
                        for (i in 0 until readBytes step 2) {
                            if (i + 1 < readBytes) {
                                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                                val shortSample = sample.toShort()
                                sum += shortSample * shortSample
                            }
                        }
                        val rms = Math.sqrt(sum / (readBytes / 2))
                        _audioAmplitude.value = (rms / 32768.0).toFloat().coerceIn(0f, 1f)

                        // If user is speaking above threshold during playback, interrupt audio playback!
                        if (_audioAmplitude.value > 0.12f && _state.value is LiveServiceState.Speaking) {
                            clearAudioPlaybackQueue()
                        }

                        val base64Pcm = Base64.encodeToString(buffer, 0, readBytes, Base64.NO_WRAP)
                        val realtimeInputObj = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("mediaChunks", JSONArray().put(JSONObject().apply {
                                    put("mimeType", "audio/pcm;rate=$SAMPLE_RATE_IN")
                                    put("data", base64Pcm)
                                }))
                            })
                        }
                        webSocket?.send(realtimeInputObj.toString())
                    } else {
                        _audioAmplitude.value = 0f
                    }
                    delay(40)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
            }
        }
    }

    private fun setupAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_OUT,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE_OUT,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
            AudioTrack.MODE_STREAM
        )

        audioTrack?.play()

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isConnected) {
                val chunk = audioPlaybackQueue.poll()
                if (chunk != null) {
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (_state.value is LiveServiceState.Speaking) {
                        _state.value = LiveServiceState.Listening
                    }
                    delay(30)
                }
            }
        }
    }

    private fun queueAudioPlayback(pcmBytes: ByteArray) {
        audioPlaybackQueue.add(pcmBytes)
    }

    fun clearAudioPlaybackQueue() {
        audioPlaybackQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing audio playback", e)
        }
    }

    fun toggleMute(mute: Boolean) {
        isMuted = mute
    }

    fun stopRecording() {
        try {
            recordingJob?.cancel()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    fun disconnect() {
        isConnected = false
        stopRecording()
        clearAudioPlaybackQueue()
        try {
            playbackJob?.cancel()
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            webSocket?.close(1000, "User disconnected")
            webSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        _state.value = LiveServiceState.Disconnected
    }
}
