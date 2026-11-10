package com.syedmsidsm.abdulsscanner.ui.screens.tools

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.syedmsidsm.abdulsscanner.di.AppContainer
import com.syedmsidsm.abdulsscanner.domain.pdf.ToolInput
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolPasswordScreen(
    container: AppContainer,
    onNavigateBack: () -> Unit,
    onNavigateToDoc: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourcePicker by remember { mutableStateOf(true) }
    var selectedInput by remember { mutableStateOf<ToolInput?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Add Password, 1 = Remove Password

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var removeCurrentPassword by remember { mutableStateOf("") }
    var showRemovePassword by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var resultFileName by remember { mutableStateOf("") }
    var resultInfoText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Password", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (selectedInput == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = { showSourcePicker = true }) {
                        Text("Choose Document or PDF")
                    }
                }
            } else {
                val inputName = when (val inp = selectedInput!!) {
                    is ToolInput.AppDoc -> "App Document"
                    is ToolInput.PdfFile -> inp.displayName
                }

                Text(
                    text = "File: $inputName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Add Password") },
                        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Remove Password") },
                        icon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (selectedTab == 0) {
                    // Add Password Tab
                    Text(
                        text = "Protect this PDF with 256-bit AES encryption. A password will be required every time the file is opened.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (password.isBlank()) {
                                Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password != confirmPassword) {
                                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isProcessing = true
                            scope.launch {
                                try {
                                    val baseName = container.pdfToolsService.getDocDisplayName(selectedInput!!)
                                    val protectedFile = withContext(Dispatchers.IO) {
                                        container.pdfToolsService.passwordProtect(selectedInput!!, password)
                                    }
                                    val savedUri = withContext(Dispatchers.IO) {
                                        container.pdfToolsService.exportToToolsFolder(protectedFile, baseName, "protected")
                                    }
                                    protectedFile.delete()

                                    if (savedUri != null) {
                                        resultUri = savedUri
                                        resultFileName = "$baseName (protected).pdf"
                                        resultInfoText = "Protected with 256-bit AES encryption"
                                    } else {
                                        Toast.makeText(context, "Failed to save protected file", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error protecting PDF: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = password.isNotBlank() && confirmPassword.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(24.dp))
                        } else {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Protect PDF")
                        }
                    }
                } else {
                    // Remove Password Tab
                    Text(
                        text = "Enter the current password of this PDF to remove security protection and produce an unlocked copy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = removeCurrentPassword,
                        onValueChange = { removeCurrentPassword = it },
                        label = { Text("Current Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showRemovePassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showRemovePassword = !showRemovePassword }) {
                                Icon(
                                    if (showRemovePassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (removeCurrentPassword.isBlank()) {
                                Toast.makeText(context, "Enter current password", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isProcessing = true
                            scope.launch {
                                try {
                                    val baseName = container.pdfToolsService.getDocDisplayName(selectedInput!!)
                                    val unlockedFile = withContext(Dispatchers.IO) {
                                        container.pdfToolsService.removePassword(selectedInput!!, removeCurrentPassword)
                                    }
                                    val savedUri = withContext(Dispatchers.IO) {
                                        container.pdfToolsService.exportToToolsFolder(unlockedFile, baseName, "unlocked")
                                    }
                                    unlockedFile.delete()

                                    if (savedUri != null) {
                                        resultUri = savedUri
                                        resultFileName = "$baseName (unlocked).pdf"
                                        resultInfoText = "Password protection removed successfully"
                                    } else {
                                        Toast.makeText(context, "Failed to save unlocked file", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: InvalidPasswordException) {
                                    Toast.makeText(context, "Wrong password", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    if (e.message?.contains("password", ignoreCase = true) == true) {
                                        Toast.makeText(context, "Wrong password", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = removeCurrentPassword.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(24.dp))
                        } else {
                            Icon(Icons.Outlined.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove Password")
                        }
                    }
                }
            }
        }

        if (showSourcePicker) {
            ToolSourcePickerDialog(
                title = "Select Document or PDF for Password",
                container = container,
                onSelectAppDoc = { doc ->
                    selectedInput = ToolInput.AppDoc(doc.id)
                    showSourcePicker = false
                },
                onSelectPdfFile = { uri, name ->
                    selectedInput = ToolInput.PdfFile(uri, name)
                    showSourcePicker = false
                },
                onDismiss = {
                    showSourcePicker = false
                    if (selectedInput == null) onNavigateBack()
                }
            )
        }

        resultUri?.let { uri ->
            ToolResultBottomSheet(
                outputUri = uri,
                fileName = resultFileName,
                infoText = resultInfoText,
                container = container,
                onNavigateToDoc = onNavigateToDoc,
                onDone = {
                    resultUri = null
                    onNavigateBack()
                }
            )
        }
    }
}
