# Modbus RTU mit SolarMiner in Docker

Diese Anleitung beschreibt den empfohlenen Produktionsaufbau auf einem Linux-Mini-PC. SolarMiner spricht Modbus RTU über eine serielle Schnittstelle an; bei RS485 ist das in der Regel ein galvanisch getrennter USB-RS485-Adapter.

## 1. Gerät und Bus vorbereiten

1. Im Handbuch von Wechselrichter, Batterie oder Smart Meter die Schnittstelle, Pinbelegung und seriellen Parameter nachschlagen.
2. RS485 `A/B` beziehungsweise `D0/D1` entsprechend dem Gerätehandbuch verbinden. Hersteller verwenden die Bezeichnungen `A` und `B` leider nicht immer gleich.
3. Wenn vom Hersteller vorgesehen, auch `Common/GND` anschließen. Ein galvanisch getrennter Adapter reduziert das Risiko von Masseschleifen.
4. Geräte als Bus beziehungsweise Daisy-Chain verdrahten, nicht sternförmig. Es darf nur einen Modbus-Master auf dem Bus geben.
5. Abschlusswiderstände nur an den beiden physischen Busenden aktivieren. Keine zusätzlichen Abschlüsse an Abzweigungen setzen.
6. Jedem Slave auf demselben Bus eine eindeutige ID geben. Alle Teilnehmer eines Busses verwenden dieselbe Baudrate, Parität sowie Anzahl Daten- und Stoppbits.

