package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickCommandChips(
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickCommands = listOf(
        "🔦 টর্চ জ্বালাও",
        "💡 টর্চ বন্ধ করো",
        "🔋 ব্যাটারি চার্জ কত?",
        "🕌 আজকের নামাজের সময়",
        "🚨 ৯৯৯ এ কল দাও",
        "আরুষি, আসসালামু আলাইকুম",
        "হোয়াটসঅ্যাপ খোলো",
        "ইউটিউব খোলো",
        "আম্মুকে ফোন করো",
        "বাংলায় কথা বলো",
        "Open WhatsApp",
        "Open YouTube",
        "Call Mom",
        "Open Settings"
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_command_chips_row")
    ) {
        items(quickCommands) { command ->
            AssistChip(
                onClick = { onCommandSelected(command) },
                label = {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = Color(0xFF1E293B)
                    )
                },
                shape = RoundedCornerShape(20.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFFFFFFFF),
                    labelColor = Color(0xFFD97706)
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = Color(0xFFD97706).copy(alpha = 0.35f)
                )
            )
        }
    }
}
