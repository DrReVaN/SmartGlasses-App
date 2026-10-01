# Android-App über GitHub aktualisieren

## Einmaliger Umstieg

App **1.3.0** einmal manuell aus diesem GitHub-Release herunterladen und über die vorhandene App installieren. Nicht vorher deinstallieren: Die signierte APK verwendet denselben Schlüssel wie die bisher bereitgestellten lokalen APKs; Einstellungen und Benachrichtigungsfreigaben bleiben erhalten. Die separat erzeugten CI-Debug-APKs verwenden eine andere Signatur und gehören nicht zu dieser Update-Reihe.

## Danach

Beim Öffnen der App wird automatisch nach neueren Appversionen gesucht, auch ohne Verbindung zur Brille. Automatische Prüfungen sind auf einmal in sechs Stunden begrenzt. „Nach App-Updates suchen“ prüft sofort erneut. Eine neue Version bietet „App-Update herunterladen“ oder „Später / diese Version zurückstellen“ an. Später betrifft nur diese Version; ein höherer Versionscode wird wieder angeboten. Die manuelle Prüfung hebt die Zurückstellung auf.

Erst nach deiner Zustimmung lädt die App die APK. Sie prüft GitHub-Digests, Dateigröße und SHA-256, Paketname `com.test`, Versionsnummer/Versionscode, Mindest-Androidversion und dieselbe Signatur wie die installierte App. Ein inkompatibles oder beschädigtes Paket wird nicht zur Installation weitergegeben. Android zeigt abschließend seinen eigenen Installationsdialog. Die App installiert nichts stillschweigend.

Ab Android 8 kann einmalig „Aus dieser Quelle zulassen“ für Smartglasses erforderlich sein. Die App öffnet dafür nach Bestätigung die Android-Einstellungen; danach zurückkehren und „App-Update installieren“ wählen. Ein laufendes Brillen-Firmwareupdate muss zuerst abgeschlossen werden. Das Update-Paket erhält ausschließlich eine zeitweilige Lesefreigabe für den Android-Installer.

Versionsnummern und Android-Versionscodes steigen gemeinsam. App 1.3.0 hat Versionscode 7. Firmware- und Appversionen bleiben voneinander unabhängig. Bestehende Firmware-Releases sowie beide main-Branches bleiben unverändert.

## Veröffentlichungsablauf

Die offiziellen App-Releases heißen `app-vX.Y.Z` und enthalten `Smartglasses-App-X.Y.Z.apk` und die passende JSON-Beschreibung. GitHub veröffentlicht sie nach erfolgreichen Androidtests, Lint, Builds und Paketprüfung. Der Signierschlüssel bleibt lokal. Eine vorbereitete APK liegt mit ihrer Beschreibung unter `releases/android/X.Y.Z/`; die Versionsdateien werden niemals überschrieben.

Für die nächste Version: Versionsname und Versionscode erhöhen, Änderungen auf dem Dev-Branch committen, APK lokal mit dem bisherigen Schlüssel signieren und `tools/app_release.py --prepare <APK> --sdk <Android-SDK>` ausführen. Das Paket enthält den Quellcommit. Paketdateien anschließend auf den Dev-Branch committen und pushen. CI vergleicht alle nicht zur Signatur gehörenden APK-Inhalte mit ihrem eigenen Build, prüft die tatsächliche APK-Signatur/Version und veröffentlicht erst danach. Weitere Quelländerungen verlangen eine neue Version und ein neu signiertes Paket. Der vorbereitete Paketcommit darf außerhalb der Paketdateien keine App-/Buildänderungen enthalten.

Bei Erstveröffentlichung wird ein Entwurf angelegt. Uploads und Abschlussprüfung verwenden dessen numerische ID, damit eine verzögert sichtbare GitHub-Draft-Liste die Veröffentlichung nicht verhindert. Erst ein vollständiges, überprüftes Paket wird sichtbar. Versions-Tags behalten ihren ursprünglichen Quellstand. Bereits veröffentlichte APKs bleiben unverändert.

## Prüfumfang

Tests prüfen vollständige/fehlende Release-Dateien, Quellen-/Digest-/Versions-/Signaturbindung, höhere Android-Versionscodes, unveränderte und beschädigte Download-Caches, begrenzte Provider-Lesefreigaben und die bestehenden Firmware-OTA-Verbindungsabläufe auf Android 9 und 15. Die Veröffentlichung prüft ebenfalls die APK-Signatur mit Androids Werkzeugen. Der Installationsdialog und die systemseitige Übernahme müssen einmal auf dem echten Handy bestätigt werden.
