package com.appmine.bankmail.mail

import com.appmine.bankmail.data.local.TransactionDirection
import com.appmine.bankmail.data.local.TransactionLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

data class SmtpCredentials(
    val senderGmail: String,
    val appPassword: String,
    val recipientGmail: String,
    val verificationKey: String = ""
)

sealed interface SmtpSendResult {
    data object Success : SmtpSendResult
    data class AuthError(val message: String) : SmtpSendResult
    data class TransientError(val message: String) : SmtpSendResult
}

/**
 * Sends emails directly from the Android device to Gmail SMTP (smtp.gmail.com)
 * using the user's Google App Password — no backend server required.
 *
 * Uses Port 465 (SMTPS / SSL) first, and automatically falls back to Port 587 (STARTTLS)
 * if the local network blocks port 465.
 */
class GmailSmtpClient {

    fun sendTransactionEmail(
        credentials: SmtpCredentials,
        subjectPrefix: String,
        entry: TransactionLogEntity
    ): SmtpSendResult {
        val txnId = formatTransactionId(entry.fingerprint)
        val subject = buildTransactionSubject(subjectPrefix, entry)
        val plainBody = buildTransactionPlainBody(entry, credentials.verificationKey, txnId)
        val htmlBody = buildTransactionHtmlBody(entry, credentials.verificationKey, txnId)
        return sendEmail(
            credentials = credentials,
            subject = subject,
            plainBody = plainBody,
            htmlBody = htmlBody,
            txnId = txnId
        )
    }

    fun sendTestEmail(
        credentials: SmtpCredentials,
        subjectPrefix: String
    ): SmtpSendResult {
        val nowMillis = System.currentTimeMillis()
        val nowFormatted = formatTimestamp(nowMillis)
        val prefix = subjectPrefix.trim().ifEmpty { "[Indian Bank]" }
        val verifyKey = credentials.verificationKey.trim()
        val testTxnId = "TXN-TEST-${nowMillis.toString(16).uppercase()}"
        val verifyJson = buildTestVerificationJson(verifyKey, testTxnId, nowMillis)
        val subject = "$prefix BankMail Connection Test"
        val plainBody = buildString {
            appendLine("BankMail test email succeeded!")
            appendLine()
            appendLine("Your Android phone connected directly to Gmail SMTP using your saved Google App Password.")
            appendLine("Sender Gmail: ${credentials.senderGmail}")
            appendLine("Recipient Gmail: ${credentials.recipientGmail}")
            if (verifyKey.isNotEmpty()) {
                appendLine("Verification Key: $verifyKey")
            }
            appendLine("Transaction ID: $testTxnId")
            appendLine("Timestamp: $nowFormatted")
            appendLine()
            appendLine("Incoming Indian Bank transaction SMS messages will now be forwarded here automatically.")
            appendLine()
            appendLine(VERIFY_BLOCK_START)
            appendLine(verifyJson)
            appendLine(VERIFY_BLOCK_END)
        }
        val htmlBody = """
            <html>
            <body style="font-family: -apple-system, Roboto, Arial, sans-serif; color: #1b2430; line-height: 1.5; margin: 0; padding: 20px; background-color: #f5f7fa;">
              <div style="max-width: 560px; margin: 0 auto; background: #ffffff; border: 1px solid #dce3ec; border-radius: 12px; padding: 24px;">
                <h2 style="margin: 0 0 8px 0; color: #163567; font-size: 18px;">BankMail Test Successful</h2>
                <p style="margin: 0 0 16px 0; font-size: 14px; color: #435060;">
                  Your phone connected directly to <strong>smtp.gmail.com</strong> without any backend server.
                  Incoming Indian Bank transaction SMS alerts will be forwarded to this inbox.
                </p>
                <table style="width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 16px;">
                  <tr><td style="padding: 6px 0; color: #64748b;">Sender</td><td style="padding: 6px 0; font-weight: 600;">${escapeHtml(credentials.senderGmail)}</td></tr>
                  <tr><td style="padding: 6px 0; color: #64748b;">Recipient</td><td style="padding: 6px 0; font-weight: 600;">${escapeHtml(credentials.recipientGmail)}</td></tr>
                  ${if (verifyKey.isNotEmpty()) "<tr><td style=\"padding: 6px 0; color: #64748b;\">Verification Key</td><td style=\"padding: 6px 0; font-family: monospace; font-weight: 600;\">${escapeHtml(verifyKey)}</td></tr>" else ""}
                  <tr><td style="padding: 6px 0; color: #64748b;">Transaction ID</td><td style="padding: 6px 0; font-family: monospace;">${escapeHtml(testTxnId)}</td></tr>
                  <tr><td style="padding: 6px 0; color: #64748b;">Tested At</td><td style="padding: 6px 0;">${escapeHtml(nowFormatted)}</td></tr>
                </table>
                <div style="background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px; font-family: monospace; font-size: 12px; color: #0f172a; white-space: pre-wrap;">${escapeHtml("$VERIFY_BLOCK_START\n$verifyJson\n$VERIFY_BLOCK_END")}</div>
              </div>
            </body>
            </html>
        """.trimIndent()
        return sendEmail(
            credentials = credentials,
            subject = subject,
            plainBody = plainBody,
            htmlBody = htmlBody,
            txnId = testTxnId
        )
    }

