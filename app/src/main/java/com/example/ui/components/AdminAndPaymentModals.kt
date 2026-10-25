package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AdminConfig
import com.example.data.PaymentSubmission
import com.example.ui.theme.BkashPink
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.CreamBorder
import com.example.ui.theme.CreamSurface
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NagadOrange
import com.example.ui.theme.RocketPurple
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextDarkPrimary
import com.example.ui.theme.TextDarkSecondary

@Composable
fun AdminControlDialog(
    config: AdminConfig,
    submissions: List<PaymentSubmission>,
    onSaveConfig: (AdminConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // State holders
    var isPremiumReq by remember { mutableStateOf(config.isPremiumRequired) }
    var monthlyPrice by remember { mutableStateOf(config.monthlyPriceBdt.toString()) }
    var yearlyPrice by remember { mutableStateOf(config.yearlyPriceBdt.toString()) }
    var minAge by remember { mutableStateOf(config.minAgeRequirement.toString()) }
    var freeLimit by remember { mutableStateOf(config.freeMessageLimit.toString()) }

    var isBkashOn by remember { mutableStateOf(config.isBkashEnabled) }
    var bkashNum by remember { mutableStateOf(config.bkashNumber) }

    var isNagadOn by remember { mutableStateOf(config.isNagadEnabled) }
    var nagadNum by remember { mutableStateOf(config.nagadNumber) }

    var isRocketOn by remember { mutableStateOf(config.isRocketEnabled) }
    var rocketNum by remember { mutableStateOf(config.rocketNumber) }

    val tabs = listOf("সিস্টেম ও প্যাকেজ", "বিকাশ/নগদ/রকেট", "পেমেন্ট হিস্ট্রি", "Firebase")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Admin",
                    tint = GoldPrimary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "SOJIB AI — এডমিন কন্ট্রোল সেন্টার",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextDarkPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CreamBackground,
                    contentColor = GoldPrimary,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) GoldPrimary else TextDarkSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.height(340.dp)) {
                    when (selectedTab) {
                        0 -> { // সিস্টেম ও প্যাকেজ
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CreamSurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "প্রিমিয়াম সিস্টেম বাধ্যতামূলক",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = TextDarkPrimary
                                                )
                                                Text(
                                                    text = if (isPremiumReq) "অন: ফ্রি লিমিট শেষে পেমেন্ট লাগবে" else "অফ: সবার জন্য সম্পূর্ণ ফ্রি",
                                                    fontSize = 11.sp,
                                                    color = TextDarkSecondary
                                                )
                                            }
                                            Switch(
                                                checked = isPremiumReq,
                                                onCheckedChange = { isPremiumReq = it },
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GoldPrimary)
                                            )
                                        }
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = monthlyPrice,
                                        onValueChange = { monthlyPrice = it },
                                        label = { Text("মাসিক প্যাকেজ মূল্য (৳)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = yearlyPrice,
                                        onValueChange = { yearlyPrice = it },
                                        label = { Text("বাৎসরিক প্যাকেজ মূল্য (৳)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = minAge,
                                        onValueChange = { minAge = it },
                                        label = { Text("নূন্যতম বয়স নির্ধারণ (যেমন: ১৮+ বা ০)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = freeLimit,
                                        onValueChange = { freeLimit = it },
                                        label = { Text("ফ্রি ট্রায়াল মেসেজ লিমিট") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        1 -> { // বিকাশ, নগদ, রকেট পেমেন্ট সেটিংস
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // bKash Card
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CreamSurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BkashPink.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("বিকাশ (bKash) পেমেন্ট", fontWeight = FontWeight.Bold, color = BkashPink)
                                                Switch(
                                                    checked = isBkashOn,
                                                    onCheckedChange = { isBkashOn = it },
                                                    colors = SwitchDefaults.colors(checkedTrackColor = BkashPink)
                                                )
                                            }
                                            OutlinedTextField(
                                                value = bkashNum,
                                                onValueChange = { bkashNum = it },
                                                label = { Text("বিকাশ পার্সোনাল নম্বর") },
                                                singleLine = true,
                                                enabled = isBkashOn,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                // Nagad Card
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CreamSurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NagadOrange.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("নগদ (Nagad) পেমেন্ট", fontWeight = FontWeight.Bold, color = NagadOrange)
                                                Switch(
                                                    checked = isNagadOn,
                                                    onCheckedChange = { isNagadOn = it },
                                                    colors = SwitchDefaults.colors(checkedTrackColor = NagadOrange)
                                                )
                                            }
                                            OutlinedTextField(
                                                value = nagadNum,
                                                onValueChange = { nagadNum = it },
                                                label = { Text("নগদ পার্সোনাল নম্বর") },
                                                singleLine = true,
                                                enabled = isNagadOn,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }

                                // Rocket Card
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CreamSurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, RocketPurple.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("রকেট (Rocket) পেমেন্ট", fontWeight = FontWeight.Bold, color = RocketPurple)
                                                Switch(
                                                    checked = isRocketOn,
                                                    onCheckedChange = { isRocketOn = it },
                                                    colors = SwitchDefaults.colors(checkedTrackColor = RocketPurple)
                                                )
                                            }
                                            OutlinedTextField(
                                                value = rocketNum,
                                                onValueChange = { rocketNum = it },
                                                label = { Text("রকেট পার্সোনাল নম্বর") },
                                                singleLine = true,
                                                enabled = isRocketOn,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> { // পেমেন্ট হিস্ট্রি
                            if (submissions.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("এখনও কোনো পেমেন্ট সাবমিশন আসেনি।", color = TextDarkSecondary, fontSize = 13.sp)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(submissions) { sub ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = CreamSurface),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CreamBorder),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text("TrxID: ${sub.trxId}", fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                                                    Text("গেটওয়ে: ${sub.method} • ৳${sub.amount}", fontSize = 12.sp, color = TextDarkSecondary)
                                                }
                                                Surface(
                                                    color = SuccessGreen.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("অনুমোদিত", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> { // Firebase Info
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CreamSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text("Firebase কানেকশন স্ট্যাটাস", fontWeight = FontWeight.Bold, color = GoldPrimary)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("• Project ID: aisojib\n• Sender ID: 804686990856\n• Auth Domain: aisojib.firebaseapp.com\n• Storage: aisojib.firebasestorage.app\n• API Key: AIzaSyDIR4Z17dmwzQ_WM2rlZjvB3Uh5NJ7EQmI", fontSize = 12.sp, color = TextDarkSecondary)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("HTML ওয়েব অ্যাডমিন ড্যাশবোর্ড ফাইল `sojib_ai_admin_dashboard.html` অ্যাপ রুটে সেভ করা আছে। আপনি যেকোনো ব্রাউজারে এটি ওপেন করে রিমোটলি কন্ট্রোল করতে পারবেন।", fontSize = 11.sp, color = TextDarkSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = config.copy(
                        isPremiumRequired = isPremiumReq,
                        monthlyPriceBdt = monthlyPrice.toIntOrNull() ?: config.monthlyPriceBdt,
                        yearlyPriceBdt = yearlyPrice.toIntOrNull() ?: config.yearlyPriceBdt,
                        minAgeRequirement = minAge.toIntOrNull() ?: config.minAgeRequirement,
                        freeMessageLimit = freeLimit.toIntOrNull() ?: config.freeMessageLimit,
                        isBkashEnabled = isBkashOn,
                        bkashNumber = bkashNum,
                        isNagadEnabled = isNagadOn,
                        nagadNumber = nagadNum,
                        isRocketEnabled = isRocketOn,
                        rocketNumber = rocketNum
                    )
                    onSaveConfig(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
            ) {
                Text("সেভ ও আপডেট করুন", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = TextDarkSecondary)
            }
        },
        containerColor = CreamBackground,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun SubscriptionModal(
    config: AdminConfig,
    isUserPremium: Boolean,
    onPaymentSubmit: (method: String, trxId: String, amount: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedPlan by remember { mutableStateOf("monthly") } // "monthly" or "yearly"
    var selectedGateway by remember { mutableStateOf("bKash") }
    var trxIdInput by remember { mutableStateOf("") }
    var isSubmittedSuccess by remember { mutableStateOf(false) }

    val amount = if (selectedPlan == "monthly") config.monthlyPriceBdt else config.yearlyPriceBdt

    val currentNumber = when (selectedGateway) {
        "bKash" -> config.bkashNumber
        "Nagad" -> config.nagadNumber
        else -> config.rocketNumber
    }

    val gatewayColor = when (selectedGateway) {
        "bKash" -> BkashPink
        "Nagad" -> NagadOrange
        else -> RocketPurple
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = "Premium", tint = GoldPrimary, modifier = Modifier.padding(end = 8.dp))
                Text(
                    text = if (isUserPremium) "SOJIB AI প্রিমিয়াম সক্রিয়" else "SOJIB AI প্রিমিয়াম আনলক করুন",
                    fontWeight = FontWeight.Bold,
                    color = TextDarkPrimary,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            if (isSubmittedSuccess) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Pending", tint = GoldPrimary, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("পেমেন্ট TrxID সফলভাবে জমা হয়েছে!", fontWeight = FontWeight.Bold, color = TextDarkPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("আপনার পেমেন্ট বর্তমানে এডমিনের অনুমোদনের অপেক্ষায় রয়েছে (Pending)। এডমিন যাচাই করার পর আপনার একাউন্ট স্বয়ংক্রিয়ভাবে সক্রিয় হবে।", fontSize = 13.sp, color = TextDarkSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else if (isUserPremium) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = SuccessGreen, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("অভিনন্দন! আপনার প্রিমিয়াম সক্রিয় আছে!", fontWeight = FontWeight.Bold, color = TextDarkPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("এখন আপনি আরুষির সাথে আনলিমিটেড লাইভ কথা ও সব ডিভাইস কন্ট্রোল করতে পারবেন।", fontSize = 13.sp, color = TextDarkSecondary)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Plan Choice Chips
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (selectedPlan == "monthly") GoldPrimary.copy(alpha = 0.15f) else CreamSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedPlan == "monthly") GoldPrimary else CreamBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable { selectedPlan = "monthly" }
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("মাসিক প্ল্যান", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                Text("৳${config.monthlyPriceBdt}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GoldPrimary)
                                Text("৩০ দিন মেয়াদ", fontSize = 10.sp, color = TextDarkSecondary)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (selectedPlan == "yearly") GoldPrimary.copy(alpha = 0.15f) else CreamSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedPlan == "yearly") GoldPrimary else CreamBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).clickable { selectedPlan = "yearly" }
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("বাৎসরিক প্ল্যান", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDarkPrimary)
                                Text("৳${config.yearlyPriceBdt}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GoldPrimary)
                                Text("১২ মাস আনলিমিটেড", fontSize = 10.sp, color = TextDarkSecondary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("পেমেন্ট গেটওয়ে বেছে নিন:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDarkPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Gateway Selectors
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (config.isBkashEnabled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedGateway == "bKash") BkashPink else CreamSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BkashPink),
                                modifier = Modifier.weight(1f).clickable { selectedGateway = "bKash" }
                            ) {
                                Text(
                                    "বিকাশ",
                                    color = if (selectedGateway == "bKash") Color.White else BkashPink,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        if (config.isNagadEnabled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedGateway == "Nagad") NagadOrange else CreamSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, NagadOrange),
                                modifier = Modifier.weight(1f).clickable { selectedGateway = "Nagad" }
                            ) {
                                Text(
                                    "নগদ",
                                    color = if (selectedGateway == "Nagad") Color.White else NagadOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }

                        if (config.isRocketEnabled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedGateway == "Rocket") RocketPurple else CreamSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, RocketPurple),
                                modifier = Modifier.weight(1f).clickable { selectedGateway = "Rocket" }
                            ) {
                                Text(
                                    "রকেট",
                                    color = if (selectedGateway == "Rocket") Color.White else RocketPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Payment details card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CreamSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gatewayColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("১. $selectedGateway থেকে 'Send Money' করুন ৳$amount", fontSize = 12.sp, color = TextDarkPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("নম্বর: $currentNumber", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = gatewayColor)
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(currentNumber))
                                        Toast.makeText(context, "$selectedGateway নম্বর কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = gatewayColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = trxIdInput,
                        onValueChange = { trxIdInput = it },
                        placeholder = { Text("TrxID লিখুন (যেমন: 9J38S...)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = gatewayColor,
                            focusedTextColor = TextDarkPrimary,
                            unfocusedTextColor = TextDarkPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isSubmittedSuccess && !isUserPremium) {
                Button(
                    onClick = {
                        if (trxIdInput.isNotBlank()) {
                            onPaymentSubmit(selectedGateway, trxIdInput, amount)
                            isSubmittedSuccess = true
                        } else {
                            Toast.makeText(context, "দয়া করে TrxID লিখুন", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    modifier = Modifier.testTag("submit_payment_button")
                ) {
                    Text("পেমেন্ট সাবমিট করুন", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)) {
                    Text("ঠিক আছে", color = Color.White)
                }
            }
        },
        dismissButton = {
            if (!isSubmittedSuccess && !isUserPremium) {
                TextButton(onClick = onDismiss) {
                    Text("পরে করবো", color = TextDarkSecondary)
                }
            }
        },
        containerColor = CreamBackground,
        shape = RoundedCornerShape(20.dp)
    )
}
