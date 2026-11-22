package com.appmine.bankmail.ui.screens

import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.appmine.bankmail.ui.SettingsFormState
import com.appmine.bankmail.ui.theme.SemanticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    formState: SettingsFormState,
    readiness: DeviceReadiness,
    isMonitorServiceRunning: Boolean,
    onSenderGmailChange: (String) -> Unit,
    onAppPasswordChange: (String) -> Unit,
    onRecipientGmailChange: (String) -> Unit,
    onVerificationKeyChange: (String) -> Unit,
    onRegenerateVerificationKey: () -> Unit,
    onCopyVerificationKey: (String) -> Unit,
    onSubjectPrefixChange: (String) -> Unit,
    onSenderKeywordsChange: (String) -> Unit,
    onBlockOtpsChange: (Boolean) -> Unit,
    onForwardingChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSendTestEmail: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Gmail Credentials stored in Local Room DB (Encrypted via Keystore)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Gmail SMTP (Local Database)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Saved directly in this phone's local SQLite database with Android Keystore encryption. No external server is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = formState.senderGmail,
                    onValueChange = onSenderGmailChange,
                    label = { Text("Your Gmail Address") },
                    placeholder = { Text("you@gmail.com") },
                    leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.appPasswordInput,
                    onValueChange = onAppPasswordChange,
                    label = { Text("Google App Password (16 chars)") },
                    placeholder = {
                        Text(
                            if (formState.hasSavedPassword) "Saved encrypted — enter to replace"
                            else "xxxx xxxx xxxx xxxx"
                        )
                    },
                    supportingText = {
                        Text(
                            if (formState.hasSavedPassword) {
                                "App Password is saved encrypted in local DB. Leave blank to keep it unchanged."
                            } else {
                                "Create at myaccount.google.com/apppasswords (requires 2-Step Verification). Spaces are removed automatically."
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.recipientGmail,
                    onValueChange = onRecipientGmailChange,
                    label = { Text("Forward To Gmail (Optional)") },
                    placeholder = { Text("Leave blank to send to yourself") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.verificationKey,
                    onValueChange = onVerificationKeyChange,
                    label = { Text("App Verification Key (Sent in Mail Body)") },
                    placeholder = { Text("BMK-XXXXXXXXXXXXXXXXXXXXXXXX") },
                    supportingText = {
                        Text(
                            "Embedded inside every forwarded email (between ---BANKMAIL_VERIFY_START--- and ---BANKMAIL_VERIFY_END---) so your integrating app can verify genuine transactions and avoid mixing other emails."
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = { onCopyVerificationKey(formState.verificationKey) },
                                enabled = formState.verificationKey.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy verification key"
                                )
                            }
                            IconButton(onClick = onRegenerateVerificationKey) {
                                Icon(
                                    imageVector = Icons.Outlined.Autorenew,
                                    contentDescription = "Generate new verification key"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!formState.testStatusMessage.isNullOrBlank()) {
                    val containerColor = if (formState.testStatusIsError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        SemanticColors.creditContainer()
                    }
                    val contentColor = if (formState.testStatusIsError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        SemanticColors.creditContent()
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = containerColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (formState.testStatusIsError) {
                                    Icons.Outlined.ErrorOutline
                                } else {
                                    Icons.Outlined.CheckCircle
                                },
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = formState.testStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSave,
                        enabled = !formState.isSaving && !formState.isTestingEmail,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (formState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to DB")
                        }
                    }

                    FilledTonalButton(
                        onClick = onSendTestEmail,
                        enabled = !formState.isSaving && !formState.isTestingEmail,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (formState.isTestingEmail) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Email")
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Section 2: Indian Bank SMS Filter Rules
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Indian Bank SMS Rules",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = formState.senderKeywords,
                    onValueChange = onSenderKeywordsChange,
                    label = { Text("Matched Sender IDs (comma-separated)") },
                    supportingText = {
                        Text("Automatically strips telecom prefixes like JD- or suffixes like -S / -T.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.subjectPrefix,
                    onValueChange = onSubjectPrefixChange,
                    label = { Text("Email Subject Prefix") },
                    supportingText = {
                        Text("Useful for creating a Gmail filter/label for your bank emails.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SettingSwitchRow(
                    title = "Block OTP messages",
                    subtitle = "Strongly recommended. Prevents login or payment OTPs from being emailed.",
                    checked = formState.blockOtps,
                    onCheckedChange = onBlockOtpsChange
                )

                SettingSwitchRow(
                    title = "Auto-forward incoming SMS",
                    subtitle = "When off, BankMail stops queueing new incoming messages.",
                    checked = formState.forwardingEnabled,
                    onCheckedChange = onForwardingChange
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Section 3: Device Permissions & Background Reliability
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Phone Permissions & Reliability",
                    style = MaterialTheme.typography.titleMedium
                )

                PermissionStatusRow(
                    title = "SMS Permission (RECEIVE_SMS)",
                    subtitle = if (readiness.hasSmsPermission) {
                        "Granted — listening for incoming Indian Bank SMS."
                    } else {
                        "Required. If Android 13+ blocks it on a sideloaded APK, open App Info -> 3-dot menu -> Allow restricted settings."
                    },
                    granted = readiness.hasSmsPermission,
                    primaryActionLabel = "Grant",
                    onPrimaryAction = onRequestSmsPermission,
                    secondaryActionLabel = "App Info",
                    onSecondaryAction = onOpenAppSettings
                )

                PermissionStatusRow(
                    title = "Battery Optimization",
                    subtitle = if (readiness.isBatteryUnrestricted) {
                        "Unrestricted — background delivery stays reliable."
                    } else {
                        "Currently optimized. Allow unrestricted battery so Android does not delay email forwarding."
                    },
                    granted = readiness.isBatteryUnrestricted,
                    primaryActionLabel = "Unrestricted",
                    onPrimaryAction = onRequestBatteryUnrestricted
                )

                BackgroundServiceCard(
                    isRunning = isMonitorServiceRunning,
                    onOpenAutostart = onOpenAutostartSettings,
                    onOpenAppSettings = onOpenAppSettings
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionStatusRow(
                        title = "Failure Alert Notifications",
                        subtitle = if (readiness.hasNotificationPermission) {
                            "Enabled — alerts you if an email fails to send."
                        } else {
                            "Optional — lets BankMail notify you if an App Password error occurs."
                        },
                        granted = readiness.hasNotificationPermission,
                        primaryActionLabel = "Allow",
                        onPrimaryAction = onRequestNotificationPermission
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BackgroundServiceCard(
    isRunning: Boolean,
    onOpenAutostart: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Background Service & Autostart",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isRunning) SemanticColors.creditContainer() else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isRunning) "Running" else "Paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRunning) SemanticColors.creditContent() else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = if (isRunning) {
                    "Active in background. Android does not report OEM Autostart state to apps, so use the shortcuts below anytime to verify Autostart is enabled."
                } else {
                    "Enable Auto-forward incoming SMS and grant SMS permission to start the background monitor."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAutostart) {
                    Text("Autostart Settings")
                }
                OutlinedButton(onClick = onOpenAppSettings) {
                    Text("App Info")
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (granted) SemanticColors.creditContainer() else SemanticColors.warningContainer()
                ) {
                    Text(
                        text = if (granted) "Ready" else "Action needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (granted) SemanticColors.creditContent() else SemanticColors.warningContent(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPrimaryAction) {
                        Text(primaryActionLabel)
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        OutlinedButton(onClick = onSecondaryAction) {
                            Text(secondaryActionLabel)
                        }
                    }
                }
            }
        }
    }
}
