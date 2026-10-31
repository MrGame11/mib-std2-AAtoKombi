# Test: AA-Zielführung über das MFA-Abbruchmenü beenden

Branch: `test/mfa-up-down-forced-media`. Ergänzung gegenüber `06baf80`.
Build-Grundlage sind die bereitgestellten HMI-/GAL-Dateien der VW MST2-ZR-Unit.
`main` und der erste Lauftext-Testbranch werden durch diese Änderung nicht aktualisiert.

## Was der Test macht

Der native MQB-Navi-Befehl `RG_ActDeact` (BAP-Funktion **34**, `controlType=1`)
erreicht AAtoKombi nach Bestätigung von „Zielführung stoppen“. Bei aktiver AA-Zielführung
auf der nativen Navi-Seite sendet der Test einmalig:

```java
dsiAndroidAuto2.navFocusNotification(Constants.NAVFOCUS_NATIVE, true);
// Native = 1; true = unaufgeforderte Fokusmitteilung der Headunit.
```

Genau diesen DSI-Aufruf verwendet die Serienklasse `NavigationListener.updateGuidanceActive(true)`,
wenn VW-Navigation aktiv wird. Das passt zur Vermutung, dass ein Start der VW-Zielführung
Maps beendet. **Es bleibt ein Fokuswechsel, kein nachgewiesener universeller Stop-Befehl.**
Ob Maps/Waze auf der konkreten Telefon-/AA-Version die Route tatsächlich beenden oder
nur die Weitergabe ihrer Navigationsdaten einstellen, muss am Fahrzeug geprüft werden.
Es wird keine künstliche VW-Route gestartet. Direkte UP/DOWN- oder Titelwechsel-Ereignisse
lösen diesen Abbruch nicht aus; das Bestätigungsmenü selbst bleibt Sache des Kombiinstruments.

## Rückmeldung und Fehlerverhalten

1. Bis zur Rückmeldung bleibt der tatsächliche AA-Zustand für die Anzeige maßgeblich.
2. Eine **neue** IPC-Sequenz mit Status `INACTIVE=2` oder `UNAVAILABLE=0` aus derselben
   AA-Sitzung bestätigt das Ende der von AA gemeldeten Zielführung. Der Reader räumt die
   Anzeige auf; BAP erhält danach Ergebnis `0` (erfolgreich).
3. `ACTIVE=1`, `REROUTING=3`, unbekannte Statuswerte oder eine unveränderte Sequenz sind
   keine Bestätigung. Ein Verbindungsabbruch oder Sitzungswechsel räumt die Anzeige wie
   bisher auf, zählt aber ebenfalls nicht als Nachweis, dass Maps seine Route beendet hat.
4. Nach **fünf Sekunden** ohne Bestätigung wird beim nächsten Reader-Durchlauf Ergebnis
   `1` (nicht erfolgreich) gesendet. Es gibt keine automatische Wiederholung. Bleibt AA
   aktiv, wird der aktive Navi-Status erneut ans Kombi gesendet. Ob das Kombi damit sofort
   vom Kompass zur Route zurückkehrt, bleibt Teil des Fahrzeugtests.
5. Fehlender DSI-Service, nicht verbundenes Telefon oder eine veraltete Reader-Beobachtung
   verhindern den Fokusaufruf. Sendefehler werden abgefangen. Doppelte BAP-Startanforderungen
   während eines ausstehenden Abbruchs senden keinen weiteren Fokuswechsel.

Ein BAP-Transaktionsabbruch beendet nur das Warten dieser Transaktion; ein schon gesendeter
Fokuswechsel lässt sich dadurch nicht zurücknehmen. Später eintreffende AA-Zustände werden
weiter angezeigt, erzeugen aber kein zweites Ergebnis für die aufgegebene Transaktion.
Der native VW-Routenstatus kann den AA-Abbruch nicht voreilig bestätigen: Beide Vorgänge
besitzen getrennte Zustände. Ohne aktive AA-Ausgabe greift weiterhin die Serienbehandlung.

