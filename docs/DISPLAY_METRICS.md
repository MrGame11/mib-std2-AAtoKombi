# MFA/MFD: Auflösung, Schrift und Messungen

Stand: 2026-10-28. Referenz des Nutzers: **Golf 7 Facelift (2018), MQB mono,
5G1 920 740 C, SW 0310, HW 202**. Dafür liegt noch keine Pixelmessung vor.

## Ergebnis der Recherche

Eine verifizierte Zuordnung von Panelauflösung, aktiver Textfläche und Fontmetriken für MQB/PQ,
jeweils mono und farbig, konnte mit den hier erreichbaren Quellen nicht hergestellt werden.
Suchmaschinen, Volkswagen Newsroom, Ersatzdisplayanbieter und SSP-Seiten wurden durch den
Netzwerkzugang mit HTTP 403 blockiert; GitHub war erreichbar. Auch die Suche nach der konkreten
Teilenummer lieferte dort keine verwertbaren Schrift- oder Auflösungsdaten. Das ist keine Aussage,
dass solche Unterlagen grundsätzlich nicht existieren.

| Variante | Verifiziertes Pixelprofil für AAtoKombi |
|---|---|
| MQB mono, 5G1 920 740 C / SW 0310 / HW 202 | Noch offen; manuell messbar |
| Andere MQB mono | Offen; Werte des Referenzgeräts nicht automatisch übertragen |
| MQB farbig | Offen; eigenes Font-/Layoutprofil erforderlich |
| PQ mono | Offen; Generation und Teilenummer erforderlich |
| PQ farbig | Offen; Generation und Teilenummer erforderlich |
| AID / Virtual Cockpit | Offen für die BAP-Textzeilen; Videoflächen sind keine Textmetriken |

Die gefundenen Open-Source-Quellen grenzen ältere oder andere Anzeigepfade ab:

