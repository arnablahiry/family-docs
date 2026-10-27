# Family Docs (Android)

Private vault for your family's official documents (Aadhaar, PAN, passports, visas, residence permits and anything else) plus per-person passwords. Kotlin + Jetpack Compose, minSdk 26 (Android 8), targetSdk 35.

## Run it

1. Open this folder in **Android Studio** (Ladybug 2024.2 or newer). Let Gradle sync; it downloads Gradle 8.11.1 and the libraries.
2. Plug in a phone (or start an emulator) and press **Run**.
3. Unit tests: `./gradlew test` (OCR parser, country detection, backup crypto: 18 tests).

The app runs without any Google setup. "Continue with Google", passcode recovery and Drive backup need the one-time setup below.

## Building the APK

- **Android Studio:** Build → Generate Signed App Bundle / APK → APK → choose `familydocs-release.jks` (kept separately, see below) → release. The APK lands in `app/release/`.
- **Command line:** put `keystore.properties` and `familydocs-release.jks` in this folder, then run `./gradlew assembleRelease`. The output is `app/build/outputs/apk/release/app-release.apk`.
- **GitHub Actions:** push this folder to a private repo and add the secrets listed at the top of `.github/workflows/build-apk.yml`. Every push to `main` then builds a signed APK you can download from the run page.

## Shipping updates without losing anything

Android keeps all app data through an update when three things hold:

1. **Same signing key.** Always sign with `familydocs-release.jks`. If you lose it, phones can't take updates any more, and the only way forward is uninstalling, which wipes the vault. Keep the key and its password in two safe places, for example a password manager plus an offline copy.
2. **Higher versionCode.** Bump `familydocs.versionCode` (and `versionName`) in `gradle.properties` for every release.
3. **Database migrations.** The app never wipes the database on a schema change. If you change a table, add a `Migration` in `data/Migrations.kt` (instructions are in that file). As an extra safety net, `data/UpdateGuard.kt` copies the encrypted database before the first launch of each new version and keeps the last three copies.

Install updates by opening the new APK on the phone. Android shows "Update" rather than "Install", which means it worked. The Drive backup is a second safety net.

## Avoiding "unsafe app" warnings

- **Google Play Protect / developer verification:** Google now requires sideloaded apps to be registered by a verified developer. It's enforced in Brazil, Indonesia, Singapore and Thailand from 30 Sep 2026, and worldwide from 2027. For a family app, create a free **limited distribution** account in the Android Developer Console (email only, no ID, up to 20 devices). Register the package `com.familydocs.app` with the release key's SHA-256 (below). Registered apps install without warnings. Until then, Android may show a one-time "unknown app" or Play Protect prompt; tap *More details → Install anyway*.
- **What the app already does to look trustworthy:** it's release-signed (never debug), targets the latest Android (SDK 35), and asks for only Internet, biometrics and the camera app. There's no SMS, contacts, location, accessibility or background-start permission, nothing is downloaded or run at runtime, and there's no obfuscated networking.
- **Google sign-in screen:** the app only asks for the `drive.file` permission, which only covers files the app creates. That's a non-sensitive scope, so after you publish the OAuth consent screen (add an app name, your email and an icon), Google doesn't show the "Google hasn't verified this app" warning.

