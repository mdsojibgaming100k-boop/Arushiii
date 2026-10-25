package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.LiveServiceState
import com.example.ui.MainViewModel
import com.example.ui.components.CallConfirmationModal
import com.example.ui.components.ConversationLogView
import com.example.ui.components.MultipleContactsModal
import com.example.ui.components.QuickCommandChips
import com.example.ui.components.SettingsModal
import com.example.ui.components.SubscriptionModal
import com.example.ui.components.VoiceOrbVisualizer
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.CreamBorder
import com.example.ui.theme.CreamSurface
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SojibAiApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SojibAiApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var textCommandInput by remember { mutableStateOf("") }

    // Runtime Permission Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] ?: false

        if (micGranted) {
            viewModel.connectLiveSession()
        }
    }

    // Check permissions on start
    fun requestPermissionsAndConnect() {
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        if (!hasMic || !hasContacts || !hasCall) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE
                )
            )
        } else {
            viewModel.connectLiveSession()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.sojib_ai_cream_icon_1792906435459),
                            contentDescription = "SOJIB AI",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, GoldPrimary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SOJIB AI",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = TextDarkPrimary,
                                        fontSize = 18.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (uiState.isUserPremium) GoldPrimary.copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.isUserPremium) GoldPrimary else CreamBorder),
                                    modifier = Modifier.clickable { viewModel.showSubscriptionDialog(true) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Plan",
                                            tint = if (uiState.isUserPremium) GoldPrimary else Color.Gray,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = if (uiState.isUserPremium) "PRO" else "FREE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.isUserPremium) GoldPrimary else Color.Gray
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "আরুষি — বাংলাদেশি ভয়েস সহকারী",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier.testTag("mute_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute Toggle",
                            tint = if (uiState.isMuted) Color(0xFFDC2626) else GoldPrimary
                        )
                    }

                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextDarkSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamSurface)
            )
        },
        containerColor = CreamBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CreamBackground),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Voice Orb Centerpiece in Cream Gold Theme
            VoiceOrbVisualizer(
                state = uiState.liveState,
                amplitude = uiState.amplitude,
                isMuted = uiState.isMuted,
                statusText = uiState.voiceStatusText,
                onOrbClick = {
                    if (uiState.liveState is LiveServiceState.Disconnected) {
                        requestPermissionsAndConnect()
                    } else {
                        viewModel.disconnectLiveSession()
                    }
                },
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Voice Action Chips
            QuickCommandChips(
                onCommandSelected = { command ->
                    viewModel.executeQuickCommand(command)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Real-Time Transcript & Action Log in Pure White & Cream Border Container
            ConversationLogView(
                logs = uiState.logs,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Action Controls Bar
            Surface(
                color = CreamSurface,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth()
                ) {
                    // Manual Command Input Field
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = textCommandInput,
                            onValueChange = { textCommandInput = it },
                            placeholder = {
                                Text(
                                    "বাংলায় কথা লিখুন (যেমন: হোয়াটসঅ্যাপ খোলো)...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (textCommandInput.isNotBlank()) {
                                    viewModel.executeQuickCommand(textCommandInput)
                                    textCommandInput = ""
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextDarkPrimary,
                                unfocusedTextColor = TextDarkPrimary,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = CreamBorder,
                                focusedContainerColor = CreamBackground,
                                unfocusedContainerColor = CreamBackground
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("command_input_field")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (textCommandInput.isNotBlank()) {
                                    viewModel.executeQuickCommand(textCommandInput)
                                    textCommandInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(GoldPrimary, CircleShape)
                                .testTag("send_command_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send Command",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Connect / Disconnect Live Session Button
                    Button(
                        onClick = {
                            if (uiState.liveState is LiveServiceState.Disconnected) {
                                requestPermissionsAndConnect()
                            } else {
                                viewModel.disconnectLiveSession()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.liveState is LiveServiceState.Disconnected)
                                GoldPrimary else Color(0xFFDC2626)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("connect_live_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Live Session",
                            tint = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = if (uiState.liveState is LiveServiceState.Disconnected)
                                "আরুষির সাথে সরাসরি কথা বলুন (SOJIB AI)" else "কল সমাপ্ত করুন",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }

    // Modal Overlays
    CallConfirmationModal(
        state = uiState.confirmationDialog,
        onDismiss = { viewModel.dismissConfirmationDialog() }
    )

    MultipleContactsModal(
        state = uiState.multipleContactsDialog,
        onDismiss = { viewModel.dismissMultipleContactsDialog() }
    )


    // User Subscription / Payment Modal (bKash, Nagad, Rocket)
    if (uiState.showSubscriptionDialog) {
        SubscriptionModal(
            config = uiState.adminConfig,
            isUserPremium = uiState.isUserPremium,
            onPaymentSubmit = { method, trxId, amount ->
                viewModel.submitPayment(method, trxId, amount)
            },
            onDismiss = { viewModel.showSubscriptionDialog(false) }
        )
    }

    if (showSettings) {
        SettingsModal(
            currentApiKey = uiState.apiKey,
            isDirectCallEnabled = uiState.isDirectCallEnabled,
            onSaveApiKey = { newKey -> viewModel.updateApiKey(newKey) },
            onToggleDirectCall = { enabled -> viewModel.toggleDirectCall(enabled) },
            onDismiss = { showSettings = false }
        )
    }
}