- [MFAImageTool](https://github.com/surdu/MFAImageTool/blob/master/index.html) arbeitet mit einem
  **64 × 88 Pixel großen Zeichenbereich** für die ältere VW2002FISWriter-Anbindung. Daraus folgt
  weder die Gesamtauflösung eines aktuellen Kombiinstruments noch eine MQB-Zeilenbreite.
- [TLBFISLib](https://github.com/domnulvlad/TLBFISLib) dokumentiert die ältere Dreileiter-Anbindung
  und verschiedene Textfonts. Deren Fontauswahl ist kein Nachweis für die BAP-Fonts im Golf 7.
- [VcMOSTRenderMqb](https://github.com/OneB1t/VcMOSTRenderMqb) behandelt die Videoausgabe zum
  Virtual Cockpit über MOST. Ein eigener Font in dieser Videoausgabe beschreibt nicht die vom
  Kombiinstrument gerenderten `CurrentStationInfo`-/`TurnToInfo`-Zeilen.

Keine dieser Quellen wurde als Pixelprofil in den Testbranch übernommen.

## Was die bereitgestellte HMI-Datei tatsächlich zeigt

Der aus der bereitgestellten JXE erzeugte `MIBHMI.jar` enthält keine TTF-, OTF-, FNT-, BDF- oder
PCF-Fontdateien. Das allein schließt eingebettete Fontdaten in anderen Formaten nicht aus. Für
AAtoKombi entscheidend: Die verwendeten BAP-Funktionen senden Text an das Kombiinstrument; sie
rendern diese Zeilen nicht selbst mit einem Font aus dem Infotainment.

Die untersuchten `ASG_Capabilities_PresentationCapabilities` für Audio und Navigation enthalten
keine Buchstabenbreitentabelle oder Pixelbreite dieser Textzeilen. Auch die geprüften
`DisplayIdentification`-/`KombiDisplayStatus`-Klassen liefern keine solchen Fontmetriken.

Die folgenden **Transportgrenzen** wurden dagegen an den Serializer-Konstruktoren und
`BAPString.computeMaximalStringPayloadByteLength` / `transformContentByByteLength` nachvollzogen:

| Feld | BAPString-Kapazität | UTF-8-Nutzdaten nach Längenpräfix |
|---|---:|---:|
| MQB Medien, je Feld | 73 Bytes | 72 Bytes |
| PQ Medien, je Feld | 49 Bytes | 48 Bytes |
| MQB Navi `turnToInfo` | 76 Bytes | 75 Bytes |
| PQ Navi `turnToInfo` | 75 Bytes | 74 Bytes |

Diese Zahlen sagen nichts über die physisch sichtbaren Pixel oder Zeichen aus. Die früher als
Zeichenlimits bezeichneten Medienwerte 72/48 sind nur für einbyteigen ASCII-Text zahlenmäßig gleich.

## Manuelles Messen am MQB-Mono-Referenzgerät

Ja: Ein gemessenes Breitenprofil würde die aktuellen Zeichenanzahl-Schätzungen deutlich verbessern.
Für die Erkennung abgeschnittener Buchstaben genügt eine Breitentabelle; vollständige Bitmapbilder
jedes Zeichens und der offizielle Fontname sind dafür nicht erforderlich.

Bitte je tatsächlich genutztem Layout getrennt messen:

| Ansicht | Zu messende Zeile |
|---|---|
| Medien, Fortschritt aus | Titel in der großen Primärzeile |
| Medien, Fortschritt an | Titel; zusätzlich Artist, Album und Fortschrittszeile zur Layoutkontrolle |
| Navi in der Medienseite | Straßenname in der Sekundärzeile, ohne eingeblendete Spurzeile |
| Native Navi-Seite über BAP | Straßenname, sofern auf dem Gerät verfügbar |

Ein nicht vorhandenes Layout als nicht verfügbar markieren. Eine Zeile nur dann mit einer anderen
zusammenfassen, wenn gleiche Testtexte nachweislich dieselben Maße haben.

Benötigt werden:

1. **Nutzbare Zeilenbreite in echten LCD-Pixeln** zwischen den Clippinggrenzen, nach Abzug von
   Rändern, Icons und reservierten Bereichen. Die Paneldiagonale allein reicht nicht.
2. **Zeichen-Vorschub**: Abstand vom Start eines Buchstabens zum Start des nächsten, einschließlich
   Leerraum. Die Breite der leuchtenden Pixel allein ist nicht ausreichend.
3. **Leerzeichen, Ziffern und Satzzeichen**, außerdem `ÄÖÜäöüß`, normale Klein- und Großbuchstaben.
   Besonders aussagekräftig für einen ersten Vergleich sind `i`, `l`, `I`, `M`, `W`, `m`, `w` und `0`.
4. **Kontrolltexte nahe der Clippinggrenze** und Angabe, welcher Buchstabe teilweise oder vollständig
   abgeschnitten wird. Damit lassen sich Zentrierung und mögliche Auslassungspunkte berücksichtigen.

Für den Vorschub sind Wiederholungen wie `iiiiiiiiii`, `MMMMMMMMMM`, `0000000000` und `A A A A`
praktisch. Wenn kein Kerning verwendet wird, gilt für zehn gleiche Zeichen:

`Vorschub = (Pixelbreite des Zehnertexts − Pixelbreite des einzelnen Zeichens) / 9`

Zusätzlich `AV`, `VA`, `To` und `WA` gegen die Summe der Einzelvorschübe prüfen. Abweichungen können
Kerning anzeigen; dann reicht eine reine Tabelle pro Buchstabe nicht für jedes Wort pixelgenau.
Bei Fotos die tatsächlichen LCD-Pixel zählen, nicht die hochskalierten Kamerapixel.

Minimaler Messdatensatz, z. B. als Tabelle:

```text
Teilenummer / HW / SW: 5G1 920 740 C / 202 / 0310
Ansicht und Zeile:
Fortschritt an/aus:
Nutzbare Zeilenbreite in LCD-Pixeln:
Zeichen oder Testtext:
Breite der leuchtenden Fläche:
Vorschub einschließlich Abstand:
Teilweise/vollständig abgeschnittene Zeichen:
```

Mit diesen Daten kann ein eigenes Pixelprofil hinterlegt werden. Es ist in diesem Review noch
nicht implementiert: Die Messwerte fehlen. Ein solches Profil verbessert die Entscheidung, **wann**
und **wie weit** gescrollt wird; die Textübertragung über BAP wird dadurch nicht zu einer Animation
mit frei steuerbaren einzelnen Pixelpositionen.
