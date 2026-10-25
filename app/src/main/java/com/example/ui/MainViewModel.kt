package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AdminConfig
import com.example.data.AdminRepository
import com.example.data.AndroidActionBridge
import com.example.data.ContactItem
import com.example.data.ContactSearchResult
import com.example.data.PaymentSubmission
import com.example.service.GeminiLiveService
import com.example.service.LiveServiceState
import com.example.service.ToolCallRequest
import com.example.service.VoiceAssistantEngine
import com.example.service.VoiceEngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class LogEntry(
    val id: String = System.currentTimeMillis().toString() + "_" + (1000..9999).random(),
    val timestamp: String,
    val sender: String, // "User", "Arushi", "System", "Action"
    val message: String,
    val isError: Boolean = false
)

data class ConfirmationDialogState(
    val isVisible: Boolean = false,
    val title: String = "",
    val message: String = "",
    val confirmText: String = "Confirm",
    val cancelText: String = "Cancel",
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {}
)

data class MultipleContactsDialogState(
    val isVisible: Boolean = false,
    val query: String = "",
    val contacts: List<ContactItem> = emptyList(),
    val onSelect: (ContactItem) -> Unit = {}
)

data class MainUiState(
    val liveState: LiveServiceState = LiveServiceState.Disconnected,
    val logs: List<LogEntry> = emptyList(),
    val isMuted: Boolean = false,
    val amplitude: Float = 0f,
    val apiKey: String = "AIzaSyDIR4Z17dmwzQ_WM2rlZjvB3Uh5NJ7EQmI",
    val isDirectCallEnabled: Boolean = true,
    val confirmationDialog: ConfirmationDialogState = ConfirmationDialogState(),
    val multipleContactsDialog: MultipleContactsDialogState = MultipleContactsDialogState(),
    val activeVoiceName: String = "Aoede", // Warm, natural female voice
    val adminConfig: AdminConfig = AdminConfig(),
    val isUserPremium: Boolean = false,
    val userMessageCount: Int = 0,
    val paymentSubmissions: List<PaymentSubmission> = emptyList(),
    val showAdminDialog: Boolean = false,
    val showSubscriptionDialog: Boolean = false,
    val isVoiceActive: Boolean = false,
    val voiceStatusText: String = "আরুষির সাথে কথা বলুন",
    val isBackgroundServiceRunning: Boolean = false,
    val wakeWordEnabled: Boolean = true,
    val wakeSensitivity: com.example.service.WakeSensitivity = com.example.service.WakeSensitivity.MEDIUM,
    val isBatteryOptimizationIgnored: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val actionBridge = AndroidActionBridge(application)
    val liveService = GeminiLiveService(application, viewModelScope)
    val adminRepo = AdminRepository(application)
    val voiceEngine = VoiceAssistantEngine(application, viewModelScope)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Load GEMINI_API_KEY from BuildConfig or use aisojib key
        val defaultKey = try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else "AIzaSyDIR4Z17dmwzQ_WM2rlZjvB3Uh5NJ7EQmI"
        } catch (e: Exception) {
            "AIzaSyDIR4Z17dmwzQ_WM2rlZjvB3Uh5NJ7EQmI"
        }

        _uiState.value = _uiState.value.copy(apiKey = defaultKey)

        addLog("System", "SOJIB AI চালু হয়েছে। আরুষি (Arushi) কথা বলার জন্য সম্পূর্ণ প্রস্তুত।")

        // Collect Admin Config & Premium State
        viewModelScope.launch {
            adminRepo.configFlow.collect { cfg ->
                _uiState.value = _uiState.value.copy(adminConfig = cfg)
            }
        }
        viewModelScope.launch {
            adminRepo.isUserPremium.collect { isPrem ->
                _uiState.value = _uiState.value.copy(isUserPremium = isPrem)
            }
        }
        viewModelScope.launch {
            adminRepo.userMessageCount.collect { count ->
                _uiState.value = _uiState.value.copy(userMessageCount = count)
            }
        }
        viewModelScope.launch {
            adminRepo.submissions.collect { list ->
                _uiState.value = _uiState.value.copy(paymentSubmissions = list)
            }
        }

        // Voice Engine State listener
        viewModelScope.launch {
            voiceEngine.engineState.collect { state ->
                when (state) {
                    is VoiceEngineState.Idle -> {
                        _uiState.value = _uiState.value.copy(
                            liveState = if (_uiState.value.isVoiceActive) LiveServiceState.Listening else LiveServiceState.Disconnected,
                            voiceStatusText = if (_uiState.value.isVoiceActive) "আরুষি শুনছে..." else "আরুষির সাথে কথা বলুন"
                        )
                    }
                    is VoiceEngineState.Listening -> {
                        _uiState.value = _uiState.value.copy(
                            liveState = LiveServiceState.Listening,
                            voiceStatusText = if (state.partialText.isNotBlank()) state.partialText else "শুনছি, বলুন..."
                        )
                    }
                    is VoiceEngineState.Thinking -> {
                        _uiState.value = _uiState.value.copy(
                            liveState = LiveServiceState.Connected,
                            voiceStatusText = "আরুষি ভাবছে..."
                        )
                    }
                    is VoiceEngineState.Speaking -> {
                        _uiState.value = _uiState.value.copy(
                            liveState = LiveServiceState.Speaking,
                            voiceStatusText = "আরুষি কথা বলছে..."
                        )
                    }
                    is VoiceEngineState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            liveState = LiveServiceState.Error(state.message),
                            voiceStatusText = state.message
                        )
                    }
                }
            }
        }

        // Voice Amplitude listener
        viewModelScope.launch {
            voiceEngine.amplitude.collect { amp ->
                if (_uiState.value.liveState is LiveServiceState.Listening || _uiState.value.liveState is LiveServiceState.Speaking) {
                    _uiState.value = _uiState.value.copy(amplitude = amp)
                }
            }
        }

        // Handle recognized user speech
        voiceEngine.onCommandResult = { userInput, _ ->
            executeUserVoiceCommand(userInput)
        }

        // WebSocket Live Service Fallback listener
        viewModelScope.launch {
            liveService.state.collect { state ->
                if (!_uiState.value.isVoiceActive) {
                    _uiState.value = _uiState.value.copy(liveState = state)
                }
            }
        }

        viewModelScope.launch {
            liveService.toolCallFlow.collect { toolCall ->
                handleToolCall(toolCall)
            }
        }

        // Listen to VoiceAssistantForegroundService state
        viewModelScope.launch {
            com.example.service.VoiceAssistantForegroundService.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isBackgroundServiceRunning = running)
            }
        }

        viewModelScope.launch {
            com.example.service.VoiceAssistantForegroundService.statusText.collect { status ->
                if (_uiState.value.isBackgroundServiceRunning) {
                    _uiState.value = _uiState.value.copy(voiceStatusText = status)
                }
            }
        }

        viewModelScope.launch {
            com.example.service.VoiceAssistantForegroundService.amplitude.collect { amp ->
                if (_uiState.value.isBackgroundServiceRunning) {
                    _uiState.value = _uiState.value.copy(amplitude = amp)
                }
            }
        }

        checkBatteryOptimization()
    }

    fun checkBatteryOptimization() {
        try {
            val pm = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val isIgnored = pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: false
            _uiState.value = _uiState.value.copy(isBatteryOptimizationIgnored = isIgnored)
        } catch (e: Exception) {
            // Ignored safely
        }
    }

    fun toggleBackgroundService(enable: Boolean) {
        val app = getApplication<Application>()
        if (enable) {
            com.example.service.VoiceAssistantForegroundService.startService(app)
            addLog("System", "সবসময় ব্যাকগ্রাউন্ডে সচল মোড চালু হয়েছে। স্ক্রিন অফ থাকলেও 'হে আরুষি' বা 'Hey Sojib' শুনবে।")
        } else {
            com.example.service.VoiceAssistantForegroundService.stopService(app)
            addLog("System", "ব্যাকগ্রাউন্ড মোড বন্ধ করা হয়েছে।")
        }
    }

    fun setWakeSensitivity(level: com.example.service.WakeSensitivity) {
        _uiState.value = _uiState.value.copy(wakeSensitivity = level)
        addLog("System", "ওয়েক-ওয়ার্ড সংবেদনশীলতা সেট করা হয়েছে: ${level.label}")
    }

    fun openBatterySettings(context: android.content.Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                addLog("System", "ব্যাটারি অপ্টিমাইজেশন সেটিংস ওপেন করা যায়নি: ${ex.message}")
            }
        }
    }

    fun updateApiKey(newKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = newKey.trim())
    }

    fun toggleMute() {
        val newMute = !_uiState.value.isMuted
        _uiState.value = _uiState.value.copy(isMuted = newMute)
        if (newMute) {
            voiceEngine.stopListening()
            voiceEngine.stopSpeaking()
        }
        liveService.toggleMute(newMute)
        addLog("System", if (newMute) "মাইক্রোফোন মিউট করা হয়েছে" else "মাইক্রোফোন আনমিউট করা হয়েছে")
    }

    fun toggleDirectCall(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDirectCallEnabled = enabled)
    }

    fun showAdminDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAdminDialog = show)
    }

    fun showSubscriptionDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSubscriptionDialog = show)
    }

    fun saveAdminConfig(config: AdminConfig) {
        adminRepo.saveConfig(config)
        addLog("System", "এডমিন সেটিংস আপডেট হয়েছে (বিকাশ, নগদ, রকেট ও প্যাকেজ পলিসি)।")
    }

    fun submitPayment(method: String, trxId: String, amount: Int) {
        val success = adminRepo.submitTrxId(method, trxId, amount)
        if (success) {
            addLog("System", "পেমেন্ট TrxID ($trxId) জমা হয়েছে। এডমিন ভেরিফাই করলে প্রিমিয়াম চালু হবে।")
        }
    }

    /**
     * Start Real Interactive Voice Session (আরুষির সাথে সরাসরি কথা বলা ও শোনা)
     */
    fun startVoiceSession() {
        val canProceed = adminRepo.incrementMessageCount()
        if (!canProceed) {
            _uiState.value = _uiState.value.copy(showSubscriptionDialog = true)
            addLog("System", "ফ্রি ট্রায়াল সীমা শেষ। আরুষির সাথে কথা বলতে প্রিমিয়াম সাবস্ক্রিপশন প্রয়োজন।", isError = true)
            return
        }

        _uiState.value = _uiState.value.copy(isVoiceActive = true)

        // Greet and immediately start listening
        val greeting = "আসসালামু আলাইকুম! আমি আরুষি। বলুন, আপনার জন্য কী করতে পারি?"
        addLog("Arushi", greeting)
        voiceEngine.speak(greeting)

        viewModelScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(3500)
            if (_uiState.value.isVoiceActive && !_uiState.value.isMuted) {
                voiceEngine.startListening()
            }
        }
    }

    fun stopVoiceSession() {
        _uiState.value = _uiState.value.copy(isVoiceActive = false)
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        liveService.disconnect()
        addLog("System", "ভয়েস সেশন সমাপ্ত হয়েছে।")
    }

    fun connectLiveSession() {
        startVoiceSession()
    }

    fun disconnectLiveSession() {
        stopVoiceSession()
    }

    fun executeQuickCommand(command: String) {
        executeUserVoiceCommand(command)
    }

    /**
     * Executes voice commands and speaks response aloud
     */
    fun executeUserVoiceCommand(commandText: String) {
        if (commandText.isBlank()) return
        addLog("User", commandText)
        val clean = commandText.lowercase()

        viewModelScope.launch {
            when {
                clean.contains("whatsapp") || clean.contains("হোয়াটসঅ্যাপ") -> {
                    val res = actionBridge.openWhatsApp()
                    val reply = "হোয়াটসঅ্যাপ খুলে দেওয়া হয়েছে।"
                    addLog("Action", "WhatsApp: ${res.message}")
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                }
                clean.contains("youtube") || clean.contains("ইউটিউব") -> {
                    val res = actionBridge.openApp("youtube")
                    val reply = "ইউটিউব ওপেন করা হচ্ছে।"
                    addLog("Action", "YouTube: ${res.message}")
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                }
                clean.contains("instagram") || clean.contains("ইন্সটাগ্রাম") -> {
                    val res = actionBridge.openApp("instagram")
                    val reply = "ইন্সটাগ্রাম খুলে দেওয়া হয়েছে।"
                    addLog("Action", "Instagram: ${res.message}")
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                }
                clean.contains("chrome") || clean.contains("ক্রোম") -> {
                    val res = actionBridge.openApp("chrome")
                    val reply = "ক্রোম ব্রাউজার ওপেন করছি।"
                    addLog("Action", "Chrome: ${res.message}")
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                }
                clean.contains("settings") || clean.contains("setting") || clean.contains("সেটিংস") -> {
                    val res = actionBridge.openApp("settings")
                    val reply = "ডিভাইস সেটিংস ওপেন করা হয়েছে।"
                    addLog("Action", "Settings: ${res.message}")
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                }
                clean.contains("টর্চ জ্বালাও") || clean.contains("টর্চ অন") || clean.contains("ফ্ল্যাশলাইট অন") || clean.contains("torch on") -> {
                    val res = actionBridge.toggleTorch(true)
                    val reply = "ফোনের টর্চলাইট জ্বালিয়ে দিয়েছি।"
                    addLog("Action", res.message)
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                }
                clean.contains("টর্চ বন্ধ") || clean.contains("টর্চ অফ") || clean.contains("ফ্ল্যাশলাইট অফ") || clean.contains("torch off") -> {
                    val res = actionBridge.toggleTorch(false)
                    val reply = "টর্চলাইট বন্ধ করে দিয়েছি।"
                    addLog("Action", res.message)
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                }
                clean.contains("ব্যাটারি") || clean.contains("চার্জ") || clean.contains("battery") -> {
                    val res = actionBridge.getBatteryInfo()
                    addLog("Arushi", res.message)
                    voiceEngine.speak(res.message)
                }
                clean.contains("নামাজের সময়") || clean.contains("নামাজ") || clean.contains("prayer") -> {
                    val res = actionBridge.getPrayerTimes()
                    val spokenPrayer = "আজকের ফজর চারটা তেতাল্লিশ, যোহর এগারটা ছেচল্লিশ, আসর তিনটা ছাপ্পান্ন, মাগরিব পাঁচটা চব্বিশ এবং এশা ছয়টা চল্লিশ।"
                    addLog("Arushi", res.message)
                    voiceEngine.speak(spokenPrayer)
                }
                clean.contains("৯৯৯") || clean.contains("999") || clean.contains("জরুরি") -> {
                    val reply = "জাতীয় জরুরি সেবা ৯৯৯ এ কল দিচ্ছি।"
                    addLog("Arushi", reply)
                    voiceEngine.speak(reply)
                    confirmAndMakeCall("999") { success, msg ->
                        addLog("Action", "জরুরি সেবা ৯৯৯ কল: $msg")
                    }
                }
                clean.startsWith("call ") || clean.contains("ko call karo") || clean.contains("কল করো") || clean.contains("ফোন করো") || clean.contains("ফোন দাও") -> {
                    val target = commandText.replace(Regex("(?i)call|ko call karo|phone lagao|কল করো|ফোন করো|ফোন দাও"), "").trim()
                    if (target.matches(Regex("[0-9+ ]+"))) {
                        val reply = "$target নম্বরে কল দেওয়া হচ্ছে।"
                        addLog("Arushi", reply)
                        voiceEngine.speak(reply)
                        confirmAndMakeCall(target) { success, msg ->
                            addLog("Action", "Call $target: $msg")
                        }
                    } else {
                        val searchResult = actionBridge.callContact(target)
                        when (searchResult) {
                            is ContactSearchResult.SingleMatch -> {
                                val reply = "${searchResult.contact.name} কে ফোন করছি।"
                                addLog("Arushi", reply)
                                voiceEngine.speak(reply)
                                confirmAndMakeCall(searchResult.contact.phoneNumber) { success, msg ->
                                    addLog("Action", "কল ${searchResult.contact.name}: $msg")
                                }
                            }
                            is ContactSearchResult.MultipleMatches -> {
                                val reply = "'$target' নামের একাধিক নম্বর পাওয়া গেছে। কাকে কল করবো বেছে নিন।"
                                addLog("Arushi", reply)
                                voiceEngine.speak(reply)
                                _uiState.value = _uiState.value.copy(
                                    multipleContactsDialog = MultipleContactsDialogState(
                                        isVisible = true,
                                        query = target,
                                        contacts = searchResult.contacts,
                                        onSelect = { selectedContact ->
                                            _uiState.value = _uiState.value.copy(
                                                multipleContactsDialog = MultipleContactsDialogState(isVisible = false)
                                            )
                                            confirmAndMakeCall(selectedContact.phoneNumber) { success, msg ->
                                                addLog("Action", "সিলেক্ট করা হয়েছে ${selectedContact.name}: $msg")
                                            }
                                        }
                                    )
                                )
                            }
                            else -> {
                                val reply = "আপনার ফোনে '$target' নামে কোনো কন্টাক্ট পাওয়া যায়নি।"
                                addLog("Arushi", reply)
                                voiceEngine.speak(reply)
                            }
                        }
                    }
                }
                else -> {
                    // Ask Gemini AI for sweet Bangladeshi girl conversation
                    val aiReply = voiceEngine.queryGeminiAi(_uiState.value.apiKey, commandText)
                    addLog("Arushi", aiReply)
                    voiceEngine.speak(aiReply)
                }
            }

            // After speaking finishes, resume gentle listening if voice is still active
            kotlinx.coroutines.delay(4000)
            if (_uiState.value.isVoiceActive && !_uiState.value.isMuted) {
                voiceEngine.startListening()
            }
        }
    }

    /**
     * Executes tool calls requested by Gemini Live AI
     */
    private fun handleToolCall(toolCall: ToolCallRequest) {
        val resultJson = JSONObject()

        when (toolCall.name) {
            "openWhatsApp" -> {
                val result = actionBridge.openWhatsApp()
                resultJson.put("success", result.success)
                resultJson.put("message", result.message)
                addLog("Action", "WhatsApp: ${result.message}")
            }

            "openApp" -> {
                val appName = toolCall.argsJson.optString("appName", "app")
                val result = actionBridge.openApp(appName)
                resultJson.put("success", result.success)
                resultJson.put("message", result.message)
                addLog("Action", "Open App ($appName): ${result.message}")
            }

            "openUrl" -> {
                val url = toolCall.argsJson.optString("url", "https://google.com")
                val result = actionBridge.openUrl(url)
                resultJson.put("success", result.success)
                resultJson.put("message", result.message)
                addLog("Action", "Open Link ($url): ${result.message}")
            }

            "makeCall" -> {
                val phone = toolCall.argsJson.optString("phoneNumber", "")
                confirmAndMakeCall(phone) { success, msg ->
                    resultJson.put("success", success)
                    resultJson.put("message", msg)
                    liveService.sendToolResponse(toolCall.callId, resultJson)
                }
                return
            }

            "callContact" -> {
                val contactName = toolCall.argsJson.optString("contactName", "")
                val searchResult = actionBridge.callContact(contactName)

                when (searchResult) {
                    is ContactSearchResult.SingleMatch -> {
                        val contact = searchResult.contact
                        addLog("Action", "কন্টাক্ট পাওয়া গেছে '${contact.name}' (${contact.phoneNumber})। কল শুরু হচ্ছে...")
                        confirmAndMakeCall(contact.phoneNumber) { success, msg ->
                            resultJson.put("success", success)
                            resultJson.put("message", "Calling ${contact.name} (${contact.phoneNumber}): $msg")
                            liveService.sendToolResponse(toolCall.callId, resultJson)
                        }
                        return
                    }

                    is ContactSearchResult.MultipleMatches -> {
                        addLog("Action", "একাধিক কন্টাক্ট পাওয়া গেছে '$contactName'। সিলেক্ট করুন।")
                        _uiState.value = _uiState.value.copy(
                            multipleContactsDialog = MultipleContactsDialogState(
                                isVisible = true,
                                query = contactName,
                                contacts = searchResult.contacts,
                                onSelect = { selectedContact ->
                                    _uiState.value = _uiState.value.copy(
                                        multipleContactsDialog = MultipleContactsDialogState(isVisible = false)
                                    )
                                    confirmAndMakeCall(selectedContact.phoneNumber) { success, msg ->
                                        resultJson.put("success", success)
                                        resultJson.put("message", "Selected ${selectedContact.name} (${selectedContact.phoneNumber}): $msg")
                                        liveService.sendToolResponse(toolCall.callId, resultJson)
                                    }
                                }
                            )
                        )
                        return
                    }

                    is ContactSearchResult.NotFound -> {
                        resultJson.put("success", false)
                        resultJson.put("message", "No contact found matching '$contactName' in device contacts.")
                        addLog("Action", "'$contactName' নামে কোনো কন্টাক্ট পাওয়া যায়নি।", isError = true)
                    }

                    is ContactSearchResult.Error -> {
                        resultJson.put("success", false)
                        resultJson.put("message", searchResult.message)
                        addLog("Action", "কন্টাক্ট এরর: ${searchResult.message}", isError = true)
                    }
                }
            }

            "toggleTorch" -> {
                val enable = toolCall.argsJson.optBoolean("enable", true)
                val result = actionBridge.toggleTorch(enable)
                resultJson.put("success", result.success)
                resultJson.put("message", result.message)
                addLog("Action", "টর্চলাইট: ${result.message}")
            }

            "getBatteryInfo" -> {
                val result = actionBridge.getBatteryInfo()
                resultJson.put("success", result.success)
                resultJson.put("message", result.message)
                addLog("Action", "ব্যাটারি: ${result.message}")
            }

            "getPrayerTimes" -> {
                val result = actionBridge.getPrayerTimes()
                resultJson.put("success", result.success)
                resultJson.put("message", result.message)
                addLog("Action", "নামাজের সময়: ${result.message}")
            }

            else -> {
                resultJson.put("success", false)
                resultJson.put("message", "Unknown tool function: ${toolCall.name}")
            }
        }

        // Send response back to Gemini Live
        liveService.sendToolResponse(toolCall.callId, resultJson)
    }

    private fun confirmAndMakeCall(phone: String, onCompleted: (Boolean, String) -> Unit) {
        val directAllowed = _uiState.value.isDirectCallEnabled

        if (!directAllowed) {
            _uiState.value = _uiState.value.copy(
                confirmationDialog = ConfirmationDialogState(
                    isVisible = true,
                    title = "ফোন কল নিশ্চিত করুন",
                    message = "আপনি কি $phone নম্বরে কল করতে চান?",
                    confirmText = "কল করুন",
                    cancelText = "বাতিল",
                    onConfirm = {
                        _uiState.value = _uiState.value.copy(confirmationDialog = ConfirmationDialogState(isVisible = false))
                        val res = actionBridge.makeCall(phone, directCallPermitted = false)
                        onCompleted(res.success, res.message)
                    },
                    onCancel = {
                        _uiState.value = _uiState.value.copy(confirmationDialog = ConfirmationDialogState(isVisible = false))
                        onCompleted(false, "User cancelled phone call.")
                    }
                )
            )
        } else {
            val res = actionBridge.makeCall(phone, directCallPermitted = true)
            onCompleted(res.success, res.message)
        }
    }

    fun dismissConfirmationDialog() {
        _uiState.value = _uiState.value.copy(confirmationDialog = ConfirmationDialogState(isVisible = false))
    }

    fun dismissMultipleContactsDialog() {
        _uiState.value = _uiState.value.copy(multipleContactsDialog = MultipleContactsDialogState(isVisible = false))
    }

    private fun addLog(sender: String, message: String, isError: Boolean = false) {
        val timeString = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newEntry = LogEntry(timestamp = timeString, sender = sender, message = message, isError = isError)
        _uiState.value = _uiState.value.copy(logs = _uiState.value.logs + newEntry)
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.destroy()
        liveService.disconnect()
    }
}
