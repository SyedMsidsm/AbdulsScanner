package com.syedmsidsm.abdulsscanner.ui.screens.qr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedmsidsm.abdulsscanner.data.db.entity.QrHistoryEntity
import com.syedmsidsm.abdulsscanner.data.repo.QrRepository
import com.syedmsidsm.abdulsscanner.domain.qr.ParsedQr
import com.syedmsidsm.abdulsscanner.domain.qr.QrParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrResultScreen(
    qrId: Long,
    rawText: String? = null,
    qrRepository: QrRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf<QrHistoryEntity?>(null) }
    var parsedQr by remember { mutableStateOf<ParsedQr?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(qrId, rawText) {
        if (!rawText.isNullOrEmpty()) {
            val parsed = QrParser.parse(rawText)
            entry = QrHistoryEntity(
                rawValue = rawText,
                format = "QR_CODE",
                valueType = parsed.valueType.name,
                source = "CAMERA",
                scannedAt = System.currentTimeMillis()
            )
            parsedQr = parsed
        } else if (qrId > 0) {
            val loaded = qrRepository.getById(qrId)
            entry = loaded
            if (loaded != null) {
                parsedQr = QrParser.parse(loaded.rawValue)
            }
        }
        isLoading = false
    }

    fun copyText(text: String, label: String = "Text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    fun connectToWifi(wifi: ParsedQr.Wifi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(wifi.ssid)
                .apply {
                    if (wifi.password.isNotBlank()) {
                        setWpa2Passphrase(wifi.password)
                    }
                    if (wifi.isHidden) setIsHiddenSsid(true)
                }
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bundle = Bundle().apply {
                    putParcelableArrayList(
                        Settings.EXTRA_WIFI_NETWORK_LIST,
                        arrayListOf(suggestion)
                    )
                }
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                    putExtras(bundle)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    copyText(wifi.password, "Password")
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            } else {
                copyText(wifi.password, "Password")
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        } else {
            copyText(wifi.password, "Password")
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Result") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val raw = entry?.rawValue
                    if (!raw.isNullOrBlank()) {
                        IconButton(onClick = { shareText(raw) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val qr = parsedQr
        if (qr == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Result not found", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        val formattedDate = remember(entry?.scannedAt) {
            val ts = entry?.scannedAt ?: System.currentTimeMillis()
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date(ts))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Result Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Type Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when (qr) {
                                is ParsedQr.Wifi -> Icons.Outlined.Wifi
                                is ParsedQr.Url -> Icons.Outlined.Link
                                is ParsedQr.Phone -> Icons.Outlined.Call
                                is ParsedQr.Email -> Icons.Outlined.Email
                                is ParsedQr.Sms -> Icons.Outlined.Sms
                                is ParsedQr.Geo -> Icons.Outlined.LocationOn
                                is ParsedQr.Contact -> Icons.Outlined.Person
                                is ParsedQr.Text -> Icons.Outlined.QrCode
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            val typeTitle = when (qr) {
                                is ParsedQr.Wifi -> "Wi-Fi Network"
                                is ParsedQr.Url -> "Website Link"
                                is ParsedQr.Phone -> "Phone Number"
                                is ParsedQr.Email -> "Email Address"
                                is ParsedQr.Sms -> "SMS Message"
                                is ParsedQr.Geo -> "Location"
                                is ParsedQr.Contact -> "Contact Card"
                                is ParsedQr.Text -> "Plain Text"
                            }
                            Text(typeTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Type Content
                    when (qr) {
                        is ParsedQr.Wifi -> {
                            Text("Network (SSID)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(qr.ssid, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Security", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(qr.encryptionType, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (qr.password.isNotBlank()) {
                                Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(qr.password, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { copyText(qr.password, "Password") }) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy password")
                                    }
                                }
                            }
                        }
                        is ParsedQr.Url -> {
                            Text("URL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(qr.url, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        is ParsedQr.Phone -> {
                            Text("Phone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(qr.number, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        is ParsedQr.Email -> {
                            Text("To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(qr.address, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (!qr.subject.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Subject", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(qr.subject, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (!qr.body.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Body", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(qr.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is ParsedQr.Sms -> {
                            Text("Recipient", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(qr.number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (!qr.message.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(qr.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is ParsedQr.Geo -> {
                            Text("Coordinates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${qr.latitude}, ${qr.longitude}", style = MaterialTheme.typography.titleMedium)
                            if (!qr.query.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Location Query", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(qr.query, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        is ParsedQr.Contact -> {
                            Text("Name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(qr.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (!qr.phone.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Phone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(qr.phone, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (!qr.email.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Email", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(qr.email, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (!qr.org.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Company / Org", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(qr.org, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        is ParsedQr.Text -> {
                            Text(qr.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // Primary Action Buttons
            when (qr) {
                is ParsedQr.Wifi -> {
                    Button(
                        onClick = { connectToWifi(qr) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect to Wi-Fi")
                    }
                    if (qr.password.isNotBlank()) {
                        OutlinedButton(
                            onClick = { copyText(qr.password, "Password") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy Password")
                        }
                    }
                }
                is ParsedQr.Url -> {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qr.url))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Browser")
                    }
                    OutlinedButton(
                        onClick = { copyText(qr.url, "URL") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Link")
                    }
                }
                is ParsedQr.Phone -> {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${qr.number}"))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call Number")
                    }
                    OutlinedButton(
                        onClick = { copyText(qr.number, "Phone") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Number")
                    }
                }
                is ParsedQr.Email -> {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${qr.address}")
                                if (!qr.subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, qr.subject)
                                if (!qr.body.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, qr.body)
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Email")
                    }
                    OutlinedButton(
                        onClick = { copyText(qr.address, "Email") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Email")
                    }
                }
                is ParsedQr.Sms -> {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${qr.number}")).apply {
                                if (!qr.message.isNullOrBlank()) putExtra("sms_body", qr.message)
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send SMS")
                    }
                }
                is ParsedQr.Geo -> {
                    Button(
                        onClick = {
                            val uri = if (!qr.query.isNullOrBlank()) {
                                Uri.parse("geo:${qr.latitude},${qr.longitude}?q=${Uri.encode(qr.query)}")
                            } else {
                                Uri.parse("geo:${qr.latitude},${qr.longitude}")
                            }
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Maps")
                    }
                }
                is ParsedQr.Contact -> {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
                                putExtra(ContactsContract.Intents.Insert.NAME, qr.name)
                                if (!qr.phone.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, qr.phone)
                                if (!qr.email.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, qr.email)
                                if (!qr.org.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.COMPANY, qr.org)
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add to Contacts")
                    }
                }
                is ParsedQr.Text -> {
                    Button(
                        onClick = { copyText(qr.text, "Text") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Text")
                    }
                    OutlinedButton(
                        onClick = { shareText(qr.text) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Text")
                    }
                }
            }
        }
    }
}
