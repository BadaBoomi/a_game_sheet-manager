# a_game_sheet-manager

Verwaltet Spielebögen von Spielen (typischerweise Würfelspiele)

## Über die App

Die **Spielbögen-Manager** App ermöglicht es, Papierbögen von Würfelspielen digital zu verwalten. Typische Anwendungsfälle sind Spiele wie Kniffel oder „Noch mal!", die Papierblöcke zum Aufzeichnen von Spielständen verwenden.

## Funktionen

### Vorlagen verwalten
- **Einscannen**: Fotografiere Papiervorlagen direkt mit der Kamera
- **Importieren**: Lade Bilder aus der Galerie als Vorlage
- **Verwalten**: Übersicht aller gespeicherten Vorlagen mit Löschfunktion

### Spiel spielen
- **Vorlage auswählen**: Starte ein neues Spiel auf Basis einer gespeicherten Vorlage
- **Zeichnen**: Fülle den Spielbogen per Touchscreen aus
  - Freie Wahl der **Stiftfarbe** (12 vordefinierte Farben)
  - Freie Wahl der **Strichbreite** per Schieberegler
  - **Rückgängig** (Undo) einzelner Striche
  - **Alles löschen** der gesamten Zeichnung
- **Automatisches Speichern**: Der Zwischenstand wird beim Verlassen der App automatisch gespeichert
- **Explizit speichern**: Manuelle Speicherung des Zwischenstands jederzeit möglich
- **Fortsetzen**: Beim nächsten Start einer Vorlage wird gefragt, ob das bestehende Spiel fortgesetzt oder ein neues begonnen werden soll

### Hall of Fame
- **Spiel beenden**: Speichere abgeschlossene Spielbögen dauerhaft in der Hall of Fame
- **Nicht veränderbar**: Gespeicherte Einträge sind schreibgeschützt
- **Übersicht**: Alle vergangenen Spiele sind einsehbar

## Technische Details

- **Sprache**: Kotlin
- **Mindest-Android-Version**: Android 8.0 (API 26)
- **Datenspeicherung**: SQLite-Datenbank (ohne externe ORM-Bibliothek)
- **Berechtigungen**: Kamera, Medienzugriff (nur für Bildimport)

## Projekt aufbauen

```bash
./gradlew assembleDebug
```

Voraussetzungen:
- Android Studio oder JDK 17+
- Android SDK (compileSdk 34)
- Internetverbindung für den ersten Build (Gradle-Abhängigkeiten)
