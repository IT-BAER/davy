# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.3] - 2026-03-12

<en-US>
### Fixed
- Fixed false "authentication failed" notifications appearing even when sync completes successfully
- Removed spurious notification triggers from CalDAV/CardDAV principal discovery
- Improved HTTP error classification to prevent false positives from unrelated error messages containing status codes
</en-US>

<de-DE>
### Behoben
- Falsche „Authentifizierung fehlgeschlagen"-Benachrichtigungen behoben, die trotz erfolgreicher Synchronisation angezeigt wurden
- Fehlerhafte Benachrichtigungsauslöser aus CalDAV/CardDAV Principal Discovery entfernt
- Verbesserte HTTP-Fehlerklassifizierung zur Vermeidung von Fehlalarmen
</de-DE>

## [1.1.2] - 2026-03-11

<en-US>
### Added
- "Re-authenticate with Nextcloud" button in account settings — re-establishes credentials via Nextcloud Login Flow V2 when app passwords are revoked server-side
- Informational hint for App Password accounts explaining the authentication method

### Fixed
- Deep-link navigation not working when the app was already running
</en-US>

<de-DE>
### Hinzugefügt
- Schaltfläche „Erneut mit Nextcloud authentifizieren" in den Kontoeinstellungen — stellt die Anmeldedaten über Nextcloud Login Flow V2 wieder her
- Informationshinweis für App-Passwort-Konten

### Behoben
- Deep-Link-Navigation korrigiert, wenn die App bereits im Vordergrund lief
</de-DE>

## [1.1.1] - 2026-03-04

<en-US>
### Fixed
- Fixed a bug introduced in v1.1.0 where upgrading from earlier versions could reset all app data (accounts, calendars, contacts, tasks)
- Added complete database migration path covering all versions from the initial release to the current schema (v7 → v19)
</en-US>

<de-DE>
### Behoben
- Fehler behoben, durch den beim Update von älteren Versionen alle App-Daten zurückgesetzt werden konnten
- Vollständiger Datenbankmigrationsweg für alle Versionen seit der Erstveröffentlichung (v7 → v19)
</de-DE>

## [1.1.0] - 2026-02-28

<en-US>
### Added
- Full CalDAV task sync with VTODO standard compliance
- 10 new task properties: location, URL, geo, organizer, recurrence (RRULE/RDATE/EXDATE), classification, categories, and related tasks
- Overdue task indicators in the task list
- DURATION fallback when DUE date is missing

### Changed
- Kotlin 2.0.21, Compose 1.7.6, Material 3 1.3.1 upgrade
- Database schema v19 with safe migration
- Improved iCalendar parsing with robust DTSTART/DUE date handling
- Proper timezone support for task dates
- Improved enum handling for task status values
- Better compatibility with industry CalDAV standards
</en-US>

<de-DE>
### Hinzugefügt
- Vollständige CalDAV-Aufgabensynchronisation nach VTODO-Standard
- 10 neue Aufgaben-Eigenschaften: Ort, URL, Geo-Koordinaten, Organisator, Wiederholung, Klassifizierung, Kategorien und verknüpfte Aufgaben
- Überfälligkeitsanzeige in der Aufgabenliste
- DURATION-Fallback wenn kein Fälligkeitsdatum gesetzt ist

### Geändert
- Kotlin 2.0.21, Compose 1.7.6, Material 3 1.3.1 Upgrade
- Datenbankschema v19 mit sicherer Migration
- Verbesserte iCalendar-Analyse mit robuster Datumsverarbeitung
- Korrekte Zeitzonen-Unterstützung für Aufgabendaten
- Bessere Kompatibilität mit CalDAV-Industriestandards
</de-DE>

## [1.0.2] - 2025-12-30

<en-US>
### Added
- Translations for all 15 languages (ES, FR, PT, IT, NL, PL, RU, AR, HI, ID, JA, KO, TR, ZH)
- Translated Privacy Policy & Terms
- New Feedback & GitHub links

### Fixed
- CI/CD dependency issues
</en-US>

<de-DE>
### Hinzugefügt
- App jetzt in 15 Sprachen verfügbar
- Datenschutzerklärung & AGB übersetzt
- Neue Feedback- & GitHub-Links

### Behoben
- CI/CD-Prozesse optimiert
</de-DE>

## [1.0.1] - 2025-11-25

<en-US>
### Added
- Privacy Policy and Terms of Service
- Onboarding experience for first-time users
- "What's New" dialog after updates
- Send Feedback option in Settings
- Expanded FAQ with Open Source and Help sections
</en-US>

<de-DE>
### Hinzugefügt
- Datenschutzerklärung und Nutzungsbedingungen
- Onboarding für Erstnutzer
- "Was ist neu"-Dialog nach Updates
- Feedback-Option in den Einstellungen
- Erweiterte FAQ (Open Source & Hilfe)
</de-DE>
