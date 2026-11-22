# BankMail — SMS to Mail Payment Gateway Tool

> **100% on-device, zero-backend Android app** that watches incoming bank transaction SMS alerts and forwards verified, structured transaction payloads directly to Gmail using a Google App Password.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org/)

**Repository:** [https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool](https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool)  
**Author:** [Soumyadip Paul](https://github.com/SoumyadipPaul01223322)

---

## Why BankMail?

Traditional SMS-to-payment-verification setups require hosting a backend server, exposing webhooks, or trusting third-party automation tools with your banking SMS. **BankMail runs completely on your Android phone**:

```text
Incoming Bank SMS (e.g. Indian Bank / Custom Bank)
   │
   ▼
BankMailMonitorService (Foreground Service + BroadcastReceiver + SMS Inbox Observer)
   │  └─ Parses amount, direction, mode, account mask, UTR & strictly blocks OTPs
   ▼
Local Room SQLite DB + WorkManager (Expedited + NetworkType.CONNECTED + Auto-Retry)
   │  └─ Deduplicates transactions & survives offline periods / reboots
   ▼
Direct Gmail SMTP (smtp.gmail.com:465 SSL with 587 STARTTLS fallback)
   │  └─ Credentials encrypted at rest via Android Keystore (AES-256-GCM)
   ▼
Your Gmail Inbox (Human-readable card + Signed Verification Key JSON block)
   │
   ▼
Your Payment Gateway / Expense App (Reads Gmail via IMAP & verifies `verify_key` + `txn_id`)
```

---

## Key Features

- **Zero Backend Server:** Connects directly from your Android phone to `smtp.gmail.com` using your 16-character Google App Password (Port `465` SSL with automatic Port `587` STARTTLS fallback).
- **Local Encrypted Storage:** Gmail credentials, App Password, and your App Verification Key are stored in a local Room SQLite database (`bankmail_local.db`) encrypted via hardware-backed **Android Keystore AES-256-GCM**, with Android cloud backup disabled (`allowBackup="false"`).
- **Indian Bank Ready (Customizable for Any Bank):** Handles TRAI DLT headers (`JD-INDBNK-S`, `VM-INDIBK-T`, `AD-INDBNK`, etc.), extracts Debit/Credit direction, amount, account/card mask, payment mode (`UPI`, `IMPS`, `NEFT`, `RTGS`, `ATM`, `Card`, `NACH`), reference/UTR, and available balance. Sender IDs can be edited directly in **Settings** without rebuilding.
- **Strict OTP & Promo Blocker:** Filters out OTPs, 2FA codes, MPIN/CVV alerts, and promotional bank messages by default so sensitive codes are never emailed.
- **Reliable Background Monitoring:** Uses a persistent `START_STICKY` Foreground Service (`specialUse`), dual `SMS_RECEIVED` broadcast + `content://sms/inbox` observer, `BootCompletedReceiver` auto-restart, and expedited `WorkManager` retries.
- **Tamper-Resistant App Integration Payload:** Embeds your unique **Verification Key** (`verify_key`), deterministic **Transaction ID** (`txn_id`), custom SMTP headers, and a machine-readable JSON block inside every forwarded email so downstream apps can verify genuine transactions and reject unrelated mail.
- **Built-In Testing Tools:** Includes **Send Test Email** and **Simulate Bank SMS** (UPI Debit, IMPS Credit, and OTP-block presets) right inside the app.

---

## Email Verification Format (For Payment Gateway / App Integration)

Every forwarded email is sent as `multipart/alternative` (`text/plain` + `text/html`) with:

- **Custom SMTP Headers:**
  - `X-BankMail-Verify-Key`: `<your verification key>`
  - `X-BankMail-Txn-Id`: `TXN-<16 uppercase hex chars>`
- **Subject Format:**
  - `[Indian Bank] Debited ₹500.00 (UPI) A/c XXXXX1234` (appends `[TEST]` when simulated)
- **Machine-Readable Verification Block (in both plain-text and HTML bodies):**

```text
---BANKMAIL_VERIFY_START---
{
  "verify_key": "BMK-9F4A2C8E1B7D4039A6C5E21F",
  "txn_id": "TXN-A1B2C3D4E5F60718",
  "bank": "Indian Bank",
  "direction": "DEBIT",
  "amount": "500.00",
  "currency": "INR",
  "account": "XXXXX1234",
  "mode": "UPI",
  "reference": "432198765432",
  "balance": "12345.67",
  "sender": "JD-INDBNK-S",
  "received_at_ms": 1763799310000,
  "simulated": false
}
---BANKMAIL_VERIFY_END---
```

**Connection test emails** include `"event": "CONNECTION_TEST"` (no `direction`/`amount`) so your consuming app can recognize test pings without recording a transaction.

### Recommended Verification Flow in Your Consuming App
1. Copy your **App Verification Key** from **BankMail → Settings** and save it in your app's config.
2. Read the `text/plain` part of incoming emails via Gmail IMAP (or decode HTML entities if reading `text/html`).
3. Extract the JSON strictly between `---BANKMAIL_VERIFY_START---` and `---BANKMAIL_VERIFY_END---`.
4. Verify `verify_key` matches your saved key (reject and ignore any other emails).
5. Deduplicate using `txn_id` as a unique key and ignore entries where `"simulated": true` in production.

---

## Build From Source

### Prerequisites
- **Android Studio** (or Android SDK with API 35 + **JDK 17 or JDK 21**)
- Android phone running **Android 8.0 (API 26) or newer**

### 1. Clone the Repository
```bash
git clone https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool.git
cd SMStoMAILPaymentGatewaytool
```

### 2. Run Unit Tests & Build a Debug APK
```bash
# Linux / macOS
./gradlew testDebugUnitTest assembleDebug

# Windows (PowerShell)
.\gradlew.bat testDebugUnitTest assembleDebug
```
Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`

### 3. Build Your Own Signed Release APK
1. Generate your own personal keystore (never commit `.jks` files or passwords):
   ```bash
   keytool -genkeypair -keystore bankmail-release.jks -storetype PKCS12 \
     -alias bankmail -keyalg RSA -sigalg SHA256withRSA -keysize 2048 -validity 10000
   ```
2. Copy [`keystore.properties.example`](keystore.properties.example) to `keystore.properties` and fill in your keystore password and alias (`keystore.properties` is already in `.gitignore`).
3. Build the R8-minified, resource-shrunk release APK:
   ```bash
   ./gradlew assembleRelease
   ```
   Release APK output: `app/build/outputs/apk/release/app-release.apk`

---

## Phone Setup Guide (Personal Sideload)

1. **Create a Google App Password:**
   - Turn on **2-Step Verification** on your Google account.
   - Open [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords), create an app password named `BankMail`, and copy the 16-character code.
2. **Install the APK (Recommended via USB `adb`):**
   - Installing via USB avoids messaging-app Play Protect sideload blocks and Android 13+'s restricted-settings lock on SMS permissions:
     ```bash
     adb install -r app/build/outputs/apk/release/app-release.apk
     ```
   - *If installing directly on the phone without USB:* Copy the APK to your Downloads folder (avoid installing directly from WhatsApp/Telegram). On Android 13+, open **Settings → Apps → BankMail → ⋮ (top-right) → Allow restricted settings** before granting SMS access.
3. **Configure Gmail & Verification Key:**
   - Open **BankMail → Settings**, enter your Gmail address and 16-character App Password, review or copy your **App Verification Key**, tap **Save to DB**, and tap **Test Email**.
4. **Enable Background Reliability (Important on Xiaomi / Realme / OnePlus / Vivo):**
   - In **Settings → Phone Permissions & Reliability**, set **Battery Optimization** to **Unrestricted**.
   - Tap **Autostart Settings** and enable **Autostart / Allow background activity** for BankMail.
   - Verify the quiet **"BankMail Active"** notification remains visible after swiping BankMail away from Recents.

---

## Customizing for Other Banks

- **Quick way (no code changes):** Open **Settings → Matched Sender IDs** in the app and add your bank's DLT sender codes (comma-separated, e.g. `HDFCBK,ICICIB,SBIINB,AXISBK,INDBNK`). BankMail automatically strips carrier prefixes (`JD-`, `VM-`, `AX-`) and TRAI DLT suffixes (`-S`, `-T`, `-P`, `-G`).
- **Custom regex / parser rules:** Edit [`IndianBankSmsParser.kt`](app/src/main/java/com/appmine/bankmail/domain/IndianBankSmsParser.kt) and add test cases in [`IndianBankSmsParserTest.kt`](app/src/test/java/com/appmine/bankmail/domain/IndianBankSmsParserTest.kt).

---

## Project Structure

```text
app/src/main/java/com/appmine/bankmail/
├── BankMailApp.kt                   # App singleton container & notification channels
├── MainActivity.kt                  # Edge-to-edge Material 3 Compose host & permissions
├── data/
│   ├── local/                       # Room SQLite DB, DAOs, entities & migrations
│   └── repository/                  # Coordinates DB, encryption, WorkManager & SMTP
├── domain/
│   └── IndianBankSmsParser.kt       # Bank DLT header matcher, field extractor & OTP blocker
├── mail/
│   └── GmailSmtpClient.kt           # Direct Gmail SMTP (465 SSL / 587 STARTTLS) + payload builder
├── security/
│   └── CryptoManager.kt             # Android Keystore AES-256-GCM encryption
├── service/
│   ├── BankMailMonitorService.kt    # Persistent START_STICKY Foreground Service + SMS observer
│   └── BootCompletedReceiver.kt     # Auto-restart after reboot / app update
├── sms/
│   └── SmsBroadcastReceiver.kt      # Multi-part SMS reassembly & background handoff
├── ui/
│   ├── BankMailViewModel.kt         # Unidirectional StateFlow UI state
│   ├── screens/                     # Transactions dashboard & Settings screens
│   └── theme/                       # Material 3 Light/Dark + Dynamic Color theme
└── worker/
    └── ForwardTransactionWorker.kt  # Expedited WorkManager worker with exponential retry
```

---

## Security & Privacy

See [SECURITY.md](SECURITY.md) for the security model, encryption details, and responsible disclosure guidelines.

- No telemetry, analytics, or external backend servers.
- No hardcoded credentials or signing keys in this repository.
- OTP / authentication SMS messages are blocked from forwarding by default.

---

## Contributing

Contributions, new bank SMS templates, and bug fixes are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a Pull Request.

---

## Credits & License

Created and open-sourced by **Soumyadip Paul** — [SMStoMAILPaymentGatewaytool](https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool).

Licensed under the [MIT License](LICENSE). You are free to use, modify, self-host, and build your own custom version.
