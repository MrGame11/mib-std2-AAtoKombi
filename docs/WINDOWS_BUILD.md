# Windows-Build

Der Windows-Starter baut dieselben zwei Dateien wie `build.sh`:
`AAtoKombi.jar` und `libext.google.gal.receiver.so`. Die Build-Schritte laufen weiterhin
in Linux-Containern. Die Einstellungen in `jar/src/de/aatokombi/Config.java`, einschließlich
Lauftext, Geschwindigkeit und Medienfortschritt mit `LIVE`, gelten auch hier.

## Verwendung

1. Docker Desktop installieren, starten und auf **Linux containers** stellen.
   Docker Desktop kann dafür WSL 2 verwenden; eine eigene Linux-Distribution für die
   Build-Befehle, Git Bash, ein lokales JDK und Python sind nicht erforderlich.
2. Den Quellcode dieses Branches vollständig entpacken oder klonen. Beim Aktualisieren
   eines bestehenden Windows-Checkouts müssen die Shell-Skripte LF-Zeilenenden behalten.
3. `Config.java` vor dem Bauen nach Wunsch bearbeiten.
4. `build.bat` doppelklicken und die `MIBHMI.jxe` sowie die **unveränderte originale**
   `libext.google.gal.receiver.so` derselben Unit auswählen. Alternativ beide Dateien
   gemeinsam auf `build.bat` ziehen; ihre Reihenfolge wird erkannt.
5. Bei Erfolg liegen die Ergebnisse in `dist`. Bei einem Doppelklick öffnet sich der
   Ordner automatisch. Das Konsolenfenster bleibt zur Kontrolle offen.

Mit expliziten Pfaden aus PowerShell, einschließlich optionalem Ausgabeordner:

```powershell
.\build.bat "C:\MIB dump\MIBHMI.jxe" "C:\MIB dump\libext.google.gal.receiver.so" "C:\AA build"
```

Für einen Aufruf ohne abschließende Tastendruck-Pause direkt das PowerShell-Skript starten:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 "C:\MIB dump\MIBHMI.jxe" "C:\MIB dump\libext.google.gal.receiver.so"
```

`ExecutionPolicy Bypass` gilt für diesen PowerShell-Prozess und ändert keine dauerhaft
gespeicherte Ausführungsrichtlinie. Ein fehlgeschlagener Build liefert Exitcode 1.
Bereits vorhandene Dateien in `dist` können von einem älteren Build stammen; nur nach
der Erfolgsmeldung die neuen Ergebnisse verwenden.

Die einmalige JXE-Konvertierung wird wie bei `build.sh` anhand des SHA-256 der Eingabe
zwischengespeichert. Beide Starter verwenden `jar/lib/MIBHMI.jar` und
`jar/lib/.MIBHMI.jxe.sha256`. Der erste Build benötigt Downloads für die Docker-Images
und die Toolchain. `JXE2JAR_IMAGE` kann wie unter Linux/macOS über die Umgebung gesetzt werden.
Die Installation auf der Unit erfolgt anschließend wie bisher über die Toolbox.

## Übernahme und genaue Abweichungen

Grundlage ist der angefragte Windows-Build-Commit
`6f5fc56dca27d1980fcacbaa9adc071cee24558f`.

- `build.bat`: unverändert übernommen; Windows-Zeilenenden (CRLF).
- `build.ps1`: übernommen. Zusätzlich werden auch `jar/build.sh` und `shim/build.sh`
  auf falsche CRLF-Zeilenenden geprüft; die Vorlage prüft nur `docker/entrypoint.sh`.
  Die Fehlermeldung nennt die betroffene Datei.
- `.gitattributes`: die vier Regeln der Vorlage übernommen und um LF für `*.ps1` ergänzt.
- `.gitignore`: `output/` ergänzt. Die zusätzlichen Regeln der Vorlage für unbenutzte
  temporäre Verzeichnisse unter dem alten Projektnamen, interne Forschung,
  CarPlay-Werkzeuge und `.venv/` wurden nicht übernommen.
- `README.md`: Windows-Anleitung in die bestehende Testbranch-Dokumentation integriert,
  Pfade mit Leerzeichen gezeigt und die Rolle von WSL 2 präzisiert.
- `docs/WINDOWS_BUILD.md`: diese ergänzende Anleitung und Änderungsliste neu angelegt.

Der Lenkrad-Auswahl-Commit `4e7c440f2299cfadb81d0de8d500e02c60e8c894` ist nicht Teil
dieser Übernahme. Java- und Shim-Quellcode, `Config.java`, `build.sh`, Dockerfile und
Toolbox-Dateien werden durch die Windows-Erweiterung nicht geändert.

## Prüfung bei der Übernahme

- PowerShell-Syntaxprüfung und 13 Ablaufprüfungen mit PowerShell 7.4.6 unter Linux und
  einem simulierten Docker-Kommando bestanden: Erstbau, Cache-Wiederverwendung,
  Pfade mit Leerzeichen und eckigen Klammern, vertauschte Eingaben, bereits abgelegte
  GAL-Datei, fehlende Eingabe, Docker-Ausfall, falscher Container-Modus, fehlgeschlagene
  Konvertierung, fehlendes Konvertierungsergebnis, Image-/Kompilierfehler und CRLF
  in jedem der drei Shell-Skripte. Erfolgsfälle prüfen auch die Ausgabedateien und
  den mit `build.sh` kompatiblen Hash-Cache.
- Echter JAR-Build gegen die bereitgestellte HMI sowie die bestehenden Text-,
  Lauftext-, Fortschritts- und Navigations-Mapping-Tests bestanden.
- Ein kompletter Neubau des Docker-Toolchain-Images konnte in der Cloud wegen fehlender
  DNS-Auflösung für die Ubuntu-Paketserver nicht abgeschlossen werden.
- Windows PowerShell 5.1, die Windows-Dateidialoge, Drag-and-drop und Docker Desktop
  wurden hier nicht auf einem Windows-System ausgeführt. Die Ablaufprüfungen ersetzen
  diesen Test nicht.
