# Zweiter Testbranch: direkte Auf-/Ab-Tasten und erzwungene Medienausgabe

Branch: `test/mfa-up-down-forced-media`, abgezweigt von `40e0d89` des ersten Testbranches.
Die Lauftexte, LIVE-Anzeige, Zeitformatierung und der Windows-Build sind enthalten.

**Aktueller Stand:** Der Branch enthält zusätzlich den [Abbruchtest](TEST_NAV_CANCEL.md).
Dafür ist `TEST_FORCE_MEDIA_NAVIGATION` jetzt standardmäßig **false**. Für den hier
beschriebenen erzwungenen Medien-/Tastentest den Schalter am PC auf **true** setzen und
neu bauen. Das frühere Paket `06baf80` verwendete noch `true` als Standard.

## Was dieser Test macht

- Direkte DSI-Ereignisse `KEY_MFW_UP` wählen die Navigationsansicht auf der Medienseite.
- `KEY_MFW_DOWN` wählt dort die Musikansicht. Wiederholte Eingaben derselben Richtung
  behalten die Ansicht; eine neue Zielführung startet wieder mit Navigation.
- `TEST_FORCE_MEDIA_NAVIGATION=true` erzwingt ab dem Start des HMI die AA-Ausgabe auf
  der **Medienseite**, auch wenn die Headunit eine native Navigation besitzt.
- Die ursprüngliche `DedicatedAudioControl`-Ersetzung wurde entfernt. BAP NEXT/PREVIOUS
  sowie Vor-/Rücklauf werden in diesem Branch nicht als Ansichtsumschaltung abgefangen.

Die Umschaltung setzt eine aktive AA-Audioquelle, AA-Verbindung und laufende AA-Zielführung
voraus; `SHOW_NAV`, `SHOW_MEDIA` und `MFA_UP_DOWN_SWITCH` müssen eingeschaltet sein.
Sie ändert den AAtoKombi-Inhalt auf der Medienseite. Die übergeordneten MFA-Menüseiten
werden weiterhin vom Kombiinstrument bedient; AAtoKombi schaltet diese Seiten nicht um.

## Was am Fahrzeug noch unbestätigt ist

Die Schnittstelle definiert direkte UP/DOWN-Kennungen. Daraus folgt nicht, dass die
Auf-/Ab-Tasten neben OK deines Golf diese Ereignisse tatsächlich an die Headunit senden.
Die normale `KeyListener`-Schnittstelle der bereitgestellten HMI benennt stattdessen
unter anderem die uneindeutigen Pfeilgruppen A/B; eigene `KEY_MFW_UP/DOWN`-Kennungen
fehlen dort. Deshalb beobachtet dieser Test die rohe DSI-KeyPanel-Schnittstelle,
bevor die konfigurationsabhängige Umwandlung eine Rolle spielt.

Sendet das Kombiinstrument beim Druck auf diese Tasten ausschließlich BAP-Befehle
oder verarbeitet es den Druck intern, kann dieser Test darauf nicht reagieren.
Er weicht dann **nicht** automatisch auf die Titelwechsel-Tasten oder die uneindeutigen
Pfeilgruppen A/B aus. Das wäre wieder die ungeklärte Belegung aus dem ersten Test.

Der Listener beobachtet Ereignisse; er unterdrückt keine Serienfunktion. Sendet das
Kombiinstrument zusätzlich einen BAP-Audiobefehl, bleibt dessen Serienwirkung erhalten.
Beim Test deshalb auch auf einen unbeabsichtigten Titelwechsel oder eine zusätzliche
MFA-Menüaktion achten. Die aktuell geöffnete MFA-Seite wird nicht separat abgefragt:
ein weitergeleitetes UP/DOWN kann bei aktiver AA-Musik/Zielführung auch die im Hintergrund
gespeicherte Ansicht ändern.

## Einstellungen am PC

Für den ursprünglichen Medien-/Tastentest in `jar/src/de/aatokombi/Config.java` einstellen:

```java
public static final boolean MFA_UP_DOWN_SWITCH = true;
public static final boolean TEST_FORCE_MEDIA_NAVIGATION = true;
public static final boolean TEST_LOG_MFA_KEYS = true;
```

`TEST_FORCE_MEDIA_NAVIGATION=false` und ein neuer Build stellen die automatische
AA-Pfadauswahl wieder her. Der Test ändert weder Codierung noch persistente Einstellungen
der Unit oder des Kombiinstruments. `ClusterCaps.isNavCapable()` meldet weiterhin das
echte Headunit-Feature; nur `useNativeNavigation()` für die AA-Ausgabe wird übersteuert.
Damit werden die serienmäßigen Navi-Listener nicht durch eine falsche Geräteerkennung
abgeschaltet. Der Override gilt auch für den PQ-Ausgabepfad; die physische Tastenfunktion
ist auf MQB, PQ und AID bislang ungetestet.

Ohne den Override verwendet eine nav-fähige Unit wieder die native Navi-Seite; die
neue Tasten-Umschaltung ist auf diesem Ausgabeweg nicht aktiv. Die Abbruchabfrage einer
AA-Route wird im aktuellen Stand über den zusätzlichen [Abbruchtest](TEST_NAV_CANCEL.md)
behandelt; die Darstellung des Menüs übernimmt weiterhin das Kombiinstrument.

