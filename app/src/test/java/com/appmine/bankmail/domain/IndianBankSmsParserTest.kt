package com.appmine.bankmail.domain

import com.appmine.bankmail.data.local.GmailConfigEntity
import com.appmine.bankmail.data.local.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianBankSmsParserTest {

    private val defaultKeywords = GmailConfigEntity.DEFAULT_SENDER_KEYWORDS
        .split(",")
        .map { it.trim() }

    @Test
    fun parsesIndianBankUpiDebitWithDltSuffix() {
        val sms = "Your A/c XXXXX1234 is debited by Rs.500.00 on 22-11-26 by UPI/432198765432. Avl Bal Rs.12,345.67. -Indian Bank"
        val result = IndianBankSmsParser.evaluate(
            sender = "JD-INDBNK-S",
            body = sms,
            senderKeywords = defaultKeywords,
            blockOtps = true
        )

        assertTrue(result is SmsEvaluation.Matched)
        val parsed = (result as SmsEvaluation.Matched).parsed
        assertEquals("INDBNK", parsed.normalizedSender)
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals("₹500.00", parsed.amount)
        assertEquals("XXXXX1234", parsed.accountMask)
        assertEquals("UPI", parsed.paymentMode)
        assertEquals("432198765432", parsed.referenceNumber)
        assertEquals("₹12,345.67", parsed.availableBalance)
    }

    @Test
    fun parsesIndianBankImpsCredit() {
        val sms = "Your A/c *4821 is credited with INR 15,250.00 on 22-11-26 via IMPS Ref 99887766. Clear Bal: INR 42,100.50 - Indian Bank"
        val result = IndianBankSmsParser.evaluate(
            sender = "VM-INDIBK-T",
            body = sms,
            senderKeywords = defaultKeywords,
            blockOtps = true
        )

        assertTrue(result is SmsEvaluation.Matched)
        val parsed = (result as SmsEvaluation.Matched).parsed
        assertEquals(TransactionDirection.CREDIT, parsed.direction)
        assertEquals("₹15,250.00", parsed.amount)
        assertEquals("*4821", parsed.accountMask)
        assertEquals("IMPS", parsed.paymentMode)
        assertEquals("99887766", parsed.referenceNumber)
        assertEquals("₹42,100.50", parsed.availableBalance)
    }

    @Test
    fun parsesAtmWithdrawal() {
        val sms = "Rs. 2,500.00 withdrawn from ATM using Card XX4321 on 22-11-2026. Avl Bal Rs. 19,845.67 -Indian Bank"
        val result = IndianBankSmsParser.evaluate(
            sender = "AD-INDBNK",
            body = sms,
            senderKeywords = defaultKeywords,
            blockOtps = true
        )

        assertTrue(result is SmsEvaluation.Matched)
        val parsed = (result as SmsEvaluation.Matched).parsed
        assertEquals(TransactionDirection.DEBIT, parsed.direction)
        assertEquals("₹2,500.00", parsed.amount)
        assertEquals("ATM", parsed.paymentMode)
        assertEquals("XX4321", parsed.accountMask)
        assertEquals("₹19,845.67", parsed.availableBalance)
    }

    @Test
    fun blocksOtpMessagesEvenWithCurrencyAmount() {
        val otpSms = "Dear Customer, OTP for your Indian Bank transaction of Rs.5,000.00 is 482910. Valid for 5 mins. Do not share OTP with anyone."
        val result = IndianBankSmsParser.evaluate(
            sender = "JD-INDBNK-S",
            body = otpSms,
            senderKeywords = defaultKeywords,
            blockOtps = true
        )

        assertTrue(result is SmsEvaluation.Ignored)
    }

    @Test
    fun ignoresPromotionalBankSms() {
        val promoSms = "Congratulations! You have a pre-approved Indian Bank loan offer of Rs. 2,00,000. Apply now on IndSMART."
        val result = IndianBankSmsParser.evaluate(
            sender = "AX-INDBNK-P",
            body = promoSms,
            senderKeywords = defaultKeywords,
            blockOtps = true
        )

        assertTrue(result is SmsEvaluation.Ignored)
    }

    @Test
    fun ignoresNonIndianBankSms() {
        val otherSms = "Your A/c XX9999 is debited by Rs.250.00 on 22-11-26 via UPI. Avl Bal Rs.1,000.00 - OtherBank"
        val result = IndianBankSmsParser.evaluate(
            sender = "VM-OTHRBK-S",
            body = otherSms,
            senderKeywords = defaultKeywords,
            blockOtps = true
        )

        assertTrue(result is SmsEvaluation.Ignored)
    }
}
