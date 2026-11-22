package com.appmine.bankmail.mail

import com.appmine.bankmail.data.local.TransactionDirection
import com.appmine.bankmail.data.local.TransactionLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailSmtpClientTest {

    private val client = GmailSmtpClient()

    private fun sampleEntry() = TransactionLogEntity(
        id = 1L,
        smsSender = "JD-INDBNK-S",
        smsBody = "Your A/c XXXXX1234 is debited by Rs.500.00 on 22-11-26 by UPI/432198765432. Avl Bal Rs.12,345.67. -Indian Bank",
        receivedAt = 1763796195000L,
        direction = TransactionDirection.DEBIT,
        amount = "₹500.00",
        accountMask = "XXXXX1234",
        paymentMode = "UPI",
        referenceNumber = "432198765432",
        availableBalance = "₹12,345.67",
        fingerprint = "a1b2c3d4e5f6071899887766554433221100aabbccddeeff0011223344556677",
        isSimulated = false
    )

    @Test
    fun plainAndHtmlBodiesIncludeVerificationKeyAndStructuredPayload() {
        val key = "BMK-9F4A2C8E1B7D4039A6C5E21F"
        val entry = sampleEntry()
        val expectedTxnId = "TXN-A1B2C3D4E5F60718"

        assertEquals(expectedTxnId, GmailSmtpClient.formatTransactionId(entry.fingerprint))

        val plain = client.buildTransactionPlainBody(entry, key)
        val html = client.buildTransactionHtmlBody(entry, key)

        assertTrue(plain.contains("Verification Key: $key"))
        assertTrue(plain.contains("Transaction ID: $expectedTxnId"))
        assertTrue(plain.contains(GmailSmtpClient.VERIFY_BLOCK_START))
        assertTrue(plain.contains(GmailSmtpClient.VERIFY_BLOCK_END))
        assertTrue(plain.contains("\"verify_key\": \"$key\""))
        assertTrue(plain.contains("\"txn_id\": \"$expectedTxnId\""))
        assertTrue(plain.contains("\"amount\": \"500.00\""))
        assertTrue(plain.contains("\"balance\": \"12345.67\""))

        assertTrue(html.contains(key))
        assertTrue(html.contains(expectedTxnId))
        assertTrue(html.contains(GmailSmtpClient.VERIFY_BLOCK_START))
        assertTrue(html.contains(GmailSmtpClient.VERIFY_BLOCK_END))
    }

    @Test
    fun escapesSpecialCharactersInVerificationKey() {
        val key = "MY\"KEY\\123"
        val json = client.buildTransactionVerificationJson(sampleEntry(), key)
        assertTrue(json.contains("\"verify_key\": \"MY\\\"KEY\\\\123\""))
    }
}
