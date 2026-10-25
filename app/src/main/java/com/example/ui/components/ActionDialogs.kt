package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ConfirmationDialogState
import com.example.ui.MultipleContactsDialogState

@Composable
fun CallConfirmationModal(
    state: ConfirmationDialogState,
    onDismiss: () -> Unit
) {
    if (state.isVisible) {
        AlertDialog(
            onDismissRequest = {
                state.onCancel()
                onDismiss()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = Color(0xFFD97706),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                }
            },
            text = {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        state.onConfirm()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier.testTag("confirm_call_button")
                ) {
                    Text(state.confirmText, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.onCancel()
                        onDismiss()
                    }
                ) {
                    Text(state.cancelText, color = Color(0xFF64748B))
                }
            },
            containerColor = Color(0xFFFFFDF9),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun MultipleContactsModal(
    state: MultipleContactsDialogState,
    onDismiss: () -> Unit
) {
    if (state.isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Contacts",
                        tint = Color(0xFFC2410C),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "একাধিক কন্টাক্ট পাওয়া গেছে",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF0F172A)
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "'${state.query}' নামের ${state.contacts.size} টি নম্বর পাওয়া গেছে। কাকে কল করতে চান?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF334155),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        items(state.contacts) { contact ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF9F5)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE5DA)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        state.onSelect(contact)
                                        onDismiss()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("বাতিল", color = Color(0xFF64748B))
                }
            },
            containerColor = Color(0xFFFFFDF9),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun SettingsModal(
    currentApiKey: String,
    isDirectCallEnabled: Boolean,
    onSaveApiKey: (String) -> Unit,
    onToggleDirectCall: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var keyInput by remember { mutableStateOf(currentApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Settings",
                    tint = Color(0xFFD97706),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "SOJIB AI — আরুষি সেটিংস",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF0F172A)
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "গুগল জেমিনি এপিআই কি (Gemini API Key)",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFD97706)
                )
                Text(
                    text = "সিক্রেটস প্যানেল থেকে স্বয়ংক্রিয়ভাবে বা নিচে কাস্টম কি সেট করুন:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("AIzaSy...", color = Color.LightGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFFD97706),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_text_field")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ডাইরেক্ট কল মোড (Direct Call Mode)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "কোন পপআপ ছাড়া সরাসরি কল করবে",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }

                    Switch(
                        checked = isDirectCallEnabled,
                        onCheckedChange = onToggleDirectCall,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFD97706)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF9F5)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEBE5DA))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SOJIB AI — বাংলাদেশি হিউম্যান ভয়েস ইঞ্জিন",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFC2410C)
                        )
                        Text(
                            text = "• সহকারী: আরুষি (Arushi - Bangladeshi Girl)\n• ইঞ্জিন: Gemini 2.0 Live Realtime Multimodal\n• ভাষা: মিষ্টি বাংলা, ইংলিশ, হিংলিশ স্বয়ংক্রিয় সনাক্তকরণ\n• অডিও: 16kHz PCM মাইক্রোফোন, 24kHz PCM ভয়েস",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveApiKey(keyInput)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) {
                Text("সেভ করুন", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = Color(0xFF64748B))
            }
        },
        containerColor = Color(0xFFFFFDF9),
        shape = RoundedCornerShape(20.dp)
    )
}
