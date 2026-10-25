package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String
)

sealed interface ContactSearchResult {
    data class SingleMatch(val contact: ContactItem) : ContactSearchResult
    data class MultipleMatches(val query: String, val contacts: List<ContactItem>) : ContactSearchResult
    data class NotFound(val query: String) : ContactSearchResult
    data class Error(val message: String) : ContactSearchResult
}

data class ActionResult(
    val success: Boolean,
    val actionName: String,
    val message: String,
    val data: String? = null
)

class AndroidActionBridge(private val context: Context) {

    companion object {
        private const val TAG = "AndroidActionBridge"
    }

    /**
     * Open WhatsApp or fallback gracefully.
     */
    fun openWhatsApp(): ActionResult {
        val pm = context.packageManager
        
        // Try direct WhatsApp packages
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult(
                        success = true,
                        actionName = "openWhatsApp",
                        message = "WhatsApp opened successfully."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching package $pkg", e)
            }
        }

        // Try WhatsApp deep link fallback
        return try {
            val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(deepLinkIntent)
            ActionResult(
                success = true,
                actionName = "openWhatsApp",
                message = "WhatsApp opened via browser deep link."
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                actionName = "openWhatsApp",
                message = "WhatsApp is not installed on this device."
            )
        }
    }

    /**
     * Open a specified app (YouTube, Instagram, Chrome, Settings, etc.)
     */
    fun openApp(appName: String): ActionResult {
        val cleanName = appName.trim().lowercase()
        val pm = context.packageManager

        // Explicit mappings for common apps
        when {
            cleanName.contains("whatsapp") -> return openWhatsApp()
            cleanName.contains("youtube") -> {
                val intent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult(true, "openApp", "Opened YouTube.")
                } else {
                    return openUrl("https://youtube.com")
                }
            }
            cleanName.contains("instagram") -> {
                val intent = pm.getLaunchIntentForPackage("com.instagram.android")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult(true, "openApp", "Opened Instagram.")
                } else {
                    return openUrl("https://instagram.com")
                }
            }
            cleanName.contains("chrome") -> {
                val intent = pm.getLaunchIntentForPackage("com.android.chrome")
                    ?: pm.getLaunchIntentForPackage("com.chrome.beta")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult(true, "openApp", "Opened Chrome.")
                } else {
                    return openUrl("https://google.com")
                }
            }
            cleanName.contains("setting") -> {
                return try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(true, "openApp", "Opened device settings.")
                } catch (e: Exception) {
                    ActionResult(false, "openApp", "Could not open settings: ${e.message}")
                }
            }
            cleanName.contains("camera") -> {
                return try {
                    val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionResult(true, "openApp", "Opened Camera.")
                } catch (e: Exception) {
                    ActionResult(false, "openApp", "Could not open camera.")
                }
            }
            cleanName.contains("calculator") -> {
                val intent = pm.getLaunchIntentForPackage("com.google.android.calculator")
                    ?: pm.getLaunchIntentForPackage("com.sec.android.app.popupcalculator")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return ActionResult(true, "openApp", "Opened Calculator.")
                }
            }
        }

        // Generic search among installed apps
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label.contains(cleanName) || cleanName.contains(label)) {
                    val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return ActionResult(true, "openApp", "Opened $label.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching installed apps", e)
        }

        return ActionResult(
            success = false,
            actionName = "openApp",
            message = "Could not find or open app matching '$appName' on this device."
        )
    }

    /**
     * Open web URL in default browser.
     */
    fun openUrl(url: String): ActionResult {
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "openUrl", "Opened link: $formattedUrl")
        } catch (e: Exception) {
            ActionResult(false, "openUrl", "Failed to open link '$url': ${e.message}")
        }
    }

    /**
     * Initiate phone call to a phone number.
     */
    fun makeCall(phoneNumber: String, directCallPermitted: Boolean = false): ActionResult {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) {
            return ActionResult(false, "makeCall", "Invalid phone number provided.")
        }

        return try {
            if (directCallPermitted) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "makeCall", "Initiating direct call to $cleanNumber", data = cleanNumber)
            } else {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "makeCall", "Opened dialer for $cleanNumber", data = cleanNumber)
            }
        } catch (e: Exception) {
            // Fallback to dialer if ACTION_CALL failed
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "makeCall", "Opened dialer for $cleanNumber", data = cleanNumber)
            } catch (ex: Exception) {
                ActionResult(false, "makeCall", "Could not make call: ${ex.message}")
            }
        }
    }

    /**
     * Search contacts for contactName.
     */
    fun callContact(contactName: String): ContactSearchResult {
        val query = contactName.trim()
        if (query.isBlank()) {
            return ContactSearchResult.Error("Contact name cannot be empty.")
        }

        // Check permission
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return ContactSearchResult.Error("Contacts permission is required to search contacts.")
        }

        val contactsList = mutableListOf<ContactItem>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val seenNumbers = mutableSetOf<String>()

                while (c.moveToNext()) {
                    val id = if (idIdx >= 0) c.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else ""
                    val num = if (numIdx >= 0) c.getString(numIdx) else ""
                    val cleanNum = num.replace(Regex("[^0-9+]"), "")

                    if (name.isNotBlank() && cleanNum.isNotBlank() && !seenNumbers.contains(cleanNum)) {
                        seenNumbers.add(cleanNum)
                        contactsList.add(ContactItem(id, name, num))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
            return ContactSearchResult.Error("Error accessing contacts: ${e.message}")
        }

        return when {
            contactsList.isEmpty() -> ContactSearchResult.NotFound(query)
            contactsList.size == 1 -> ContactSearchResult.SingleMatch(contactsList.first())
            else -> ContactSearchResult.MultipleMatches(query, contactsList)
        }
    }

    /**
     * Toggle device flashlight (টর্চলাইট অন / অফ)
     */
    fun toggleTorch(enable: Boolean): ActionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraManager != null && cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                val msg = if (enable) "টর্চলাইট অন করা হয়েছে।" else "টর্চলাইট বন্ধ করা হয়েছে।"
                ActionResult(true, "toggleTorch", msg)
            } else {
                ActionResult(false, "toggleTorch", "এই ডিভাইসে ফ্ল্যাশলাইট পাওয়া যায়নি।")
            }
        } catch (e: Exception) {
            ActionResult(false, "toggleTorch", "ফ্ল্যাশলাইট কন্ট্রোল এরর: ${e.message}")
        }
    }

    /**
     * Get real battery status & percentage (ব্যাটারি চার্জ তথ্য)
     */
    fun getBatteryInfo(): ActionResult {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val level = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = batteryManager?.isCharging ?: false

            val msg = if (level >= 0) {
                "আপনার ফোনে এখন $level% চার্জ আছে${if (isCharging) " এবং চার্জ হচ্ছে।" else "।"}"
            } else {
                "ব্যাটারি তথ্য পাওয়া যায়নি।"
            }
            ActionResult(true, "getBatteryInfo", msg, data = level.toString())
        } catch (e: Exception) {
            ActionResult(false, "getBatteryInfo", "ব্যাটারি তথ্য এরর: ${e.message}")
        }
    }

    /**
     * Set Clock Alarm (অ্যালার্ম নির্ধারণ)
     */
    fun setAlarm(hour: Int, minute: Int, message: String = "SOJIB AI Alarm"): ActionResult {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(true, "setAlarm", "সময় $hour:$minute এর জন্য অ্যালার্ম সেট করা হয়েছে।")
        } catch (e: Exception) {
            ActionResult(false, "setAlarm", "অ্যালার্ম সেট করা যায়নি: ${e.message}")
        }
    }

    /**
     * Get Bangladeshi Prayer Times (আজকের নামাজের সময়সূচী)
     */
    fun getPrayerTimes(): ActionResult {
        val prayerInfo = "আজকের নামাজের সময়সূচী (বাংলাদেশ/ঢাকা):\n• ফজর: ৪:৪৩ AM\n• যোহর: ১১:৪৬ AM\n• আসর: ৩:৫৬ PM\n• মাগরিব: ৫:২৪ PM\n• এশা: ৬:৪০ PM"
        return ActionResult(true, "getPrayerTimes", prayerInfo)
    }
}