Release key fingerprints (for Google Cloud's Android OAuth client and for app registration):

- SHA-1: `20:73:FB:49:3D:04:18:C3:83:16:7A:1F:86:E5:60:A6:AF:B8:AC:03`
- SHA-256: `C0:12:03:6F:4F:A0:DE:80:A1:71:56:66:BF:0C:AD:A1:B8:6E:A6:84:1D:0C:DA:2C:56:6B:EC:7C:91:82:EC:6E`

## Google setup (sign-in photo, passcode recovery, Drive backup)

In [Google Cloud Console](https://console.cloud.google.com/):

1. Create a project, open **APIs & Services → OAuth consent screen**, choose *External* and add yourself as a test user.
2. **Enable the Google Drive API** (APIs & Services → Library). The app uses the non-sensitive `drive.file` scope.
3. **Credentials → Create credentials → OAuth client ID**:
   - *Android*: package `com.familydocs.app` plus the **release** SHA-1 above (add a second Android client with your debug SHA-1 if you also run from Android Studio). Get the SHA-1 from Android Studio's Gradle panel → `signingReport`, or run `keytool -list -v -keystore ~/.android/debug.keystore -storepass android`.
   - *Web application*: no extra settings. Copy its client ID.
4. Paste the **Web** client ID into `app/src/main/res/values/strings.xml` → `google_web_client_id`.

## Font (Abaddon)

All text uses Abaddon v1.2 (light + bold), bundled in `app/src/main/res/font/`, taken from your Albedo repo with its adjusted vertical metrics. The licence is CC BY 3.0 (see `FONT_LICENSE.txt`), so credit the font in an About screen if you publish the app.

## How it's put together

| Area | Where | Notes |
|---|---|---|
| Passcode | `security/PasscodeManager.kt` | 4 or 6 digits, PBKDF2-SHA256 hash. After 5 wrong tries input pauses for 30 s, then 60 s, 120 s… |
| Fingerprint | `security/BiometricHelper.kt` | BiometricPrompt (strong biometrics), passcode is always the fallback |
| Auto-lock | `security/AppLock.kt` | Locks after the chosen time in background; pickers and camera don't trigger it |
| Encryption | `security/CryptoManager.kt`, `data/AppDatabase.kt` | AES-256-GCM key in Android Keystore encrypts every file and photo; the database is SQLCipher with a Keystore-wrapped random key |
| Screenshots | `MainActivity.kt` | `FLAG_SECURE` on by default (Settings toggle) |
| Account | `ui/onboarding` | Name, phone number, **Google account (required)** for the backup email, Drive backup and profile photo, plus the **countries you keep documents for** |
| Forgot passcode | `ui/security/SecurityScreens.kt` | Sign in with Google as the backup email → set a new passcode. Documents are untouched |
| OCR | `ocr/TextReader.kt` (ML Kit, offline) → `ocr/DocumentParser.kt` | Aadhaar (Verhoeff check), PAN, passport / visa / residence-permit MRZ (ICAO check digits), voter ID, Indian DL, "Label: value" lines, IBAN. Every field stays editable |
| Icons from names | `domain/DocType.kt` | "Passport" → passport icon, "OCI card" → residence, unknown names → file icon + initials |
| Vault views | `ui/member/MemberScreen.kt`, `ui/components/DocVisuals.kt` | Cards (stacked wallet: first tap lifts a card and the cards below spring away, second tap opens), List, Icons |
| Common vault | `data/VaultRepository.kt`, `ui/member` | A shared vault (house icon) created with the account, for rent agreements, bills, home insurance and the Wi-Fi password. It can be renamed but not deleted. See the note below on sharing between phones |
| Countries | `domain/Country.kt`, `ui/components/Flag.kt`, `ui/components/CountryPicker.kt` | Every ISO country, with the phone's emoji flags. Picked at sign-up (searchable list) and per document under *Issued by* (searchable, your countries first; OCR pre-selects it). Cards show the flag; a vault with documents from more than one country gets show/hide pills |
| Backup | `backup/DriveBackup.kt`, `backup/DriveClient.kt` | `My Drive › Family Docs › <person> › <document · country>/`, with each document folder colour-coded by type and labelled with Drive appProperties (person, type, country, expiry). Files are AES-256-GCM encrypted with a key from your backup password (PBKDF2, 210k rounds). Only changed documents are re-uploaded, and deleted ones go to Drive's bin. Viewable unencrypted copies are optional and off by default. Runs weekly on Wi-Fi, or via Back up now. Restore on a new phone with the same password |

Android's own cloud backup is switched off (`allowBackup=false` plus data-extraction rules). The Keystore key can't leave the phone, so a copy made that way couldn't be decrypted. Use the in-app Drive backup instead.

## Known limits / next steps

- **Common vault sharing:** each phone has its own vault today, so "everyone can see and edit" only becomes true across phones once there's a sync layer. The planned design is a Drive folder shared with each family member's Google account, holding the Common vault encrypted with a family key. That way each person's own documents stay private. It isn't built yet.

- This was written without an Android SDK, so it has **not been compiled as an app yet**. The OCR parser and backup crypto *were* compiled and tested. Expect a small fix or two on the first Gradle sync, and send me the error text if anything doesn't build.
- Phone number isn't SMS-verified. That needs Firebase Auth, which is easy to add if you want it.
- OCR uses ML Kit's Latin model. Hindi text is ignored, but the English lines on Aadhaar/PAN carry everything the parser needs.
- The camera shot is written to app cache for a moment, then deleted right after it's read and encrypted.
