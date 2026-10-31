# Musik/Navigation über die Lenkradbedienung

Diese Testbranch-Erweiterung übernimmt Commit
`4e7c440f2299cfadb81d0de8d500e02c60e8c894` und ergänzt die bestehenden Lauftext- und
LIVE-Funktionen. Sie betrifft den **MQB-Pfad mit AA-Navigation auf der Medienseite**.

## Bedienung

Bei aktiver AA-Zielführung im Audio-Menü des Kombiinstruments:

- BAP `DedicatedAudioControl` NEXT (`controlType=1`) wählt die Musikansicht.
- PREVIOUS (`controlType=2`) wählt die Navigationsansicht.
- Wiederholte Eingaben derselben Richtung behalten die Ansicht bei.
- Eine neue Zielführung startet mit Navigation. Neuberechnungen und neue Manöver
  erhalten die Auswahl; Routenende, Verbindungsende und neue AA-Sitzung setzen sie zurück.

Bei der besprochenen Golf-7-Tastenanordnung sind dafür die Auf-/Ab-Tasten neben OK
vorgesehen, üblicherweise abwärts=NEXT und aufwärts=PREVIOUS. Die konkrete Zuordnung
am Golf wurde nicht am Fahrzeug bestätigt: Der Code erkennt BAP-Befehle aus dem
Kombiinstrument, keine physischen Tastensymbole. Die separaten Titelwechsel-Tasten
behalten ihren bisherigen Eingabepfad.

Die Übernahme setzt `SHOW_NAV=true` und `SHOW_MEDIA=true` voraus. Außerdem müssen
Android Auto die aktive Smartphone-Verbindung und die aktive Audioquelle sein,
das System eingeschaltet und die BAP-Audioaktion frei sein. Nur NEXT/PREVIOUS für
die aktuelle Liste (`listType=0`) mit positiver Schrittzahl werden abgefangen.
Andere Befehle laufen durch die Serienbehandlung. PQ und der native Navi-Pfad
erhalten keine neue Bedienung. AID nutzt diese Funktion, sofern es auf dem
MQB-non-nav-Pfad läuft; sein Displaytyp allein entscheidet nicht darüber.

Navigation, Titel-Lauftext und Straßen-Lauftext aktualisieren sich auch im
Hintergrund weiter. Scroll-Ereignisse ändern die gewählte Ansicht nicht. Beim
Umschalten wird der aktuelle Textausschnitt angezeigt; die Lauftexte starten
dabei nicht neu. Fehlen Musik-Metadaten, bleibt die vorhandene Serien-Fallbackanzeige.

## Wie nav/non-nav erkannt wird

**MQB:** `ClusterCaps.isNavCapable()` fragt
`APIFactory.getAPIFactory().getConfigurationService().isNavigationFeatureSelected()`
ab. In der bereitgestellten HMI liest diese Methode das Headunit-Featureflag **367**.
`true` wählt navsd, `false` die Medienseite. Ein erfolgreich gelesener Wert wird
für die HMI-Laufzeit gespeichert. Ist die API noch nicht verfügbar oder wirft sie
einen Fehler, wird zunächst die Medienseite angenommen und später erneut geprüft.

Das ist **keine direkte Prüfung des Kombiinstruments** und keine Abfrage von
Auflösung, Mono/Farbe, AID, Teilenummer oder tatsächlich vorhandenen Navi-Glyphen.
Die bisherige Bezeichnung „nav cluster“ ist deshalb verkürzt: Entscheidend ist
hier die Navi-Konfiguration der Headunit. Der tatsächliche navsd-Empfänger muss
zusätzlich passend unterstützt und codiert sein. Das Setzen dieses Flags allein
rüstet weder native Navigation noch eine fehlende Kombi-Funktion nach.

**PQ:** Sobald der PQ-Audio-Pfad initialisiert ist, benutzt `AANavReader.navCapable()`
stattdessen `mqbpq.navsd.functions.ManeuverDescriptor.isActive()`. Dafür muss dessen
`init()` bereits eine Instanz registriert haben. Ohne diese Instanz wird die
Medienseite gewählt. Dieser Wert wird bei der Pfadauswahl erneut abgefragt.

## „Zielführung abbrechen“ auf der nativen Navi-Seite

Die Darstellung und Bedienfolge dieses Menüs gehören zur Firmware des
Kombiinstruments. AAtoKombi meldet bei laufender AA-Navigation über `RGStatus`
den Status „Zielführung aktiv“; deshalb kann das Kombiinstrument seine gewohnte
Abbruchabfrage anbieten. Ob es das mit AAtoKombi auf einem bestimmten Kombi
tatsächlich tut, ist bislang nicht am Fahrzeug geprüft.

