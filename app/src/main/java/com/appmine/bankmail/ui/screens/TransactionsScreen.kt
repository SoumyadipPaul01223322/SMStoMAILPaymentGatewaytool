package com.appmine.bankmail.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appmine.bankmail.data.local.DeliveryStatus
import com.appmine.bankmail.data.local.GmailConfigEntity
import com.appmine.bankmail.data.local.TransactionDirection
import com.appmine.bankmail.data.local.TransactionLogEntity
import com.appmine.bankmail.mail.GmailSmtpClient
import com.appmine.bankmail.ui.TransactionFilter
import com.appmine.bankmail.ui.theme.SemanticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceReadiness(
    val hasSmsPermission: Boolean,
    val isBatteryUnrestricted: Boolean,
    val hasNotificationPermission: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    config: GmailConfigEntity,
    transactions: List<TransactionLogEntity>,
    selectedFilter: TransactionFilter,
    readiness: DeviceReadiness,
    onSelectFilter: (TransactionFilter) -> Unit,
    onToggleForwarding: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onSimulateSms: (sender: String, body: String) -> Unit,
    onRetryTransaction: (Long) -> Unit,
    onRetryAllFailed: () -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onClearAllTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showSimulatorSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            TransactionFilter.ALL -> transactions
            TransactionFilter.SENT -> transactions.filter { it.status == DeliveryStatus.SENT }
            TransactionFilter.PENDING -> transactions.filter {
                it.status == DeliveryStatus.PENDING || it.status == DeliveryStatus.SENDING
            }
            TransactionFilter.FAILED -> transactions.filter { it.status == DeliveryStatus.FAILED }
        }
    }

    val sentCount = remember(transactions) { transactions.count { it.status == DeliveryStatus.SENT } }
    val pendingCount = remember(transactions) {
        transactions.count { it.status == DeliveryStatus.PENDING || it.status == DeliveryStatus.SENDING }
    }
    val failedCount = remember(transactions) { transactions.count { it.status == DeliveryStatus.FAILED } }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("BankMail", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (config.forwardingEnabled) "Watching Indian Bank SMS" else "Forwarding paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (config.forwardingEnabled) {
                            SemanticColors.creditContent()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            actions = {
                Switch(
                    checked = config.forwardingEnabled,
                    onCheckedChange = onToggleForwarding
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Simulate Indian Bank SMS") },
                            leadingIcon = { Icon(Icons.Outlined.Science, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showSimulatorSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Retry pending & failed") },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRetryAllFailed()
                            }
                        )
                        if (transactions.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Clear local log") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showClearDialog = true
                                }
                            )
                        }
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (!readiness.hasSmsPermission || !config.isConfigured || !readiness.isBatteryUnrestricted) {
                item(key = "readiness_banner") {
                    SetupReadinessSection(
                        hasSmsPermission = readiness.hasSmsPermission,
                        isGmailConfigured = config.isConfigured,
                        isBatteryUnrestricted = readiness.isBatteryUnrestricted,
                        onRequestSmsPermission = onRequestSmsPermission,
                        onOpenSettingsTab = onOpenSettingsTab,
                        onRequestBatteryUnrestricted = onRequestBatteryUnrestricted
                    )
                }
            }

            item(key = "filter_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == TransactionFilter.ALL,
                        onClick = { onSelectFilter(TransactionFilter.ALL) },
                        label = { Text("All (${transactions.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == TransactionFilter.SENT,
                        onClick = { onSelectFilter(TransactionFilter.SENT) },
                        label = { Text("Sent ($sentCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == TransactionFilter.PENDING,
                        onClick = { onSelectFilter(TransactionFilter.PENDING) },
                        label = { Text("Pending ($pendingCount)") }
                    )
                    FilterChip(
                        selected = selectedFilter == TransactionFilter.FAILED,
                        onClick = { onSelectFilter(TransactionFilter.FAILED) },
                        label = { Text("Failed ($failedCount)") }
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                item(key = "empty_state") {
                    EmptyTransactionsState(
                        isFilteredEmpty = transactions.isNotEmpty(),
                        isConfigured = config.isConfigured,
                        onOpenSettings = onOpenSettingsTab,
                        onOpenSimulator = { showSimulatorSheet = true }
                    )
                }
            } else {
                items(
                    items = filteredTransactions,
                    key = { it.id }
                ) { item ->
                    TransactionRowItem(
                        item = item,
                        onRetry = { onRetryTransaction(item.id) },
                        onDelete = { onDeleteTransaction(item.id) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    if (showSimulatorSheet) {
        SimulateSmsBottomSheet(
            onDismiss = { showSimulatorSheet = false },
            onSubmit = { sender, body ->
                showSimulatorSheet = false
                onSimulateSms(sender, body)
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear local log?") },
            text = {
                Text("This removes captured SMS history from this phone's local database. Emails already sent to your Gmail stay untouched.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAllTransactions()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SetupReadinessSection(
    hasSmsPermission: Boolean,
    isGmailConfigured: Boolean,
    isBatteryUnrestricted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Finish phone setup",
                style = MaterialTheme.typography.titleMedium
            )
            if (!hasSmsPermission) {
                ReadinessActionRow(
                    icon = Icons.Outlined.Sms,
                    title = "Allow SMS access",
                    subtitle = "Needed to detect incoming Indian Bank alerts.",
                    actionLabel = "Allow",
                    onClick = onRequestSmsPermission
                )
            }
            if (!isGmailConfigured) {
                ReadinessActionRow(
                    icon = Icons.Outlined.MarkEmailRead,
                    title = "Save Gmail & App Password",
                    subtitle = "Stored encrypted in your local database.",
                    actionLabel = "Configure",
                    onClick = onOpenSettingsTab
                )
            }
            if (!isBatteryUnrestricted) {
                ReadinessActionRow(
                    icon = Icons.Outlined.BatteryAlert,
                    title = "Disable battery restriction",
                    subtitle = "Keeps SMS forwarding reliable when the phone sleeps.",
                    actionLabel = "Allow",
                    onClick = onRequestBatteryUnrestricted
                )
            }
        }
    }
}

@Composable
private fun ReadinessActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun TransactionRowItem(
    item: TransactionLogEntity,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember(item.id) { mutableStateOf(item.status == DeliveryStatus.FAILED) }

    val isCredit = item.direction == TransactionDirection.CREDIT
    val isDebit = item.direction == TransactionDirection.DEBIT

    val directionBg = when {
        isCredit -> SemanticColors.creditContainer()
        isDebit -> SemanticColors.debitContainer()
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val directionFg = when {
        isCredit -> SemanticColors.creditContent()
        isDebit -> SemanticColors.debitContent()
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = directionBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isCredit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                        contentDescription = item.direction,
                        tint = directionFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            if (isCredit) append("+") else if (isDebit) append("-")
                            append(item.amount ?: "Bank Alert")
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = directionFg,
                        maxLines = 1
                    )
                    DeliveryStatusBadge(status = item.status)
                }

                Spacer(modifier = Modifier.height(2.dp))

                val metaLine = buildList {
                    item.paymentMode?.let { add(it) }
                    item.accountMask?.let { add("A/c $it") }
                    add(item.smsSender)
                    if (item.isSimulated) add("Test")
                }.joinToString(" · ")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatCompactTime(item.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.smsBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val detailParts = buildList {
                            add("ID: ${GmailSmtpClient.formatTransactionId(item.fingerprint)}")
                            item.referenceNumber?.let { add("Ref: $it") }
                            item.availableBalance?.let { add("Avail Bal: $it") }
                            item.sentAt?.let { add("Emailed: ${formatCompactTime(it)}") }
                        }
                        if (detailParts.isNotEmpty()) {
                            Text(
                                text = detailParts.joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!item.errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = item.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.status != DeliveryStatus.SENT) {
                        FilledTonalButton(
                            onClick = onRetry,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Email")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDelete) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatusBadge(status: String) {
    val (label, icon, bg, fg) = when (status) {
        DeliveryStatus.SENT -> Quad(
            "Sent",
            Icons.Outlined.CheckCircle,
            SemanticColors.creditContainer(),
            SemanticColors.creditContent()
        )
        DeliveryStatus.SENDING -> Quad(
            "Sending",
            Icons.Outlined.Sync,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        DeliveryStatus.FAILED -> Quad(
            "Failed",
            Icons.Outlined.ErrorOutline,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Quad(
            "Queued",
            Icons.Outlined.Schedule,
            SemanticColors.warningContainer(),
            SemanticColors.warningContent()
        )
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun EmptyTransactionsState(
    isFilteredEmpty: Boolean,
    isConfigured: Boolean,
    onOpenSettings: () -> Unit,
    onOpenSimulator: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.MarkEmailRead,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = if (isFilteredEmpty) "No matching transactions" else "Ready for Indian Bank alerts",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (isFilteredEmpty) {
                "Switch to All to see every captured SMS in your local log."
            } else {
                "Incoming Indian Bank debit and credit SMS messages will be parsed on-device, filtered to exclude OTPs, and forwarded straight to your Gmail."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isFilteredEmpty) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isConfigured) {
                    Button(onClick = onOpenSettings) {
                        Text("Configure Gmail")
                    }
                }
                OutlinedButton(onClick = onOpenSimulator) {
                    Icon(
                        Icons.Outlined.Science,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test with Sample SMS")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimulateSmsBottomSheet(
    onDismiss: () -> Unit,
    onSubmit: (sender: String, body: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sender by remember { mutableStateOf("JD-INDBNK-S") }
    var body by remember {
        mutableStateOf(
            "Your A/c XXXXX4321 is debited by Rs.450.00 on 22-11-26 by UPI/432198765432. Avl Bal Rs.18,240.50. -Indian Bank"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Simulate Indian Bank SMS",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Tests your local parser, OTP blocker, local DB queue, and Gmail delivery end-to-end.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        sender = "JD-INDBNK-S"
                        body = "Your A/c XXXXX4321 is debited by Rs.450.00 on 22-11-26 by UPI/432198765432. Avl Bal Rs.18,240.50. -Indian Bank"
                    },
                    label = { Text("UPI Debit") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        sender = "VM-INDIBK-T"
                        body = "Your A/c *4321 is credited with INR 12,500.00 on 22-11-26 via IMPS Ref 99887766. Clear Bal: INR 30,740.50 - Indian Bank"
                    },
                    label = { Text("IMPS Credit") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        sender = "JD-INDBNK-S"
                        body = "Dear Customer, OTP for your Indian Bank transaction of Rs.5,000 is 482910. Valid for 5 mins. Do not share OTP with anyone."
                    },
                    label = { Text("OTP (Should Block)") }
                )
            }

            OutlinedTextField(
                value = sender,
                onValueChange = { sender = it },
                label = { Text("SMS Sender ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("SMS Message Body") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(sender, body) },
                    enabled = body.isNotBlank()
                ) {
                    Text("Run Test")
                }
            }
        }
    }
}

private fun formatCompactTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