Die Modbus-Organisation beschreibt für RS485 einen Stamm-Bus mit kurzen Abzweigungen und Abschluss an genau beiden Enden. Maßgeblich bleiben dennoch immer die Vorgaben des Geräteherstellers: [Modbus Serial Line Protocol and Implementation Guide](https://www.modbus.org/docs/Modbus_over_serial_line_V1_02.pdf).

## 2. Stabilen Linux-Gerätenamen ermitteln

Adapter einstecken und auf dem Host ausführen:

```bash
ls -l /dev/serial/by-id/
```

Beispiel:

```text
/dev/serial/by-id/usb-FTDI_FT232R_USB_UART_A10ABC-if00-port0 -> ../../ttyUSB0
```

Für Docker sollte der Pfad unter `/dev/serial/by-id/` verwendet werden. `/dev/ttyUSB0` kann sich nach einem Neustart oder beim Einstecken eines weiteren USB-Geräts ändern.

Falls kein `by-id`-Eintrag existiert:

```bash
dmesg --follow
udevadm info --query=all --name=/dev/ttyUSB0
```

Bei mehreren baugleichen Adaptern ohne eindeutige Seriennummer sollte auf dem Host eine eigene udev-Regel erstellt werden. Der Docker-Container soll trotzdem immer einen eindeutigen Alias wie `/dev/solarminer-rs485-inverter` erhalten.

## 3. Adapter an den SolarMiner-Container durchreichen

Im Service `frontend` der `compose.yml` ergänzen:

```yaml
services:
  frontend:
    devices:
      - "/dev/serial/by-id/usb-FTDI_FT232R_USB_UART_A10ABC-if00-port0:/dev/solarminer-rs485:rwm"
    group_add:
      - "${DIALOUT_GID:-20}"
```

Den linken Pfad durch den tatsächlich gefundenen Host-Pfad ersetzen. Rechts bleibt der stabile Name innerhalb des Containers. Docker Compose unterstützt diese Zuordnung offiziell als `HOST_PATH:CONTAINER_PATH[:CGROUP_PERMISSIONS]`: [Docker-Compose-Referenz](https://docs.docker.com/reference/compose-file/services/#devices).

Die Gruppen-ID auf dem Host bestimmen:

```bash
getent group dialout
```

Zum Beispiel `dialout:x:20:`. Falls die ID nicht `20` ist, in `.env` eintragen:

```dotenv
DIALOUT_GID=20
```

`group_add` wird erst relevant, wenn das Container-Image nicht als root läuft, und erlaubt dann den Zugriff über die Geräte-Gruppe. `privileged: true` ist dafür nicht nötig und sollte nicht verwendet werden.

Container anschließend neu erstellen:

```bash
docker compose up -d --force-recreate frontend
```

Nach einem physischen Abziehen und erneuten Einstecken des USB-Adapters den Container ebenfalls neu starten. Docker bindet beim Erstellen das zu diesem Zeitpunkt aufgelöste Gerät ein.

## 4. Zugriff im Container prüfen

```bash
docker compose exec frontend sh -c 'id; ls -l /dev/solarminer-rs485; test -r /dev/solarminer-rs485 && test -w /dev/solarminer-rs485'
```

Der Befehl muss mit Exit-Code `0` enden. Bei `Permission denied` zuerst Gerätebesitzer und Gruppen-ID auf dem Host prüfen und danach den Container neu erstellen.

## 5. SolarMiner einrichten

Im Setup `Modbus RTU` wählen und eintragen:

- Serielle Schnittstelle: `/dev/solarminer-rs485`
- Geräteprofil: passend zum Hersteller und Modell
- Baudrate: laut Gerätehandbuch, häufig `9600` oder `19200`
- Datenbits, Parität, Stoppbits: laut Gerätehandbuch, häufig `8/NONE/1` oder `8/EVEN/1`
- Slave ID: Adresse des Geräts auf dem Bus

Danach den Verbindungstest ausführen und nur die Profilabschnitte auswählen, die am Gerät tatsächlich vorhanden sind. Ein kombiniertes Gerät kann beispielsweise Wechselrichter, Batterie und Smart Meter über dieselbe serielle Schnittstelle bereitstellen. SolarMiner serialisiert die Zugriffe auf denselben Port, damit diese Komponenten den Adapter nicht gleichzeitig öffnen.

Die importierten Profile enthalten, soweit EVCC die Werte vorgibt, empfohlene serielle Standardparameter im `manifest.json`. Bei Abweichungen hat immer die lokale Gerätekonfiguration Vorrang.

## 6. Mehrere Geräte und Adapter

Mehrere Slaves dürfen denselben RS485-Bus und denselben Container-Pfad verwenden, sofern sie unterschiedliche Slave IDs besitzen. Für mehrere USB-Adapter jeweils einen eigenen Alias vergeben:

```yaml
devices:
  - "/dev/serial/by-id/usb-Adapter_A:/dev/solarminer-rs485-inverter:rwm"
  - "/dev/serial/by-id/usb-Adapter_B:/dev/solarminer-rs485-meter:rwm"
```

## Fehlerdiagnose

### Verbindungstest meldet Timeout

- Baudrate, Parität, Datenbits, Stoppbits und Slave ID prüfen.
- A/B testweise tauschen, aber nur wenn das Gerätehandbuch die Bezeichnung nicht eindeutig auflöst.
- Prüfen, ob ein anderes Programm oder ein zweiter Modbus-Master den Bus benutzt.
- Abschluss, Polarisation, Leitungslänge und Schirmung nach Herstellerangaben prüfen.
- Mit einer niedrigen Baudrate testen, wenn die Leitung lang oder störanfällig ist.

### Schnittstelle ist belegt

Auf dem Host prüfen:

```bash
sudo fuser -v /dev/ttyUSB0
```

SolarMiner koordiniert eigene Abfragen pro Port. Ein externer Dienst wie Home Assistant, evcc, mbpoll oder Modbus-Proxy darf denselben Adapter nicht gleichzeitig exklusiv öffnen.

### Adapter ist nach Neustart nicht vorhanden

```bash
ls -l /dev/serial/by-id/
docker compose config
docker compose up -d --force-recreate frontend
```

Wenn sich der `by-id`-Name geändert hat, wurde meist ein anderer Adapter verwendet oder das Gerät besitzt keine stabile Seriennummer. In diesem Fall eine udev-Regel verwenden.

### Keine Werte trotz erfolgreichem Öffnen

Ein erfolgreich geöffnetes serielles Gerät bestätigt noch keine Modbus-Antwort. Slave ID und Profil müssen zum Gerät passen. Außerdem kann ein Hersteller Register je nach Firmware, Betriebsart oder installiertem Zubehör anders bereitstellen. Solche Abweichungen sollten als eigenes getestetes SolarMiner-Profil dokumentiert werden.

