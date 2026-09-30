# App 1.2.0: Firmwareversionen und Rückwechsel

Die App prüft beim Verbinden und bei laufender Verbindung alle sechs Stunden die öffentliche Versionsliste von `DrReVaN/Glasses_V0.1_BLE`. Sie führt keine automatische Firmwareinstallation aus. „Neue Version auswählen“ lädt nach Bestätigung das geprüfte Paket; „Update starten“ und die Pad-1-Bestätigung sind weiterhin nötig. „Später“ verschiebt das Angebot dieser konkreten Version. Die manuelle Updateprüfung hebt diese Zurückstellung auf.

„Verfügbare Versionen / Zurücksetzen“ enthält vollständige ältere und neue Release-Paare, numerisch nach Major.Minor.Patch sortiert. Heruntergeladene und verifizierte Pakete sowie die letzte Versionsliste bleiben im privaten App-Speicher erhalten. Ein Rückwechsel ist ein vollständiger OTA-Upload; die Brille besitzt keinen zweiten Anwendungsslot für automatischen Rücksprung nach Abbruch.

Firmware 0.3.0 bindet die Versionsnummer an ein `SGV1`-Feld im Image bei Offset 0x140. Die App kontrolliert diese Verbindung zusätzlich zu Ziel, Format, Startvektoren, Größe, SHA-256 und CRC. Nach dem Update vergleicht sie die gelesene Geräteversion, Imagegröße und CRC. Eine andere Version oder Prüfsumme darf keine Erfolgsmeldung auslösen.

Das Discovery-Protokoll 0,2,0 bleibt in den ersten Bytes erhalten; neue Firmware ergänzt eine echte Releaseversion. Die zwanzig Byte lange Antwort verändert keine UUIDs oder Handles. App 1.2.0 unterstützt diese sowie die ältere vier Byte lange Antwort. Der bisherige Bootloader bleibt verwendbar. Zuerst diese App installieren, dann Firmware 0.3.0 wählen.

Der auswählbare Altstand 0.2.0 ist exakt die zuvor bestätigte OTA-Löschkorrektur `f55c081`. Frühere 0.2.0-Builds meldeten alle dieselben Discoverybytes; ihre Identität ist am Gerät nicht eindeutig lesbar. Deshalb bezeichnet die App den Altstand entsprechend und bietet keine vermischten Pakete mit derselben Versionsnummer an.

Die lokale Debug-APK 1.2.0 ist mit derselben lokalen Entwicklungssignatur wie die bisher übergebenen APKs gebaut. GitHub-CI-Debug-APKs besitzen eine andere Signatur. Die Release-Variante kann über die vorhandenen `SMARTGLASSES_*`-Umgebungsvariablen mit dem Eigentümerschlüssel gebaut werden; kein privater Signierschlüssel wird veröffentlicht.

Die App benötigt INTERNET als normale Android-Berechtigung. Downloads verwenden HTTPS ausschließlich zu GitHub und den offiziellen GitHub-Asset-Hosts, begrenzte Größen und Zeitlimits. Release-Assets müssen zur Versionsnummer und zum Repository passen. Es werden keine Benachrichtigungstexte an GitHub gesendet. SHA-256/CRC und HTTPS stellen Paketkonsistenz sicher; es gibt kein unabhängiges Firmware-Signatursystem.

Robolectric-Tests auf SDK 28/35 prüfen Versionssortierung, falsch bezeichnete Images, unvollständige Releases, erlaubte Downloadadressen, Paketkorruption, Offline-Rückwechsel, Zurückstellung, Prüfhäufigkeit, fehlende automatische Installation und die echte OTA-Service-Erfolgskontrolle. Eine Hardwareprüfung des neuen Katalog- und Rückwechselablaufs bleibt separat erforderlich.
