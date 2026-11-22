package com.appmine.bankmail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appmine.bankmail.BankMailApp
import com.appmine.bankmail.data.local.GmailConfigEntity
import com.appmine.bankmail.data.local.TransactionLogEntity
import com.appmine.bankmail.data.repository.ProcessSmsOutcome
import com.appmine.bankmail.mail.SmtpSendResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TransactionFilter(val label: String) {
    ALL("All"),
    SENT("Sent"),
    PENDING("Pending"),
    FAILED("Failed")
}

data class SettingsFormState(
    val senderGmail: String = "",
    val appPasswordInput: String = "",
    val hasSavedPassword: Boolean = false,
    val recipientGmail: String = "",
    val verificationKey: String = "",
    val subjectPrefix: String = GmailConfigEntity.DEFAULT_SUBJECT_PREFIX,
    val senderKeywords: String = GmailConfigEntity.DEFAULT_SENDER_KEYWORDS,
    val blockOtps: Boolean = true,
    val forwardingEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val isTestingEmail: Boolean = false,
    val testStatusMessage: String? = null,
    val testStatusIsError: Boolean = false,
    val initializedFromDb: Boolean = false
)

class BankMailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as BankMailApp).repository

    val config: StateFlow<GmailConfigEntity> = repository.observeConfig()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GmailConfigEntity()
        )

    val transactions: StateFlow<List<TransactionLogEntity>> = repository.observeTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _selectedFilter = MutableStateFlow(TransactionFilter.ALL)
    val selectedFilter: StateFlow<TransactionFilter> = _selectedFilter.asStateFlow()

    private val _formState = MutableStateFlow(SettingsFormState())
    val formState: StateFlow<SettingsFormState> = _formState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeConfig().collect { saved ->
                val savedKey = runCatching {
                    repository.decryptSecret(saved.encryptedVerificationKey)
                }.getOrDefault("")
                _formState.update { current ->
                    if (!current.initializedFromDb) {
                        current.copy(
                            senderGmail = saved.senderGmail,
                            hasSavedPassword = saved.encryptedAppPassword.isNotBlank(),
                            recipientGmail = saved.recipientGmail,
                            verificationKey = savedKey.ifBlank { repository.generateVerificationKey() },
                            subjectPrefix = saved.subjectPrefix,
                            senderKeywords = saved.senderKeywords,
                            blockOtps = saved.blockOtps,
                            forwardingEnabled = saved.forwardingEnabled,
                            initializedFromDb = true
                        )
                    } else {
                        current.copy(
                            hasSavedPassword = saved.encryptedAppPassword.isNotBlank(),
                            forwardingEnabled = saved.forwardingEnabled
                        )
                    }
                }
            }
        }
    }

    fun selectFilter(filter: TransactionFilter) {
        _selectedFilter.value = filter
    }

    fun updateSenderGmail(value: String) {
        _formState.update { it.copy(senderGmail = value, testStatusMessage = null) }
    }

    fun updateAppPasswordInput(value: String) {
        _formState.update { it.copy(appPasswordInput = value, testStatusMessage = null) }
    }

    fun updateRecipientGmail(value: String) {
        _formState.update { it.copy(recipientGmail = value, testStatusMessage = null) }
    }

    fun updateVerificationKey(value: String) {
        _formState.update { it.copy(verificationKey = value, testStatusMessage = null) }
    }

    fun regenerateVerificationKey() {
        val freshKey = repository.generateVerificationKey()
        _formState.update { it.copy(verificationKey = freshKey, testStatusMessage = null) }
        _snackbarMessages.tryEmit("Generated new verification key — tap Save to DB to apply")
    }

    fun notifyKeyCopied() {
        _snackbarMessages.tryEmit("Verification key copied to clipboard")
    }

    fun updateSubjectPrefix(value: String) {
        _formState.update { it.copy(subjectPrefix = value) }
    }

    fun updateSenderKeywords(value: String) {
        _formState.update { it.copy(senderKeywords = value) }
    }

    fun updateBlockOtps(enabled: Boolean) {
        _formState.update { it.copy(blockOtps = enabled) }
    }

    fun toggleForwarding(enabled: Boolean) {
        viewModelScope.launch {
            repository.setForwardingEnabled(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "Monitoring Indian Bank SMS" else "SMS forwarding paused"
            )
        }
    }

    fun saveSettings() {
        val state = _formState.value
        val trimmedSender = state.senderGmail.trim()
        if (trimmedSender.isEmpty() || !trimmedSender.contains("@")) {
            _formState.update {
                it.copy(
                    testStatusMessage = "Enter a valid Gmail address before saving.",
                    testStatusIsError = true
                )
            }
            return
        }
        val cleanedPassword = state.appPasswordInput.replace("\\s+".toRegex(), "")
        if (cleanedPassword.isEmpty() && !state.hasSavedPassword) {
            _formState.update {
                it.copy(
                    testStatusMessage = "Enter your 16-character Google App Password before saving.",
                    testStatusIsError = true
                )
            }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true, testStatusMessage = null) }
            try {
                val saved = repository.saveConfig(
                    senderGmail = trimmedSender,
                    plainAppPasswordInput = cleanedPassword,
                    recipientGmail = state.recipientGmail.trim(),
                    subjectPrefix = state.subjectPrefix,
                    senderKeywords = state.senderKeywords,
                    blockOtps = state.blockOtps,
                    forwardingEnabled = state.forwardingEnabled,
                    verificationKeyInput = state.verificationKey
                )
                val effectiveSavedKey = runCatching {
                    repository.decryptSecret(saved.encryptedVerificationKey)
                }.getOrDefault(state.verificationKey)
                _formState.update {
                    it.copy(
                        isSaving = false,
                        appPasswordInput = "",
                        hasSavedPassword = saved.encryptedAppPassword.isNotBlank(),
                        verificationKey = effectiveSavedKey,
                        subjectPrefix = saved.subjectPrefix,
                        senderKeywords = saved.senderKeywords,
                        testStatusMessage = "Saved to local encrypted database.",
                        testStatusIsError = false
                    )
                }
                _snackbarMessages.tryEmit("Settings saved locally")
            } catch (e: Exception) {
                _formState.update {
                    it.copy(
                        isSaving = false,
                        testStatusMessage = "Failed to save settings: ${e.message ?: "unknown error"}",
                        testStatusIsError = true
                    )
                }
            }
        }
    }

    fun sendTestEmail() {
        val state = _formState.value
        viewModelScope.launch {
            _formState.update { it.copy(isTestingEmail = true, testStatusMessage = null) }
            val result = repository.sendTestEmail(
                senderGmailOverride = state.senderGmail,
                plainPasswordOverride = state.appPasswordInput,
                recipientGmailOverride = state.recipientGmail,
                subjectPrefixOverride = state.subjectPrefix,
                verificationKeyOverride = state.verificationKey
            )
            when (result) {
                is SmtpSendResult.Success -> {
                    _formState.update {
                        it.copy(
                            isTestingEmail = false,
                            testStatusMessage = "Test email delivered to ${state.recipientGmail.ifBlank { state.senderGmail }}.",
                            testStatusIsError = false
                        )
                    }
                    _snackbarMessages.tryEmit("Test email sent")
                }

                is SmtpSendResult.AuthError -> {
                    _formState.update {
                        it.copy(
                            isTestingEmail = false,
                            testStatusMessage = result.message,
                            testStatusIsError = true
                        )
                    }
                }

                is SmtpSendResult.TransientError -> {
                    _formState.update {
                        it.copy(
                            isTestingEmail = false,
                            testStatusMessage = "Connection failed: ${result.message}",
                            testStatusIsError = true
                        )
                    }
                }
            }
        }
    }

    fun simulateIncomingSms(sender: String, body: String) {
        viewModelScope.launch {
            when (val outcome = repository.processIncomingSms(sender, body, isSimulated = true)) {
                is ProcessSmsOutcome.Queued -> {
                    val amt = outcome.amount ?: "transaction"
                    _snackbarMessages.tryEmit("Matched ${outcome.direction} ($amt) — queued for Gmail")
                }

                is ProcessSmsOutcome.Ignored -> {
                    _snackbarMessages.tryEmit("Ignored: ${outcome.reason}")
                }

                is ProcessSmsOutcome.Duplicate -> {
                    _snackbarMessages.tryEmit(outcome.reason)
                }

                ProcessSmsOutcome.Paused -> {
                    _snackbarMessages.tryEmit("Forwarding is paused")
                }
            }
        }
    }

    fun retryTransaction(id: Long) {
        viewModelScope.launch {
            repository.retryTransaction(id)
            _snackbarMessages.tryEmit("Queued retry for Gmail")
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            val count = repository.retryAllPendingOrFailed()
            _snackbarMessages.tryEmit(
                if (count > 0) "Queued $count transaction(s) for retry"
                else "No pending or failed transactions to retry"
            )
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _snackbarMessages.tryEmit("Transaction removed from local log")
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            repository.clearAllTransactions()
            _snackbarMessages.tryEmit("Cleared local transaction history")
        }
    }
}
