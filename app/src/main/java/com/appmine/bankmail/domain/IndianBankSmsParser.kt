package com.appmine.bankmail.domain

import com.appmine.bankmail.data.local.TransactionDirection
import java.security.MessageDigest

data class ParsedBankSms(
    val sender: String,
    val normalizedSender: String,
    val body: String,
    val direction: String,
    val amount: String?,
    val accountMask: String?,
    val paymentMode: String?,
    val referenceNumber: String?,
    val availableBalance: String?
)

sealed interface SmsEvaluation {
    data class Matched(val parsed: ParsedBankSms) : SmsEvaluation
    data class Ignored(val reason: String) : SmsEvaluation
}

/**
 * Evaluates incoming SMS messages for Indian Bank transactions.
 *
 * Handles TRAI DLT sender headers (e.g. JD-INDBNK, VM-INDBNK-S, AX-INDIBK-T),
 * extracts amount, account mask, payment mode, reference/UTR, and balance,
 * and strictly blocks OTP / authentication messages when [blockOtps] is true.
 */
object IndianBankSmsParser {

    // Strips TRAI 2-char telecom prefix (e.g. "JD-", "VM-") and optional DLT suffix ("-S", "-T", "-P", "-G")
    private val dltPrefixRegex = Regex("^[A-Z]{2}[- ]")
    private val dltSuffixRegex = Regex("[- ](S|T|P|G)$")

    private val otpPatterns = listOf(
        Regex("\\botp\\b", RegexOption.IGNORE_CASE),
        Regex("one[\\s-]*time[\\s-]*password", RegexOption.IGNORE_CASE),
        Regex("verification\\s+code", RegexOption.IGNORE_CASE),
        Regex("security\\s+code", RegexOption.IGNORE_CASE),
        Regex("do\\s+not\\s+share", RegexOption.IGNORE_CASE),
        Regex("\\bmpin\\b|\\btpin\\b|\\bcvv\\b", RegexOption.IGNORE_CASE),
        Regex("valid\\s+for\\s+\\d+\\s*(min|sec)", RegexOption.IGNORE_CASE),
        Regex("use\\s+\\d{4,8}\\s+(as|to|for)", RegexOption.IGNORE_CASE)
    )

    private val promoExcludePatterns = listOf(
        Regex("\\bpre-?approved\\b", RegexOption.IGNORE_CASE),
        Regex("\\bapply\\s+now\\b", RegexOption.IGNORE_CASE),
        Regex("\\bloan\\s+offer\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcredit\\s+card\\s+offer\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdownload\\s+indsmart\\b", RegexOption.IGNORE_CASE)
    )

    private val debitPatterns = listOf(
        Regex("\\bdebited\\b", RegexOption.IGNORE_CASE),
        Regex("\\bwithdrawn\\b", RegexOption.IGNORE_CASE),
        Regex("\\bspent\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpaid\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpurchase\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdeducted\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsent\\s+(rs\\.?|inr|₹)", RegexOption.IGNORE_CASE),
        Regex("\\bdr\\.?\\b", RegexOption.IGNORE_CASE)
    )

    private val creditPatterns = listOf(
        Regex("\\bcredited\\b", RegexOption.IGNORE_CASE),
        Regex("\\breceived\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdeposited\\b", RegexOption.IGNORE_CASE),
        Regex("\\brefund(ed)?\\b", RegexOption.IGNORE_CASE),
        Regex("\\breversed\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcr\\.?\\b", RegexOption.IGNORE_CASE)
    )

    private val transactionContextRegex = Regex(
        "(a/c|acct|account|card|upi|imps|neft|rtgs|atm|pos|bal|balance|txn|transaction|trf|transfer)",
        RegexOption.IGNORE_CASE
    )

    private val amountRegex = Regex(
        "(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val balanceRegex = Regex(
        "(?:avl\\.?\\s*bal(?:ance)?|avail\\.?\\s*bal(?:ance)?|clear\\s*bal(?:ance)?|bal(?:ance)?(?:\\s*is)?)\\s*[:\\-]?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9][0-9,]*(?:\\.\\d{1,2})?)",
        RegexOption.IGNORE_CASE
    )

    private val accountRegex = Regex(
        "(?:a/c|acct|account|card)\\s*(?:no\\.?|ending(?:\\s+with)?)?\\s*[:\\-]?\\s*([xX*]{1,10}\\d{3,6}|\\d{2,4}[xX*]{2,8}\\d{3,4}|[xX*]*\\d{4})",
        RegexOption.IGNORE_CASE
    )

    private val referenceRegex = Regex(
        "(?:utr|ref(?:\\s*no\\.?)?|txn\\s*(?:id|no\\.?)|upi/|imps/|neft/)\\s*[:\\-#]?\\s*([A-Za-z0-9]{6,22})",
        RegexOption.IGNORE_CASE
    )