    private fun sendEmail(
        credentials: SmtpCredentials,
        subject: String,
        plainBody: String,
        htmlBody: String,
        txnId: String
    ): SmtpSendResult {
        val cleanSender = credentials.senderGmail.trim()
        val cleanRecipient = credentials.recipientGmail.trim().ifEmpty { cleanSender }
        val cleanPassword = credentials.appPassword.replace("\\s+".toRegex(), "")
        val cleanVerifyKey = credentials.verificationKey.trim()

        if (cleanSender.isEmpty() || cleanPassword.isEmpty()) {
            return SmtpSendResult.AuthError("Gmail address or App Password is missing.")
        }

        // Attempt 1: Port 465 SSL
        val sslResult = attemptSend(
            sender = cleanSender,
            password = cleanPassword,
            recipient = cleanRecipient,
            subject = subject,
            plainBody = plainBody,
            htmlBody = htmlBody,
            verifyKey = cleanVerifyKey,
            txnId = txnId,
            useSsl465 = true
        )
        if (sslResult is SmtpSendResult.Success || sslResult is SmtpSendResult.AuthError) {
            return sslResult
        }

        // Attempt 2: Fallback to Port 587 STARTTLS
        return attemptSend(
            sender = cleanSender,
            password = cleanPassword,
            recipient = cleanRecipient,
            subject = subject,
            plainBody = plainBody,
            htmlBody = htmlBody,
            verifyKey = cleanVerifyKey,
            txnId = txnId,
            useSsl465 = false
        )
    }

