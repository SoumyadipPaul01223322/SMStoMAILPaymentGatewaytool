package com.appmine.bankmail

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appmine.bankmail.service.BankMailMonitorService
import com.appmine.bankmail.ui.BankMailViewModel
import com.appmine.bankmail.ui.screens.DeviceReadiness
import com.appmine.bankmail.ui.screens.SettingsScreen
import com.appmine.bankmail.ui.screens.TransactionsScreen
import com.appmine.bankmail.ui.theme.BankMailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BankMailTheme {
                BankMailRoot()
            }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun BankMailRoot(
    viewModel: BankMailViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val config by viewModel.config.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val isMonitorServiceRunning by BankMailMonitorService.isRunning.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var readiness by remember { mutableStateOf(checkDeviceReadiness(context)) }
    val snackbarHostState = remember { SnackbarHostState() }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        readiness = checkDeviceReadiness(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        readiness = checkDeviceReadiness(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness = checkDeviceReadiness(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(config.forwardingEnabled, readiness.hasSmsPermission) {
        BankMailMonitorService.syncState(context, config.forwardingEnabled)
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val requestSmsPermissions = {
        smsPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        )
    }

    val openAppSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    val openOemAutostartSettings: () -> Unit = {
        val candidates = listOf(
            // Xiaomi MIUI / HyperOS
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            // Realme / Oppo / OnePlus (ColorOS / OxygenOS)
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oplus.battery",
                    "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            ),
            // Vivo / iQOO
            Intent().setComponent(
                ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            ),
            // Samsung
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            )
        )
        var launched = false
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                launched = true
                break
            } catch (_: Exception) {
                // Try next OEM intent
            }
        }
        if (!launched) {
            openAppSettings()
        }
    }

    val requestBatteryUnrestricted: () -> Unit = {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(fallback)
        }
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = "Transactions") },
                    label = { Text("Transactions") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> TransactionsScreen(
                config = config,
                transactions = transactions,
                selectedFilter = selectedFilter,
                readiness = readiness,
                onSelectFilter = viewModel::selectFilter,
                onToggleForwarding = viewModel::toggleForwarding,
                onRequestSmsPermission = requestSmsPermissions,
                onRequestBatteryUnrestricted = requestBatteryUnrestricted,
                onOpenSettingsTab = { selectedTab = 1 },
                onSimulateSms = viewModel::simulateIncomingSms,
                onRetryTransaction = viewModel::retryTransaction,
                onRetryAllFailed = viewModel::retryAllFailed,
                onDeleteTransaction = viewModel::deleteTransaction,
                onClearAllTransactions = viewModel::clearAllTransactions,
                modifier = Modifier.padding(innerPadding)
            )

            1 -> SettingsScreen(
                formState = formState,
                readiness = readiness,
                isMonitorServiceRunning = isMonitorServiceRunning,
                onSenderGmailChange = viewModel::updateSenderGmail,
                onAppPasswordChange = viewModel::updateAppPasswordInput,
                onRecipientGmailChange = viewModel::updateRecipientGmail,
                onVerificationKeyChange = viewModel::updateVerificationKey,
                onRegenerateVerificationKey = viewModel::regenerateVerificationKey,
                onCopyVerificationKey = { key ->
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("BankMail Verification Key", key))
                    viewModel.notifyKeyCopied()
                },
                onSubjectPrefixChange = viewModel::updateSubjectPrefix,
                onSenderKeywordsChange = viewModel::updateSenderKeywords,
                onBlockOtpsChange = viewModel::updateBlockOtps,
                onForwardingChange = viewModel::toggleForwarding,
                onSave = viewModel::saveSettings,
                onSendTestEmail = viewModel::sendTestEmail,
                onRequestSmsPermission = requestSmsPermissions,
                onOpenAppSettings = openAppSettings,
                onOpenAutostartSettings = openOemAutostartSettings,
                onRequestBatteryUnrestricted = requestBatteryUnrestricted,
                onRequestNotificationPermission = requestNotificationPermission,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

private fun checkDeviceReadiness(context: Context): DeviceReadiness {
    val hasReceiveSms = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED

    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    return DeviceReadiness(
        hasSmsPermission = hasReceiveSms,
        isBatteryUnrestricted = batteryUnrestricted,
        hasNotificationPermission = hasNotif
    )
}
