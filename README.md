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
- **Direktstart aus Vorlagenübersicht**: Starte ein neues Spiel direkt neben einer Vorlage
- **Zeichnen**: Fülle den Spielbogen per Touchscreen aus
  - Freie Wahl der **Stiftfarbe** (12 vordefinierte Farben)
  - Freie Wahl der **Strichbreite** per Schieberegler
  - **Rückgängig** (Undo) einzelner Striche
  - **Alles löschen** der gesamten Zeichnung
  - **Zoom-Modus**: Beim Hineinzoomen wird die Strichbreite automatisch mit dem Zoomfaktor multipliziert (z. B. Zoom 2,5× → Strichbreite 2,5×). Beim Verlassen des Zoom-Modus wird die ursprüngliche Strichbreite wiederhergestellt.
- **Automatisches Speichern**: Der Zwischenstand wird beim Verlassen der App automatisch gespeichert
- **Spielmenü (3 Optionen)**:
  - **Neustart**: Setzt den aktuellen Spielbogen auf eine leere neue Runde zurück
  - **In Hall of Fame speichern**: Öffnet den Namensdialog und speichert den Spielbogen dauerhaft in der Hall of Fame
  - **Beenden**: Speichert den aktuellen Stand und beendet das Spiel direkt
- **Menü verschieben (Drag & Drop)**: Das schwebende 2×2-Menü kann per Long-Press von jedem der vier Icons aus verschoben werden (auch innerhalb der Menüfläche)
- **Fortsetzen**: Beim nächsten Start einer Vorlage wird gefragt, ob das bestehende Spiel fortgesetzt oder ein neues begonnen werden soll

#### Bedienung im Querformat
- Das Spielblatt selbst bleibt in fester Ausrichtung, um unbeabsichtigtes Drehen zu vermeiden.
- Die vier schwebenden Steuer-Icons bleiben als 2×2-Block an ihrer Position und passen ihre Ausrichtung an.
- Das Spielmenü bleibt bei drei Optionen: **Neustart**, **In Hall of Fame speichern**, **Beenden**.
- Drag & Drop des Menü-Blocks funktioniert per Long-Press von jedem Icon aus.
- Der Namensdialog für Hall of Fame wird in einer zur Tastatur passenden Ausrichtung angezeigt.

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