    private fun attemptSend(
        sender: String,
        password: String,
        recipient: String,
        subject: String,
        plainBody: String,
        htmlBody: String,
        verifyKey: String,
        txnId: String,
        useSsl465: Boolean
    ): SmtpSendResult {
        return try {
            val props = Properties().apply {
                put("mail.smtp.host", GMAIL_SMTP_HOST)
                put("mail.smtp.auth", "true")
                put("mail.smtp.connectiontimeout", TIMEOUT_MS)
                put("mail.smtp.timeout", TIMEOUT_MS)
                put("mail.smtp.writetimeout", TIMEOUT_MS)
                if (useSsl465) {
                    put("mail.smtp.port", "465")
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                    put("mail.smtp.socketFactory.port", "465")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                } else {
                    put("mail.smtp.port", "587")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
                }
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(sender, password)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(sender, "BankMail"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false))
                setSubject(subject, "UTF-8")
                if (verifyKey.isNotEmpty()) {
                    setHeader(HEADER_VERIFY_KEY, verifyKey)
                }
                setHeader(HEADER_TXN_ID, txnId)
                sentDate = Date()

                val multipart = MimeMultipart("alternative")
                val textPart = MimeBodyPart().apply {
                    setText(plainBody, "UTF-8")
                }
                val htmlPart = MimeBodyPart().apply {
                    setContent(htmlBody, "text/html; charset=UTF-8")
                }
                multipart.addBodyPart(textPart)
                multipart.addBodyPart(htmlPart)
                setContent(multipart)
            }

            Transport.send(message)
            SmtpSendResult.Success
        } catch (e: AuthenticationFailedException) {
            SmtpSendResult.AuthError(
                "Gmail rejected the login. Verify 2-Step Verification is enabled and you entered the 16-character Google App Password (not your regular password)."
            )
        } catch (e: MessagingException) {
            val raw = e.message.orEmpty()
            if (raw.contains("535", ignoreCase = true) || raw.contains("Username and Password not accepted", ignoreCase = true)) {
                SmtpSendResult.AuthError(
                    "Google App Password was not accepted (SMTP 535). Double-check your Gmail address and 16-character App Password."
                )
            } else {
                SmtpSendResult.TransientError(raw.ifBlank { "Unable to reach smtp.gmail.com" })
            }
        } catch (e: Exception) {
            SmtpSendResult.TransientError(e.message ?: "Unexpected network error while sending email")
        }
    }

    internal fun buildTransactionSubject(subjectPrefix: String, entry: TransactionLogEntity): String {
        val prefix = subjectPrefix.trim().ifEmpty { "[Indian Bank]" }
        val actionLabel = when (entry.direction) {
            TransactionDirection.DEBIT -> "Debited"
            TransactionDirection.CREDIT -> "Credited"
            else -> "Transaction"
        }
        val parts = buildList {
            add(prefix)
            add(actionLabel)
            entry.amount?.let { add(it) }
            entry.paymentMode?.let { add("($it)") }
            entry.accountMask?.let { add("A/c $it") }
            if (entry.isSimulated) add("[TEST]")
        }
        return parts.joinToString(" ")
    }

    internal fun buildTransactionPlainBody(
        entry: TransactionLogEntity,
        verificationKey: String,
        txnId: String = formatTransactionId(entry.fingerprint)
    ): String {
        val cleanKey = verificationKey.trim()
        val verifyJson = buildTransactionVerificationJson(entry, cleanKey, txnId)
        return buildString {
            appendLine("Indian Bank Transaction Alert")
            appendLine("-----------------------------")
            if (cleanKey.isNotEmpty()) {
                appendLine("Verification Key: $cleanKey")
            }
            appendLine("Transaction ID: $txnId")
            appendLine("Type: ${entry.direction}")
            entry.amount?.let { appendLine("Amount: $it") }
            entry.paymentMode?.let { appendLine("Mode: $it") }
            entry.accountMask?.let { appendLine("Account/Card: $it") }
            entry.referenceNumber?.let { appendLine("Reference / UTR: $it") }
            entry.availableBalance?.let { appendLine("Available Balance: $it") }
            appendLine("SMS Sender: ${entry.smsSender}")
            appendLine("Received At: ${formatTimestamp(entry.receivedAt)}")
            appendLine()
            appendLine("Original SMS:")
            appendLine(entry.smsBody)
            appendLine()
            appendLine(VERIFY_BLOCK_START)
            appendLine(verifyJson)
            appendLine(VERIFY_BLOCK_END)
        }
    }

    internal fun buildTransactionHtmlBody(
        entry: TransactionLogEntity,
        verificationKey: String,
        txnId: String = formatTransactionId(entry.fingerprint)
    ): String {
        val cleanKey = verificationKey.trim()
        val verifyJson = buildTransactionVerificationJson(entry, cleanKey, txnId)
        val badgeColor = when (entry.direction) {
            TransactionDirection.DEBIT -> "#b91c1c"
            TransactionDirection.CREDIT -> "#15803d"
            else -> "#163567"
        }
        val badgeBg = when (entry.direction) {
            TransactionDirection.DEBIT -> "#fef2f2"
            TransactionDirection.CREDIT -> "#f0fdf4"
            else -> "#eff6ff"
        }
        val directionTitle = when (entry.direction) {
            TransactionDirection.DEBIT -> "Debited"
            TransactionDirection.CREDIT -> "Credited"
            else -> "Transaction"
        }
        val rows = buildString {
            entry.amount?.let { append(tableRow("Amount", it, bold = true)) }
            entry.paymentMode?.let { append(tableRow("Mode", it)) }
            entry.accountMask?.let { append(tableRow("Account / Card", it)) }
            entry.referenceNumber?.let { append(tableRow("Reference / UTR", it)) }
            entry.availableBalance?.let { append(tableRow("Available Balance", it)) }
            append(tableRow("SMS Sender", entry.smsSender))
            append(tableRow("Received At", formatTimestamp(entry.receivedAt)))
            append(tableRow("Transaction ID", txnId, mono = true))
            if (cleanKey.isNotEmpty()) {
                append(tableRow("Verification Key", cleanKey, mono = true))
            }
        }
        return """
            <html>
            <body style="font-family: -apple-system, Roboto, Arial, sans-serif; color: #1b2430; line-height: 1.5; margin: 0; padding: 20px; background-color: #f5f7fa;">
              <div style="max-width: 560px; margin: 0 auto; background: #ffffff; border: 1px solid #dce3ec; border-radius: 12px; padding: 24px;">
                <div style="margin-bottom: 14px;">
                  <span style="display: inline-block; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; color: $badgeColor; background-color: $badgeBg;">
                    ${escapeHtml(directionTitle.uppercase())}
                  </span>
                  ${if (entry.isSimulated) "<span style=\"margin-left: 6px; font-size: 12px; color: #64748b;\">Simulated Test</span>" else ""}
                </div>
                <h2 style="margin: 0 0 16px 0; font-size: 22px; color: #0f172a;">
                  ${escapeHtml(entry.amount ?: "Indian Bank Alert")}
                </h2>
                <table style="width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 18px;">
                  $rows
                </table>
                <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 14px; margin-bottom: 14px;">
                  <div style="font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 6px;">Original SMS</div>
                  <div style="font-size: 13px; color: #1e293b; white-space: pre-wrap;">${escapeHtml(entry.smsBody)}</div>
                </div>
                <div style="background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px 14px;">
                  <div style="font-size: 11px; font-weight: 700; color: #475569; text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 6px;">App Verification Payload</div>
                  <pre style="margin: 0; font-family: monospace; font-size: 11px; color: #0f172a; white-space: pre-wrap; word-break: break-all;">${escapeHtml("$VERIFY_BLOCK_START\n$verifyJson\n$VERIFY_BLOCK_END")}</pre>
                </div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    internal fun buildTransactionVerificationJson(
        entry: TransactionLogEntity,
        verificationKey: String,
        txnId: String = formatTransactionId(entry.fingerprint)
    ): String {
        val numericAmount = entry.amount?.replace("[^0-9.]".toRegex(), "")
        val numericBalance = entry.availableBalance?.replace("[^0-9.]".toRegex(), "")
        return buildString {
            appendLine("{")
            appendLine("  \"verify_key\": ${jsonString(verificationKey.trim())},")
            appendLine("  \"txn_id\": ${jsonString(txnId)},")
            appendLine("  \"bank\": \"Indian Bank\",")
            appendLine("  \"direction\": ${jsonString(entry.direction)},")
            appendLine("  \"amount\": ${jsonStringOrNull(numericAmount)},")
            appendLine("  \"currency\": \"INR\",")
            appendLine("  \"account\": ${jsonStringOrNull(entry.accountMask)},")
            appendLine("  \"mode\": ${jsonStringOrNull(entry.paymentMode)},")
            appendLine("  \"reference\": ${jsonStringOrNull(entry.referenceNumber)},")
            appendLine("  \"balance\": ${jsonStringOrNull(numericBalance)},")
            appendLine("  \"sender\": ${jsonString(entry.smsSender)},")
            appendLine("  \"received_at_ms\": ${entry.receivedAt},")
            appendLine("  \"simulated\": ${entry.isSimulated}")
            append("}")
        }
    }

    private fun buildTestVerificationJson(
        verificationKey: String,
        txnId: String,
        timestampMs: Long
    ): String {
        return buildString {
            appendLine("{")
            appendLine("  \"verify_key\": ${jsonString(verificationKey)},")
            appendLine("  \"txn_id\": ${jsonString(txnId)},")
            appendLine("  \"bank\": \"Indian Bank\",")
            appendLine("  \"event\": \"CONNECTION_TEST\",")
            appendLine("  \"received_at_ms\": $timestampMs")
            append("}")
        }
    }

    private fun tableRow(
        label: String,
        value: String,
        bold: Boolean = false,
        mono: Boolean = false
    ): String {
        val weight = if (bold) "700" else "500"
        val family = if (mono) "font-family: monospace;" else ""
        return """
            <tr>
              <td style="padding: 6px 0; color: #64748b; border-bottom: 1px solid #f1f5f9;">${escapeHtml(label)}</td>
              <td style="padding: 6px 0; text-align: right; font-weight: $weight; $family color: #0f172a; border-bottom: 1px solid #f1f5f9;">${escapeHtml(value)}</td>
            </tr>
        """.trimIndent()
    }

    private fun formatTimestamp(epochMillis: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    private fun escapeHtml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun jsonStringOrNull(value: String?): String =
        if (value.isNullOrBlank()) "null" else jsonString(value)

    companion object {
        private const val GMAIL_SMTP_HOST = "smtp.gmail.com"
        private const val TIMEOUT_MS = "15000"

        const val VERIFY_BLOCK_START = "---BANKMAIL_VERIFY_START---"
        const val VERIFY_BLOCK_END = "---BANKMAIL_VERIFY_END---"
        const val HEADER_VERIFY_KEY = "X-BankMail-Verify-Key"
        const val HEADER_TXN_ID = "X-BankMail-Txn-Id"

        fun formatTransactionId(fingerprint: String): String {
            val clean = fingerprint.trim().uppercase()
            val shortHash = if (clean.length >= 16) clean.substring(0, 16) else clean.padEnd(16, '0')
            return "TXN-$shortHash"
        }
    }
}
