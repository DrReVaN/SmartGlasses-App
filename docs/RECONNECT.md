# Wiederverbindung in App 1.2.1

Der Verbindungsdienst läuft unabhängig vom geöffneten App-Bildschirm. Nach einem Reichweitenabbruch versucht er die gespeicherte Brille zuerst mit steigenden Abständen zu erreichen. Ab dem neunten Fehlschlag folgen weitere Versuche einmal pro Minute, bis die Verbindung gelingt oder ausdrücklich beendet wird. Der Zähler bleibt begrenzt. Jede erfolgreiche Profil-/Versionsprüfung setzt ihn zurück.

„Verbindung beenden“, das Ausschalten von Bluetooth, Berechtigungsentzug und ein Wechsel der ausgewählten Brille machen geplante Versuche ungültig. Ein veralteter Rückruf kann keine neue Sitzung schließen. Eine unterbrochene OTA-Übertragung bleibt abgebrochen; automatische Verbindung überträgt keine Firmware. Auch eine nach acht Versuchen nicht bestätigte OTA-Installation entsperrt weiterhin die Bedienelemente.

Android kann die Ausführung im Ruhemodus verzögern. Wenn die Brille selbst kein Bluetooth-Signal mehr sendet, kann die App das nicht beheben: dafür Firmware 0.3.1 installieren. Bei alter Firmware hilft vorübergehend das vollständige Trennen der Brillenversorgung für zehn Sekunden. Nach erfolgreicher Wiederverbindung wird die Uhrzeit erneut mit dem Handy synchronisiert.

Geprüft: Android 9 und 15 mit gesteuerten GATT-Sitzungen, erneute Versuche nach ausgeschöpfter Anfangsserie, explizites Beenden und die bestehenden OTA-Fehler-/Abschlussprüfungen. Ein realer Reichweiten- und Übernachttest erfolgt auf der Brille nach Installation.