## Einstellungen und Geltungsbereich

Aktuelle Standards in `jar/src/de/aatokombi/Config.java`:

```java
public static final boolean TEST_AA_NAV_CANCEL = true;
public static final boolean TEST_LOG_NAV_CANCEL = true;
public static final boolean TEST_FORCE_MEDIA_NAVIGATION = false;
```

Der dritte Wert wurde gegenüber `06baf80` von **true auf false** geändert. Damit benutzt
deine nav-fähige MQB-Unit wieder die native Navi-Seite und deren Abbruchmenü. Die bisherige
direkte Auf-/Ab-Auswahl auf der Medienseite bleibt implementiert. Für diesen separaten
Test `TEST_FORCE_MEDIA_NAVIGATION=true` setzen und am PC neu bauen; dann greift die neue
Abbruchbehandlung für AA auf der nativen Seite nicht.

Die neue Behandlung ist auf den **MQB-navsd-Pfad** begrenzt. Ein MQB-AID, das denselben
BAP-Befehl sendet, verwendet diesen Pfad ebenfalls; eine praktische AID-Prüfung fehlt.
Für PQ und die Medien-Navi-Fallbackanzeige wird keine zusätzliche Stop-Taste eingeführt.
PQ/Lauftexte/LIVE bleiben enthalten. `TEST_AA_NAV_CANCEL=false` deaktiviert den neuen
AA-Abbruch nach einem erneuten Build. Die Einstellungen bleiben reine PC-Build-Schalter.

## Fahrzeugtest

1. Neue `AAtoKombi.jar` installieren und HMI/Unit neu starten. Ist das gepatchte `libgal`
   der bisherigen Testversion installiert, genügt der JAR-Austausch: die Bibliothek ist
   bytegleich. Bei erstmaliger Installation weiterhin den normalen Installationsweg verwenden.
2. Im Stand Maps verbinden und eine Route starten. Im MFA die native Navigationsseite öffnen.
3. Mit ↑/↓ das gewohnte Stop-Menü aufrufen. Zuerst zurückgehen/ablehnen: Maps soll weiterlaufen.
   Danach erneut öffnen und „Zielführung stoppen“ bestätigen.
4. **MFA und Maps auf dem MIB/Telefon getrennt prüfen:** Endet Maps wirklich? Verschwindet
   nur die MFA-Anzeige? Bleibt Audio verbunden? Kehrt nach einem weiteren Manöver die Route zurück?
5. Eine neue Maps-Route starten: Die MFA-Navigation muss wieder erscheinen und erneut abbrechbar sein.
   Danach eine native VW-Route starten und deren Start-/Stop-Menü auf unveränderte Funktion prüfen.
6. Vor dem Neustart `/dev/shmem/aatokombi_nav_cancel.log` per FTP sichern. Die RAM-Datei
   enthält nur Zeitstempel und feste Zustandsmeldungen, keine Straßen, Ziele, Medien- oder Telefondaten.
   Nach 128 Zeilen beginnt sie neu (höchstens etwa 24 KiB). Die Toolbox wurde nicht erweitert.

Wichtige Meldungen:

| Meldung | Bedeutung |
| --- | --- |
| `FOCUS_REQUEST_SENT native=1 unsolicited=true` | Der DSI-Aufruf wurde ohne lokale Ausnahme übergeben. |
| `STOP_CONFIRMED` | AA hat in derselben Sitzung neu Status 0/2 gemeldet; den Zustand in Maps zusätzlich prüfen. |
| `TIMEOUT_NO_STOP_CONFIRMATION` | Innerhalb der Frist keine passende Rückmeldung. |
| `DISCONNECTED_NO_CONFIRMATION` / `SESSION_CHANGED_NO_CONFIRMATION` | Verbindung/Sitzung endete; kein bestätigter App-Abbruch. |
| `SEND_FAILED` / `SEND_UNAVAILABLE` / `NO_ACTIVE_AA_SESSION` | Kein nutzbarer Versandweg oder keine aktuelle aktive Sitzung. |
| `TRANSPORT_REMOVED` / `TRANSPORT_CHANGED` | HMI-/DSI-Lebenszyklus hat den ausstehenden Vorgang beendet. |
| `TRANSACTION_ABANDONED` | BAP-Transaktion zurückgesetzt/abgebrochen; kein Zurücknehmen des Fokuswechsels. |

