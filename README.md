<center>

# 📆 DAVy - CalDAV/CardDAV/WebCal Sync for Android

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![Google Play](https://img.shields.io/badge/Google%20Play-Download-blue.svg)](https://play.google.com/store/apps/details?id=com.davy)
[![API](https://img.shields.io/badge/API-35%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=35)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)


A privacy-focused Android application for synchronizing calendars and contacts using CalDAV and CardDAV protocols. Built with Jetpack Compose and Material 3 design.

</center>

## 🌟 Features

🔄 **Synchronization**

- CalDAV calendar sync (RFC 4791)

- CardDAV contact sync (RFC 6352)

- Bidirectional sync with Android Calendar and Contacts

- Background sync with configurable intervals

- Conflict detection and resolution

- Full and incremental sync support


💽 **Data Management**

- Multiple account support

- Calendar and address book management

- Contact integration with Android Contacts Provider

- CalDAV calendar sync (RFC 4791)

- Calendar integration with Android Calendar Provider

- Event and contact CRUD operations- CardDAV contact sync (RFC 6352)

- Offline data caching with Room

- Bidirectional sync with Android Calendar and Contacts

🛡️ **Security & Privacy**

- Encrypted credential storage (Android Keystore)

- TLS/SSL certificate validation

- No data collection or telemetry- Conflict detection and resolution

- Local data storage only

- No third-party analytics or tracking- Full and incremental sync support



🎨 **User Experience**

- Modern Material 3 design

- Dark mode support

- Intuitive account setup

- Per-collection sync control

- Multiple account support

- Calendar and address book management

## Requirements

- Android 14.0 (API level 35) or higher

- CalDAV/CardDAV compatible server (Nextcloud, ownCloud, Radicale, etc.)

- Internet connection for synchronization


## ⚙️ Installation

### ▶️ Play Store

Install directly from the [Play Store](https://play.google.com/store/apps/details?id=com.davy)


## 🚀 Quick Start



1. Launch DAVy

2. Add account with server URL and credentials

3. Configure sync settings (interval, WiFi-only, etc.)

4. Sync starts automatically



Your calendars will appear in the Android Calendar app, and contacts will appear in the Android Contacts app.


## 🏗️ Architecture

DAVy follows Android best practices with clean architecture:

- **UI Layer**: Jetpack Compose with Material 3, MVVM pattern

- **Domain Layer**: Use cases, business logic, and models

- **Data Layer**: Repositories, Room database, CalDAV/CardDAV clients- Android 14.0 (API level 35) or higher

- **Dependency Injection**: Hilt

- **Background Work**: WorkManager for periodic sync


### Technology Stack


**Android**

- Minimum SDK: 35 (Android 14.0)

- Target SDK: 36 (Android 14+)

- Compile SDK: 36

- Kotlin 1.9.24

- Android Gradle Plugin 8.13.0

**UI Framework**

- Jetpack Compose 1.6.1

- Material 3 1.2.0

- Compose Compiler 1.5.141


**Core Libraries**

- AndroidX Core 1.17.0

- Lifecycle 2.7.0

- Navigation Compose 2.7.7

- Room 2.6.1 (local database)

- WorkManager 2.9.0 (background sync)


**Networking**

- OkHttp 4.12.0

- Retrofit 2.11.0

- Moshi 1.15.22



**Protocol Support**

- ical4j 3.2.14 (iCalendar/CalDAV)

- ez-vcard 0.11.3 (vCard/CardDAV)


**Dependency Injection**

- Hilt 2.51.1

**Security**

- AndroidX Security Crypto 1.1.0


## Protocol Compliance



DAVy implements the following standards:



- **CalDAV**: RFC 4791 (Calendar Access Protocol)

- **CardDAV**: RFC 6352 (vCard Extensions to WebDAV)

- **WebDAV**: RFC 4918 (HTTP Extensions for Web Distributed Authoring and Versioning)

- **iCalendar**: RFC 5545 (Internet Calendaring and Scheduling Core Object Specification)

- **vCard**: RFC 6350 (Electronic Business Card Format) 



## Contributing



Contributions are welcome! Please:


1. Fork the repository

2. Create a feature branch (`git checkout -b feature/your-feature`)

3. Write tests for your changes

4. Ensure all tests pass

5. Follow the existing code style (ktlint, Detekt)

6. Commit your changes (`git commit -m 'Add your feature'`)

7. Push to your branch (`git push origin feature/your-feature`)

8. Open a Pull Request



## 🛡️ Privacy



DAVy respects your privacy:



- No data collection or telemetry

- No analytics or tracking services

- All data stored locally on your device

- Direct communication only with your configured servers

- No third-party SDKs or services

See [Privacy Policy](docs/privacy-policy.md) for complete details.

## 💜 Support Development

If you find this project useful, consider supporting this and future work, which heavily relies on Coffee:

<div align="center">
<a href="https://www.buymeacoffee.com/itbaer" target="_blank"><img src="https://github.com/user-attachments/assets/64107f03-ba5b-473e-b8ad-f3696fe06002" alt="Buy Me A Coffee" style="height: 60px; max-width: 217px;"></a>
<br>
<a href="https://www.paypal.com/donate/?hosted_button_id=5XXRC7THMTRRS" target="_blank">Donate via PayPal</a>
</div>

## 📜 License

Licensed under the Apache License, Version 2.0