Eine bestätigte Abbruchanforderung verarbeitet die unveränderte Serienfunktion
`RGActDeact` (BAP-Funktion 34). In der untersuchten HMI ruft sie für `controlType=1`
ihre `stopRouteGuidance()`-Behandlung auf. Diese prüft den **internen** Navigationsstatus:
bei Status 0 liefert sie Ergebnis 4 zurück; bei Status 1/2 ruft sie
`NavigationService.stopRouteGuidance()` auf. AAtoKombi ändert nur den ausgegebenen
Aktivstatus und übernimmt diese Anforderung bisher nicht für das Handy.
Ein eventuell sichtbares Menü bedeutet daher noch keine funktionierende
Abbruchfunktion für Google Maps oder Waze. Seriennavigation bleibt auf ihrem
vorhandenen Bedienpfad.

Eine AA-Erweiterung müsste die bestätigte Anforderung in `RGActDeact` bei aktivem
AA gezielt behandeln, den Abbruch an AA weitergeben und erst nach Rückmeldung des
Handys den Zustand abschließen. Nur `NavState.ACTIVE=false` zu setzen blendet die
Darstellung vorübergehend aus; die Route läuft auf dem Handy weiter und der nächste
AA-Datensatz würde sie wieder anzeigen.

Die vorhandene DSI-Schnittstelle hat `navFocusNotification(int, boolean)`, aber
keine dedizierte `stopNavigation()`-Methode. Die Serien-HMI sendet bereits
`navFocusNotification(1, true)`, wenn eine native Zielführung aktiv wird. Diesen
vorhandenen Fokus-Pfad könnte man gezielt untersuchen. Ob damit die AA-App ihre
Route tatsächlich beendet oder nur ihren Fokus verliert, ist mit Maps/Waze und
der jeweiligen Firmware zu prüfen. Eine zusätzliche native Erweiterung von
`libgal` ist deshalb derzeit weder als nötig noch als unnötig nachgewiesen.
Die Abbruchfunktion ist **nicht Bestandteil dieser Übernahme**.

## Genaue Änderungen und Tests

- `MediaViewMode.java` und die MQB-`DedicatedAudioControl.java` unverändert aus dem
  angefragten Commit übernommen. Die große zweite Datei ersetzt die vollständige
  Serienklasse; der neue Auswahlblock sitzt in `startResult()`.
- Die Auswahlbedingung in MQB-`CurrentStationInfo` und die drei Lebenszyklus-Aufrufe
  in `AANavReader` übernommen. Bestehende Lauftexte, UTF-8-Grenzen und die robuste
  Zeit-/LIVE-Formatierung bleiben erhalten. Kommentare in `Config.java` angepasst;
  keine Einstellwerte geändert.
- Kommentare in `ClusterCaps` korrigiert: Headunit-Feature statt direkter
  Kombi-Erkennung. Keine Änderung am Erkennungsalgorithmus.
- Beide Tests des Commits übernommen; Routing-Test um tatsächliche Lauftext-Callbacks
  während der Musikauswahl und um die PQ-Pfadauswahl erweitert.
- `tests/host/HostHmiRunner.java` und der Testrunner ermöglichen diese Tests mit
  JDK 8: Die JXE-Konvertierung enthält Herstellerklassen, die als Compile-API dienen,
  aber auf dem PC unter anderem wegen Code-Attributen in abstrakten Methoden nicht
  ausführbar sind. Der Test lädt unseren echten Code und erzeugt für die Hersteller-
  Abhängigkeiten ausschließlich im Speicher API-Test-Doubles. Deren Konstruktoren
  initialisieren nur die Oberklasse; sonstige konkrete Methoden werfen Fehler.
  Das emuliert weder die HMI noch den BAP-Bus. Original-JAR und ausgelieferte
  AAtoKombi.jar werden dadurch nicht verändert; Test-Doubles werden nicht ausgeliefert.
- README, Architektur- und Testbranch-Dokumentation ergänzt; dieses Dokument neu.
- Shim, `libgal`, Windows-Starter und Toolbox-Dateien bleiben unverändert.

Geprüft: vollständiger JAR-Build gegen die bereitgestellte HMI, Java-1.3-Ausgabe,
bestehende Text-/Lauftext-/Fortschritts-/Navigations-Mapping-Tests und die neuen
Auswahl-/Routing-Tests mit JVM-Verifikation. Die mit `javap -p` sichtbaren Felder,
Methodensignaturen und Obertypen von `DedicatedAudioControl` stimmen mit der
bereitgestellten Serienklasse überein. Die Fahrzeugprüfung der tatsächlichen
Tasten, Anzeigewechsel, Radio-/USB-/AA-Quellenwechsel und Wiederverbindung steht aus.
Ein erfolgreicher Build ist keine vollständige Kompatibilitätsprüfung anderer
Firmwarestände oder der übernommenen Serien-Audiologik.