## Genaue Dateiänderungen gegenüber `06baf80`

- `RGActDeact.java` neu als vollständige MQB-Serienklassen-Ersetzung: bestätigten AA-Stop
  vor der Serienbehandlung abfangen; Rückmeldung, Zeitablauf und Aufräumen ergänzen.
  Native Start-/Stop-/Zielauswahl-/HMI-Ereignisbehandlung aus der bereitgestellten HMI erhalten.
  Beim Rekonstruieren die von JXE-Konvertierung vertauschte Timer-Konstante auf **120000 ms**
  zurückführen, unbenutzte Konstanten ohne konvertierte Werte weglassen, Annotationen entfernen
  und die `NoClassDefFoundError`-Erzeugung an Java 1.3 anpassen. Diese Datei ist daher deutlich
  größer als der eigentliche AA-Zusatz; sie ersetzt nicht nur eine einzelne Methode.
- `NavCancellation.java` neu: Sitzung/Sequenz, Versand, Bestätigung und Zeitlimit verwalten.
- `NativeNavigationFocus.java` neu: bewachten DSI-Aufruf mit nativer Fokuskennung ausführen.
- `NavCancelLog.java` neu: begrenztes Protokoll für diesen Test.
- `AndroidAutoTarget.java`: DSI-Transport nach Registrierung bereitstellen, bei Abmeldung/Shutdown
  entfernen und vor Versand DSI-Registrierung sowie Telefonverbindung prüfen.
- `AANavReader.java`: jeden gültigen Snapshot vor der Sequenz-Abkürzung melden; nach Verarbeitung
  oder fehlenden/fehlerhaften Daten den Abbruchstatus prüfen. Keine künstliche Inaktivmeldung.
- `Config.java`: zwei Abbruch-Testschalter ergänzt; Medien-Override standardmäßig ausgeschaltet.
- Neue `NavCancellationTest` und `NavCancellationRoutingTest`, dazu eine ausschließlich für PC-Tests
  verwendete `Function`-Service-Fassade. `tests/jar/run.sh` führt beide Prüfungen mit aus.
- README, Architektur und vorhandene Testanleitungen aktualisiert; diese Anleitung neu.
- Shim, `libgal`, Toolbox und Windows-Buildskripte unverändert.

## Prüfungen am PC

Vollständiger JAR-Build gegen die bereitgestellte HMI mit Java-1.3-Zielformat; bestehende
Text-/Lauftext-/Fortschritts-/Navigations-/Tastentests sowie die neuen Abbruchtests bestanden.
Die neuen Tests prüfen unter anderem den exakten DSI-Aufruf, ausbleibende/falsche/verspätete
Bestätigungen, Rerouting, Verbindungs-/Sitzungswechsel, doppelte Anforderungen, Sendefehler
und das fünfsekündige Zeitlimit. Die echte `RGActDeact`-Ersetzung und der echte IPC-Reader
werden zusammen mit aufzeichnenden HMI-Service-Fassaden ausgeführt, einschließlich nativer
VW-Start-/Stop-Behandlung. Hersteller-Methodenkörper werden dabei nicht auf dem PC ausgeführt.

Zusätzlich wurden separate Builds und die JAR-Tests mit eingeschaltetem Medien-Override
sowie mit ausgeschaltetem AA-Abbruch erfolgreich ausgeführt. Testcode, HMI-Fassaden und
die Compile-Konstantenklasse werden nicht in der ausgelieferten JAR verpackt.
Diese Prüfungen bestätigen keine reale BAP-Übertragung oder das Verhalten von Maps/Waze im Auto.
