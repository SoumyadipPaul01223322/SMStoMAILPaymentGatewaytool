# Contributing to BankMail (SMStoMAILPaymentGatewaytool)

Thank you for your interest in contributing to [SMStoMAILPaymentGatewaytool](https://github.com/SoumyadipPaul01223322/SMStoMAILPaymentGatewaytool)!

## Getting Started

1. Fork the repository and clone your fork locally.
2. Open the project in Android Studio (JDK 17 or JDK 21, Android SDK API 35).
3. Run the unit test suite before making changes:
   ```bash
   ./gradlew testDebugUnitTest
   ```

## Adding Support for Another Bank's SMS Format

1. Inspect [`IndianBankSmsParser.kt`](app/src/main/java/com/appmine/bankmail/domain/IndianBankSmsParser.kt).
2. Add or refine regex rules while preserving **strict OTP blocking** and **promo filtering**.
3. Add anonymized unit tests (never use real account numbers, UTRs, or personal details!) in [`IndianBankSmsParserTest.kt`](app/src/test/java/com/appmine/bankmail/domain/IndianBankSmsParserTest.kt).
4. Verify all unit tests pass:
   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

## Pull Request Checklist

- [ ] No credentials, Gmail addresses, App Passwords, `.jks` keystores, `keystore.properties`, or `local.properties` files are included in your commits.
- [ ] All unit tests pass (`./gradlew testDebugUnitTest`).
- [ ] Release lint checks pass (`./gradlew lintVitalRelease` or `./gradlew assembleDebug`).
- [ ] Any changes to the email JSON payload format are documented in `README.md` and covered in `GmailSmtpClientTest.kt`.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
