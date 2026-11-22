# Security Policy

## Security Architecture

**BankMail** ([SMStoMAILPaymentGatewaytool](https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool)) is designed for personal, self-hosted Android use with a strict local-only trust model:

- **Zero Backend Server:** SMS messages and credentials never pass through any developer-operated server. Emails are sent directly from the user's device to `smtp.gmail.com` over TLS 1.2/1.3 (Port 465 SSL or Port 587 STARTTLS).
- **Encrypted Credential Storage:** The user's Google App Password and App Verification Key are encrypted using **AES-256-GCM** with keys stored in the hardware-backed **Android Keystore** before being written to the local Room SQLite database.
- **Backup Exclusion:** `android:allowBackup="false"`, `backup_rules.xml`, and `data_extraction_rules.xml` exclude the local database and preferences from cloud backups and device transfers.
- **OTP Protection:** Messages matching OTP, 2FA, MPIN, TPIN, CVV, or "do not share" patterns are blocked from being emailed by default.
- **Scoped Verification Key:** Downstream apps should always verify `verify_key` inside the `---BANKMAIL_VERIFY_START--- ... ---BANKMAIL_VERIFY_END---` payload and deduplicate on `txn_id` to prevent email spoofing or replay.

## User Best Practices

- Never commit `keystore.properties`, `*.jks`, `local.properties`, or personal Gmail App Passwords to Git.
- Use a dedicated **Google App Password** that can be revoked anytime from [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
- Keep **Block OTP messages** enabled in Settings.
- Keep your **App Verification Key** private and rotate it from Settings if ever exposed.

## Reporting a Vulnerability

If you discover a security vulnerability in this repository, please report it privately via GitHub Security Advisories or open an issue without posting sensitive credentials at:
[https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool/issues](https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool/issues)
