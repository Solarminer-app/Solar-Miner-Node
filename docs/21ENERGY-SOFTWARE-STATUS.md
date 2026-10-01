# 21energy-Integration – Softwarestatus

Stand: 1. Oktober 2026

Die erste Softwarebasis ist implementiert, aber **nicht produktiv freigegeben**. Neue Geräte werden ausschließlich im Modus `MONITORING` gespeichert. Steuerung, Kalibrierung und Managed Mining sind absichtlich gesperrt.

## Implementiert

- Lokaler, auf IPv4-Privatnetze begrenzter `http://<host>:<port>/21control/`-Client mit festen Endpunkten, kurzen Timeouts, begrenztem GET-Retry und Antwortgrößenlimit.
- Discovery über `/status` und `/status/system`; nur ein operationales Gerät mit Produkt-ID wird als `TWENTY_ONE_ENERGY` erkannt.
- Tolerantes Lesen von System- und Summary-Daten. Nicht bestätigte Hashrate-Einheiten werden nicht in TH/s umgerechnet.
- Persistente Heizer-Entität inklusive Flyway-Migration. Sie speichert keine WLAN-, Diagnose- oder Pool-Credentials.
- Synchroner `TWENTY_ONE_ENERGY(false, true)`-Eintrag in Node und Core.
- Explizit bestätigte Core-Kalibrierung: Sie setzt die API-Stufen `0..4`, liest jeweils `/heater/powerTarget/watt` zurück, validiert eine vollständige monotone Sollwert-Tabelle und stellt die vorherige Stufe wieder her. Der diskrete Mapper rundet nur nach unten; Budgets unter der kleinsten aktiven Stufe deaktivieren den Heizer, statt Stufe 0 als ausgeschaltet zu vermuten.
- Unit-Tests für Netzgrenze und Mapper.

## Bewusst nicht freigegeben

- `POST /heater/enable` und `/heater/powerTarget/{index}`: Der Controller ist ohne verifizierte, persistierte Stufenkarte nicht schreibfähig.
- Pool-Konfiguration, Proxy-Routing und Dev-Fee-Durchsetzung: Kein automatisches Überschreiben von Poolwerten; Managed Mining benötigt reale Share-/Gutschriftnachweise.
- Hashrate-/Effizienz- und Temperatur-Grenzwertinterpretation: Die API-Shapes bzw. Einheiten müssen mit einem realen Gerät bestätigt werden.
- Heizungs-/Raumtemperaturregelung, Zeitplan-, Lüfter-, Support-, Bluetooth- und Log-Endpunkte.

## Erforderliche Freigabeprotokolle

Vor dem Wechsel von `MONITORING` zu `CONTROL` müssen für jedes Modell/Firmware die in `../../21ENERGY-INTEGRATION.md` Abschnitt 16 beschriebenen Messungen (API-Sollwerttabelle, reale Aufnahme, Read-back, Zustandswechsel, Netzwerkfehler) dokumentiert sein. Die neue Core-Kalibrierung liefert die Sollwerttabelle; sie ersetzt keine Messung der tatsächlichen Aufnahme. Vor `MANAGED_MINING` kommen der Proxy-Login sowie User-, SolarMiner- und Referral-Share-/Poolgutschriften hinzu. Die gültige, vollständige Hersteller-OpenAPI-Datei ist vor einer Client-Erweiterung unter stabilem Namen zu versionieren; die im Repository vorhandene abgeschnittene Datei ist dafür nicht zulässig.
