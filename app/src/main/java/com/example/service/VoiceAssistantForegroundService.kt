package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AndroidActionBridge
import com.example.data.ContactSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed interface AssistantMode {
    data object StandbyWakeWord : AssistantMode
    data object ActiveListening : AssistantMode
    data object Speaking : AssistantMode
    data object Paused : AssistantMode
}

class VoiceAssistantForegroundService : Service() {

    companion object {
        private const val TAG = "VoiceAssistantService"
        const val CHANNEL_ID = "sojib_ai_voice_assistant_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"
        const val ACTION_TRIGGER_VOICE = "com.example.service.ACTION_TRIGGER_VOICE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentMode = MutableStateFlow<AssistantMode>(AssistantMode.StandbyWakeWord)
        val currentMode: StateFlow<AssistantMode> = _currentMode.asStateFlow()

        private val _amplitude = MutableStateFlow(0f)
        val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

        private val _statusText = MutableStateFlow("আরুষি শুনছে...")
        val statusText: StateFlow<String> = _statusText.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pauseListening(context: Context) {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeListening(context: Context) {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var voiceEngine: VoiceAssistantEngine
    private lateinit var liveService: GeminiLiveService
    private lateinit var actionBridge: AndroidActionBridge
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): VoiceAssistantForegroundService = this@VoiceAssistantForegroundService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceAssistantForegroundService onCreate")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        actionBridge = AndroidActionBridge(this)
        voiceEngine = VoiceAssistantEngine(this, serviceScope)
        liveService = GeminiLiveService(this, serviceScope)
        wakeWordDetector = WakeWordDetector(this, serviceScope)

        createNotificationChannel()
        acquireWakeLock()
        requestAudioFocus()
        registerNetworkMonitoring()
        setupListeners()
    }

    private fun setupListeners() {
        // Wake-word detection callback
        wakeWordDetector.onWakeWordDetected = { keyword ->
            Log.d(TAG, "Wake word detected in background: $keyword")
            vibrateDevice()
            handleWakeWordTriggered(keyword)
        }

        wakeWordDetector.onAmplitudeUpdated = { amp ->
            _amplitude.value = amp
        }

        // Voice Engine state updates
        serviceScope.launch {
            voiceEngine.engineState.collect { state ->
                when (state) {
                    is VoiceEngineState.Speaking -> {
                        _currentMode.value = AssistantMode.Speaking
                        _statusText.value = "আরুষি কথা বলছে..."
                        updateNotification("আরুষি কথা বলছে...", "Sojib AI ভয়েস সহকারী সক্রিয়")
                    }
                    is VoiceEngineState.Listening -> {
                        _currentMode.value = AssistantMode.ActiveListening
                        val prompt = if (state.partialText.isNotBlank()) state.partialText else "শুনছি, বলুন..."
                        _statusText.value = prompt
                        updateNotification(prompt, "মাইক্রোফোন সক্রিয়")
                    }
                    is VoiceEngineState.Thinking -> {
                        _statusText.value = "আরুষি ভাবছে..."
                        updateNotification("আরুষি ভাবছে...", "কমান্ড প্রসেস করা হচ্ছে")
                    }
                    is VoiceEngineState.Idle -> {
                        if (_isRunning.value && _currentMode.value !is AssistantMode.Paused) {
                            _currentMode.value = AssistantMode.StandbyWakeWord
                            _statusText.value = "বলেই ডাকুন: 'হে আরুষি' বা 'Hey Sojib'"
                            updateNotification("বলেই ডাকুন: 'হে আরুষি' বা 'Hey Sojib'", "ব্যাকগ্রাউন্ডে সচল আছে")
                        }
                    }
                    is VoiceEngineState.Error -> {
                        _statusText.value = state.message
                    }
                }
            }
        }

        voiceEngine.onCommandResult = { input, _ ->
            handleCommand(input)
        }
    }

    private fun handleWakeWordTriggered(keyword: String) {
        _currentMode.value = AssistantMode.ActiveListening
        val greeting = "হ্যাঁ, বলুন! আমি শুনছি।"
        _statusText.value = greeting
        updateNotification("আরুষি শুনছে...", "ওয়েক ওয়ার্ড অ্যাক্টিভ হয়েছে")

        voiceEngine.speak(greeting)

        serviceScope.launch {
            kotlinx.coroutines.delay(2200)
            if (_isRunning.value && _currentMode.value !is AssistantMode.Paused) {
                voiceEngine.startListening()
            }
        }
    }

    private fun handleCommand(command: String) {
        if (command.isBlank()) return
        val clean = command.lowercase()
        Log.d(TAG, "Background Voice Command: $command")

        serviceScope.launch {
            when {
                clean.contains("whatsapp") || clean.contains("হোয়াটসঅ্যাপ") -> {
                    actionBridge.openWhatsApp()
                    val reply = "হোয়াটসঅ্যাপ ওপেন করে দিয়েছি।"
                    voiceEngine.speak(reply)
                }
                clean.contains("youtube") || clean.contains("ইউটিউব") -> {
                    actionBridge.openApp("youtube")
                    val reply = "ইউটিউব খুলে দেওয়া হয়েছে।"
                    voiceEngine.speak(reply)
                }
                clean.contains("instagram") || clean.contains("ইন্সটাগ্রাম") -> {
                    actionBridge.openApp("instagram")
                    val reply = "ইন্সটাগ্রাম ওপেন করছি।"
                    voiceEngine.speak(reply)
                }
                clean.contains("chrome") || clean.contains("ক্রোম") -> {
                    actionBridge.openApp("chrome")
                    val reply = "ক্রোম ব্রাউজার খুলে দিয়েছি।"
                    voiceEngine.speak(reply)
                }
                clean.contains("settings") || clean.contains("সেটিংস") -> {
                    actionBridge.openApp("settings")
                    val reply = "ডিভাইস সেটিংস ওপেন করা হয়েছে।"
                    voiceEngine.speak(reply)
                }
                clean.contains("টর্চ জ্বালাও") || clean.contains("টর্চ অন") || clean.contains("torch on") -> {
                    actionBridge.toggleTorch(true)
                    val reply = "টর্চলাইট অন করা হয়েছে।"
                    voiceEngine.speak(reply)
                }
                clean.contains("টর্চ বন্ধ") || clean.contains("টর্চ অফ") || clean.contains("torch off") -> {
                    actionBridge.toggleTorch(false)
                    val reply = "টর্চ বন্ধ করা হয়েছে।"
                    voiceEngine.speak(reply)
                }
                clean.contains("ব্যাটারি") || clean.contains("চার্জ") || clean.contains("battery") -> {
                    val info = actionBridge.getBatteryInfo()
                    voiceEngine.speak(info.message)
                }
                clean.contains("নামাজ") || clean.contains("prayer") -> {
                    val prayerReply = "আজকের ফজর চারটা তেতাল্লিশ, যোহর এগারটা ছেচল্লিশ, আসর তিনটা ছাপ্পান্ন, মাগরিব পাঁচটা চব্বিশ এবং এশা ছয়টা চল্লিশ।"
                    voiceEngine.speak(prayerReply)
                }
                clean.startsWith("call ") || clean.contains("কল করো") || clean.contains("ফোন করো") -> {
                    val target = command.replace(Regex("(?i)call|কল করো|ফোন করো|ফোন দাও"), "").trim()
                    if (target.matches(Regex("[0-9+ ]+"))) {
                        voiceEngine.speak("$target নম্বরে কল দেওয়া হচ্ছে।")
                        actionBridge.makeCall(target, directCallPermitted = true)
                    } else {
                        val search = actionBridge.callContact(target)
                        when (search) {
                            is ContactSearchResult.SingleMatch -> {
                                voiceEngine.speak("${search.contact.name} কে ফোন করছি।")
                                actionBridge.makeCall(search.contact.phoneNumber, directCallPermitted = true)
                            }
                            is ContactSearchResult.MultipleMatches -> {
                                voiceEngine.speak("একাধিক কন্টাক্ট পাওয়া গেছে। প্রথম নম্বরটিতে কল দেওয়া হচ্ছে।")
                                actionBridge.makeCall(search.contacts.first().phoneNumber, directCallPermitted = true)
                            }
                            else -> {
                                voiceEngine.speak("আপনার কন্টাক্ট তালিকায় $target পাওয়া যায়নি।")
                            }
                        }
                    }
                }
                else -> {
                    val defaultKey = "AIzaSyDIR4Z17dmwzQ_WM2rlZjvB3Uh5NJ7EQmI"
                    val reply = voiceEngine.queryGeminiAi(defaultKey, command)
                    voiceEngine.speak(reply)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_START -> {
                _isRunning.value = true
                startForegroundWithNotification()
                wakeWordDetector.startDetection()
            }
            ACTION_STOP -> {
                stopAssistant()
            }
            ACTION_PAUSE -> {
                pauseAssistant()
            }
            ACTION_RESUME -> {
                resumeAssistant()
            }
            ACTION_TRIGGER_VOICE -> {
                handleWakeWordTriggered("manual_button")
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification(
            title = "SOJIB AI — আরুষি শুনছে",
            content = "বলেই ডাকুন: 'হে আরুষি' বা 'Hey Sojib'",
            isPaused = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, content: String, isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Pause / Resume
        val toggleActionIntent = Intent(this, VoiceAssistantForegroundService::class.java).apply {
            this.action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop
        val stopActionIntent = Intent(this, VoiceAssistantForegroundService::class.java).apply {
            this.action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause,
            if (isPaused) "Resume" else "Pause",
            togglePendingIntent
        ).build()

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop Assistant",
            stopPendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(toggleAction)
            .addAction(stopAction)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notif = buildNotification(title, content, _currentMode.value is AssistantMode.Paused)
        manager?.notify(NOTIFICATION_ID, notif)
    }

    private fun pauseAssistant() {
        _currentMode.value = AssistantMode.Paused
        _statusText.value = "ভয়েস সহকারী সাময়িক স্থগিত"
        wakeWordDetector.pause()
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        releaseWakeLock()
        updateNotification("SOJIB AI — সাময়িক স্থগিত (Paused)", "পুনরায় চালু করতে Resume চাপুন")
    }

    private fun resumeAssistant() {
        acquireWakeLock()
        _currentMode.value = AssistantMode.StandbyWakeWord
        _statusText.value = "বলেই ডাকুন: 'হে আরুষি' বা 'Hey Sojib'"
        wakeWordDetector.resume()
        wakeWordDetector.startDetection()
        updateNotification("SOJIB AI — আরুষি শুনছে", "বলেই ডাকুন: 'হে আরুষি' বা 'Hey Sojib'")
    }

    private fun stopAssistant() {
        _isRunning.value = false
        _currentMode.value = AssistantMode.Paused
        wakeWordDetector.stopDetection()
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        liveService.disconnect()
        releaseWakeLock()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SojibAI:VoiceServiceWakeLock")
        }
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(12 * 60 * 60 * 1000L) // 12 hours safe max timeout
                Log.d(TAG, "WakeLock acquired.")
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock safely released.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "Audio focus changed: $focusChange")
                }
                .build()

            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
    }

    private fun registerNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Internet connection restored in background.")
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Internet connection lost.")
            }
        }

        networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(120)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sojib AI Background Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for Arushi background voice listening and wake-word detection."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceAssistantForegroundService onDestroy")
        _isRunning.value = false
        wakeWordDetector.stopDetection()
        voiceEngine.destroy()
        liveService.disconnect()
        releaseWakeLock()
        abandonAudioFocus()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
        serviceScope.cancel()
    }
}
