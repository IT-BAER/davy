# Changelog

## v1.1.2 (11.03.2026)

<en-US>
Hotfix v1.1.2

🔐 Authentication Fix
• Added "Re-authenticate with Nextcloud" button in account settings — re-establishes credentials via Nextcloud Login Flow V2 when app passwords are revoked server-side
• Fixed deep-link navigation not working when the app was already running
• Added informational hint for App Password accounts explaining the authentication method
</en-US>

<de-DE>
Hotfix v1.1.2

🔐 Authentifizierungsfix
• Schaltfläche „Erneut mit Nextcloud authentifizieren" in den Kontoeinstellungen hinzugefügt — stellt die Anmeldedaten über Nextcloud Login Flow V2 wieder her, wenn App-Passwörter serverseitig widerrufen wurden
• Deep-Link-Navigation korrigiert, wenn die App bereits im Vordergrund lief
• Informationshinweis für App-Passwort-Konten hinzugefügt, der die Authentifizierungsmethode erklärt
</de-DE>

## v1.1.1 (04.03.2026)

<en-US>
Hotfix v1.1.1

🔧 Critical Fix
• Fixed a bug introduced in v1.1.0 where upgrading from earlier versions could reset all app data (accounts, calendars, contacts, tasks). All existing user data is now safely preserved across updates.
• Added complete database migration path covering all versions from the initial release to the current schema (v7 → v19).
</en-US>

<de-DE>
Hotfix v1.1.1

🔧 Kritische Fehlerbehebung
• Fehler behoben, durch den beim Update von älteren Versionen alle App-Daten (Konten, Kalender, Kontakte, Aufgaben) zurückgesetzt werden konnten. Alle vorhandenen Benutzerdaten werden jetzt beim Update korrekt beibehalten.
• Vollständiger Datenbankmigrationsweg für alle Versionen seit der Erstveröffentlichung hinzugefügt (v7 → v19).
</de-DE>

## v1.1.0 (28.02.2026)

<en-US>
New in v1.1.0

✅ Enhanced Task (VTODO) Support
• Full CalDAV task sync with VTODO standard compliance
• 10 new task properties: location, URL, geo, organizer, recurrence (RRULE/RDATE/EXDATE), classification, categories, and related tasks
• Improved iCalendar parsing with robust DTSTART/DUE date handling
• Proper timezone support for task dates
• DURATION fallback when DUE date is missing
• Overdue task indicators in the task list

🔧 Technical Improvements
• Kotlin 2.0.21, Compose 1.7.6, Material 3 1.3.1 upgrade
• Database schema v19 with safe migration
• Improved enum handling for task status values
• Better compatibility with industry CalDAV standards
• Stability improvements and bug fixes
</en-US>

<de-DE>
Neu in v1.1.0

✅ Erweiterte Aufgaben-Unterstützung (VTODO)
• Vollständige CalDAV-Aufgabensynchronisation nach VTODO-Standard
• 10 neue Aufgaben-Eigenschaften: Ort, URL, Geo-Koordinaten, Organisator, Wiederholung (RRULE/RDATE/EXDATE), Klassifizierung, Kategorien und verknüpfte Aufgaben
• Verbesserte iCalendar-Analyse mit robuster DTSTART/DUE-Datumsverarbeitung
• Korrekte Zeitzonen-Unterstützung für Aufgabendaten
• DURATION-Fallback wenn kein Fälligkeitsdatum gesetzt ist
• Überfälligkeitsanzeige in der Aufgabenliste

🔧 Technische Verbesserungen
• Kotlin 2.0.21, Compose 1.7.6, Material 3 1.3.1 Upgrade
• Datenbankschema v19 mit sicherer Migration
• Verbesserte Enum-Verarbeitung für Aufgabenstatus-Werte
• Bessere Kompatibilität mit CalDAV-Industriestandards
• Stabilitätsverbesserungen und Fehlerbehebungen
</de-DE>

## v1.0.2 (30.12.2025)

<en-US>
New in v1.0.2

🌍 Complete Internationalization
• Added translations for all 15 languages
• Fully translated: ES, FR, PT, IT, NL, PL, RU, AR, HI, ID, JA, KO, TR, ZH

📝 Improved Content
• Translated Privacy Policy & Terms
• New Feedback & GitHub links
• Improved translations

🔧 Technical Improvements
• Fixed CI/CD dependencies
</en-US>

<de-DE>
Neu in v1.0.2

🌍 Vollständige Internationalisierung
• App jetzt in 15 Sprachen verfügbar
• Vollständig übersetzt: ES, FR, PT, IT, NL, PL, RU, AR, HI, ID, JA, KO, TR, ZH

📝 Verbesserte Inhalte
• Datenschutzerklärung & AGB übersetzt
• Neue Feedback- & GitHub-Links
• Verbesserte Übersetzungen

🔧 Technik
• Optimierung der CI/CD-Prozesse
</de-DE>

## v1.0.1 (25.11.2025)

<en-US>
New in v1.0.1

✨ New Features & Improvements
• Added Privacy Policy and Terms of Service
• New onboarding experience for first-time users
• "What's New" dialog after updates
• Send Feedback option in Settings
• Expanded FAQ with Open Source and Help sections
</en-US>

<de-DE>
Neu in v1.0.1

✨ Neue Funktionen & Verbesserungen
• Datenschutzerklärung und Nutzungsbedingungen
• Neues Onboarding für Erstnutzer
• "Was ist neu"-Dialog nach Updates
• Feedback-Option in den Einstellungen
• Erweiterte FAQ (Open Source & Hilfe)
</de-DE>