    private val paymentModeMatchers: List<Pair<Regex, String>> = listOf(
        Regex("\\bupi\\b", RegexOption.IGNORE_CASE) to "UPI",
        Regex("\\bimps\\b", RegexOption.IGNORE_CASE) to "IMPS",
        Regex("\\bneft\\b", RegexOption.IGNORE_CASE) to "NEFT",
        Regex("\\brtgs\\b", RegexOption.IGNORE_CASE) to "RTGS",
        Regex("\\batm\\b", RegexOption.IGNORE_CASE) to "ATM",
        Regex("\\bdebit\\s*card\\b", RegexOption.IGNORE_CASE) to "Debit Card",
        Regex("\\bcredit\\s*card\\b", RegexOption.IGNORE_CASE) to "Credit Card",
        Regex("\\bpos\\b", RegexOption.IGNORE_CASE) to "POS / Card",
        Regex("\\becs\\b|\\bnach\\b|\\bauto[\\s-]*debit\\b", RegexOption.IGNORE_CASE) to "Auto-Debit / NACH",
        Regex("\\bcheque\\b|\\bchq\\b", RegexOption.IGNORE_CASE) to "Cheque"
    )

    fun normalizeSender(rawSender: String): String {
        val cleaned = rawSender.trim().uppercase()
        val withoutPrefix = dltPrefixRegex.replace(cleaned, "")
        return dltSuffixRegex.replace(withoutPrefix, "")
    }

    fun evaluate(
        sender: String,
        body: String,
        senderKeywords: List<String>,
        blockOtps: Boolean
    ): SmsEvaluation {
        val trimmedSender = sender.trim()
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) {
            return SmsEvaluation.Ignored("Empty SMS body")
        }

        val normalizedSender = normalizeSender(trimmedSender)
        val senderMatches = senderKeywords.isEmpty() || senderKeywords.any { keyword ->
            val upper = keyword.trim().uppercase()
            upper.isNotEmpty() && (
                normalizedSender.contains(upper) ||
                    trimmedSender.uppercase().contains(upper)
                )
        }

        val bodyMentionsIndianBank = trimmedBody.contains("indian bank", ignoreCase = true) ||
            trimmedBody.contains("indbank", ignoreCase = true)

        if (!senderMatches && !bodyMentionsIndianBank) {
            return SmsEvaluation.Ignored("Sender '$trimmedSender' did not match Indian Bank filters")
        }

        if (blockOtps && otpPatterns.any { it.containsMatchIn(trimmedBody) }) {
            return SmsEvaluation.Ignored("Blocked OTP / authentication SMS")
        }

        if (promoExcludePatterns.any { it.containsMatchIn(trimmedBody) }) {
            return SmsEvaluation.Ignored("Ignored promotional message")
        }

        val hasDebit = debitPatterns.any { it.containsMatchIn(trimmedBody) }
        val hasCredit = creditPatterns.any { it.containsMatchIn(trimmedBody) }
        val amountMatch = extractTransactionAmount(trimmedBody)
        val hasContext = transactionContextRegex.containsMatchIn(trimmedBody)

        if (!hasDebit && !hasCredit) {
            return SmsEvaluation.Ignored("No debit or credit keyword found")
        }
        if (amountMatch == null && !hasContext) {
            return SmsEvaluation.Ignored("No currency amount or account context found")
        }

        val direction = when {
            hasDebit && !hasCredit -> TransactionDirection.DEBIT
            hasCredit && !hasDebit -> TransactionDirection.CREDIT
            hasDebit && hasCredit -> {
                // Choose whichever verb appears first in the message
                val firstDebit = debitPatterns.mapNotNull { it.find(trimmedBody)?.range?.first }.minOrNull() ?: Int.MAX_VALUE
                val firstCredit = creditPatterns.mapNotNull { it.find(trimmedBody)?.range?.first }.minOrNull() ?: Int.MAX_VALUE
                if (firstDebit <= firstCredit) TransactionDirection.DEBIT else TransactionDirection.CREDIT
            }
            else -> TransactionDirection.OTHER
        }

        val balance = balanceRegex.find(trimmedBody)?.groupValues?.getOrNull(1)?.let { "₹$it" }
        val account = accountRegex.find(trimmedBody)?.groupValues?.getOrNull(1)?.uppercase()
        val mode = paymentModeMatchers.firstOrNull { it.first.containsMatchIn(trimmedBody) }?.second
        val reference = referenceRegex.find(trimmedBody)?.groupValues?.getOrNull(1)

        return SmsEvaluation.Matched(
            ParsedBankSms(
                sender = trimmedSender,
                normalizedSender = normalizedSender,
                body = trimmedBody,
                direction = direction,
                amount = amountMatch,
                accountMask = account,
                paymentMode = mode,
                referenceNumber = reference,
                availableBalance = balance
            )
        )
    }

    /**
     * Extracts the primary transaction amount while avoiding picking the balance figure
     * if balance appears later in the SMS.
     */
    private fun extractTransactionAmount(body: String): String? {
        val balanceStart = balanceRegex.find(body)?.range?.first ?: body.length
        val prefixBeforeBalance = body.substring(0, balanceStart)
        val amountInMainPart = amountRegex.find(prefixBeforeBalance)?.groupValues?.getOrNull(1)
        val fallbackAmount = amountRegex.find(body)?.groupValues?.getOrNull(1)
        val raw = amountInMainPart ?: fallbackAmount ?: return null
        return "₹$raw"
    }

    fun fingerprint(sender: String, body: String, receivedAtMillis: Long): String {
        val minuteBucket = receivedAtMillis / 60_000L
        val payload = "${normalizeSender(sender)}|${body.trim()}|$minuteBucket"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