## Fahrzeugtest und Protokoll

1. Gegen die eigenen Unit-Dateien bauen, JAR installieren und HMI/Unit neu starten.
   Ist das `libgal` der bisherigen Lauftext-Testversion schon installiert, muss für
   diese Änderung nur die JAR aktualisiert werden; `libgal` ist unverändert.
2. Im Stand AA verbinden, Musik und eine Route starten, dann im Kombiinstrument die
   Audio-/Medienseite öffnen. Auch auf einer nav-fähigen Unit sollen dort die AA-Hinweise erscheinen.
3. ↑ und ↓ einzeln drücken: erwartet ↑=Navigation und ↓=Musik. Anschließend die unteren
   Titelwechsel-Tasten prüfen und beobachten, ob neben dem Anzeigewechsel etwas anderes passiert.
4. Nach dem Test `/dev/shmem/aatokombi_keys.log` per FTP sichern. Ein Neustart löscht
   die RAM-Datei. Am bestehenden Toolbox-Dump wurde keine Erweiterung vorgenommen.

Das Protokoll enthält höchstens 128 Zeilen, dann beginnt es neu (rund 32 KiB maximal).
Es erfasst Zeit, numerische Keyboard-/Tasten-/Statuswerte und die ausgewählte Ansicht;
keine Namen, Titel, Telefonnummern oder Zeicheneingaben.

- `START ... UP=... DOWN=...`: aus der laufenden Firmware gelesene Kennungen.
- `ATTACHED`: KEY2-Benachrichtigung für die konfigurierte KeyPanel-Instanz angemeldet.
- `KEY ... selected=true`: bestätigtes UP/DOWN wurde für die Ansicht übernommen.
- `KEY ... selected=false`: andere Taste/Status, ungültige Daten oder Bedingungen nicht erfüllt.
- `DISABLED`: Konstanten fehlen oder sind uneindeutig; es werden keine geratenen Codes verwendet.
- `ATTACH FAILED` / `DSI error`: Anbindung oder Benachrichtigung konnte nicht eingerichtet werden.

Keine `KEY`-Zeile bei vorhandener `ATTACHED`-Meldung bedeutet zunächst nur, dass dieser
Listener kein passendes Lenkradereignis erhalten hat. Das Protokoll erlaubt die nächste
Zuordnungsprüfung, ersetzt aber keinen Mitschnitt des gesamten Fahrzeugbusses.

## Ursprüngliche Implementierung und Änderungen bis `06baf80`

- `DedicatedAudioControl.java` entfernt: die Serienklasse übernimmt wieder alle BAP-Audioaktionen.
- `MfaViewKeys.java` neu: eigener DSI-KeyPanel-Listener, an `AndroidAutoTarget` Start/Stop
  gekoppelt. Nutzt die konfigurierte Service-Instanz, entfernt eigene Listener/Notifications
  beim Stop und meldet sie nach Service-Wiederverbindung erneut an.
- `MfaKeyCodes.java` neu: liest die realen Runtime-Konstanten per Reflection. Nur
  `KBD_MFW`/`KBD_MFW_3GP`, gültige Daten und `KST_PRESSED` für die eindeutigen UP/DOWN-Codes
  werden akzeptiert. Titel-, A/B-, Links/Rechts-, Lautstärke- und Sprachtasten werden
  nicht als alternative UP/DOWN-Kennungen zugelassen.
- `MfaKeyLog.java` neu: begrenztes RAM-Protokoll für die unbestätigte physische Zuordnung.
- `MediaViewMode` auf explizite Auf-/Ab-Auswahl umgestellt; keine BAP-Parameter mehr.
- `ClusterCaps.useNativeNavigation()` und `AANavReader` trennen Ausgabe-Override und echte
  Navi-Erkennung. MQB-`CurrentStationInfo` berücksichtigt den Override; PQ berücksichtigt
  jetzt ebenfalls die gewählte Medien-/Navigationsansicht.
- `Config.java` um die drei obigen Schalter ergänzt. Bisherige Einstellwerte unverändert.
- Tests für Tastenfilter, Kontextbedingungen, wiederholte Anmeldung, Abmeldung,
  Service-Neuverbindung, teilweise fehlgeschlagene Anmeldung und uneindeutige Konstanten ergänzt.
  Routing-/Lauftexttests an die zweite Variante angepasst.
- README, Architektur- und Testdokumentation aktualisiert; diese Anleitung neu.
- Keine Änderungen an Shim, `libgal`, Toolbox oder Windows-Startern.

Die PC-Tests verwenden den echten Mod-Code mit simulierten HMI-/DSI-Schnittstellen.
Sie prüfen keine reale Tastenweiterleitung, CAN/BAP-Kommunikation oder Tacho-Darstellung.

Vollständiger JAR-Build und die Text-/Lauftext-/Fortschritts-/Navigations-/Tastentests
sind gegen die bereitgestellte HMI bestanden. Zusätzlich wurden ein separater Build
und die Routing-/Tastentests mit `TEST_FORCE_MEDIA_NAVIGATION=false` ausgeführt:
Die automatische MQB-/PQ-Ausgabe bleibt bei ausgeschaltetem Override erhalten.
Die nachfolgenden Änderungen und Prüfungen sind in [TEST_NAV_CANCEL.md](TEST_NAV_CANCEL.md) dokumentiert.
