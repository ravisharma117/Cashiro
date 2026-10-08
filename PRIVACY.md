# Privacy Policy

**Last Updated: October 2026**

## Our Commitment to Privacy

Cashiro is built with privacy as the core principle. We believe your financial data should remain yours alone.

## 100% On-Device Processing

**All data processing happens locally on your device.** We use MediaPipe's on-device LLM (Qwen 2.5) for AI features, ensuring:

- ✅ **No cloud servers** - Your financial data never leaves your phone (the one optional exception is the PIN recovery email, described below)
- ✅ **No data collection** - We don't collect, store, or transmit any user data
- ✅ **No tracking** - No analytics, no telemetry, no user tracking
- ✅ **No ads** - No advertising networks or tracking pixels
- ✅ **Offline AI** - Once downloaded, AI works completely offline

## Optional: PIN Recovery Email

Paisa IQ can lock the app with a 4-digit PIN. The PIN is stored only on your phone, as a salted hash in encrypted storage. It is never sent anywhere.

If you choose to add a **recovery email**, so that a forgotten PIN can be reset, this is the one feature that contacts a server. It is off unless you set it up, and it does nothing while you are offline.

- **What is sent:** the recovery email address (so the code can be mailed to it), the 6-digit code you type back, and a random install id made on your phone. Nothing about your transactions, accounts or PIN is sent.
- **Where it goes:** the NAX IT Solutions API (`naxits-api.netlify.app`), which sends the email through its mail provider.
- **What the server keeps:** it does not store your email address. To limit abuse it keeps only scrambled (keyed hash) forms of the address, the code and your network address for 24 hours, then deletes them automatically.
- **Where the address lives:** on your phone, in encrypted storage, so the app can offer to send the code to it. You can remove it at any time in Security.
- **No recovery email:** if you do not add one, nothing is ever sent, and a forgotten PIN can only be fixed by clearing the app data, which deletes everything that is not backed up.

## Data Storage

### What We Store (Locally Only)
- Transaction details extracted from SMS (amount, merchant, date, category)
- Your custom categories and notes
- App preferences and settings

### Where It's Stored
- All data is stored in a local SQLite database on your device
- Database is protected by Android's app sandboxing
- Data is only accessible to Cashiro app

### Data Deletion
- Uninstalling the app completely removes all data
- You can delete individual transactions at any time
- Export your data before uninstalling if you want to keep records

## Permissions

### SMS Permission (Read-Only)
- **Purpose**: To read bank transaction SMS messages
- **Scope**: Read-only access, we cannot send or modify messages
- **Processing**: SMS parsing happens entirely on-device
- **Storage**: Only transaction data is extracted and stored, not full messages

### Internet Permission
- **Primary Purpose**: To download the AI model (Qwen 2.5) on first use
- **Model Download**: One-time download of ~1.5GB model file from CloudFront CDN
- **App Updates**: Google Play Store variant uses Play Services for app updates (F-Droid variant does not)
- **After Model Download**: AI works completely offline, no internet required for core features
- **Your Data**: Never transmitted over the internet, all processing remains on-device

### Contacts Permission (Optional, Off by Default)
- **Purpose**: If you switch on contact matching under Lending & Borrowing, the app can look up the name of a contact from a phone number that appears in a payment, to recognise who paid you back
- **How it is used**: One number at a time, only when a new payment contains a phone number and you have saved people in Lending & Borrowing
- **Never stored**: The contact name is used for the comparison and then dropped. Your contact list is never read in bulk, copied, logged or sent anywhere
- **Your control**: The app works fully without it; deny or revoke the permission at any time

### No Other Permissions Required
- No location tracking
- No other contact access
- No camera or microphone access

## Third-Party Services

Cashiro does **NOT** use:
- ❌ Cloud services or APIs (except CDN for model download)
- ❌ Analytics services (Google Analytics, Firebase, etc.)
- ❌ Crash reporting services
- ❌ Advertising networks
- ❌ Social media SDKs
- ❌ Payment processors

**Note**: The Google Play Store variant includes Play Services for app updates only. The F-Droid variant has no Google services.

## AI Features

### On-Device AI Assistant
- Uses MediaPipe's Qwen 2.5 model (1.5GB download)
- Model runs entirely on your device using MediaPipe LLM Inference
- After initial download, no internet connection required
- Conversations are not stored or transmitted
- AI insights are generated locally from your local transaction data
- Model file stored in app's private storage

## Data Export

When you export your data:
- CSV/PDF files are created locally on your device
- You control where to share or save them
- No automatic uploads or backups

## Open Source Transparency

Cashiro is fully open source:
- Review our code at [GitHub](https://github.com/ritesh-kanwar/Cashiro)
- Verify our privacy claims yourself
- Contribute to make it even better

## Children's Privacy

Cashiro is not directed at children under 13. We do not knowingly collect information from children.

## Changes to Privacy Policy

Any changes to this privacy policy will be:
- Updated in the app repository
- Reflected in the "Last Updated" date
- Communicated through release notes

## Contact

For privacy concerns or questions:
- Open an issue on [GitHub](https://github.com/ritesh-kanwar/Cashiro/issues)

## Summary

**Your financial data stays on your phone. Period.**

- No servers
- No uploads
- No tracking
- No ads
- Complete privacy

---

*Cashiro - Privacy-first expense tracking with on-device AI*