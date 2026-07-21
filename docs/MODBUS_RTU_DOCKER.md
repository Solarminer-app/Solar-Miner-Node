# Modbus RTU with SolarMiner in Docker

This guide describes the recommended production setup on a Linux mini PC. SolarMiner communicates with Modbus RTU devices through a serial interface; for RS485, this is typically a galvanically isolated USB-to-RS485 adapter.

## 1. Prepare the device and bus

1. Consult the inverter, battery, or smart meter manual for the interface, pin assignment, and serial parameters.
2. Connect RS485 `A/B` or `D0/D1` according to the device manual. Unfortunately, manufacturers do not always use the `A` and `B` designations consistently.
3. Connect `Common/GND` as well if required by the manufacturer. A galvanically isolated adapter reduces the risk of ground loops.
4. Wire devices as a bus or daisy chain, not in a star topology. There must be only one Modbus master on the bus.
5. Enable termination resistors only at the two physical ends of the bus. Do not add termination at branches.
6. Assign a unique ID to every slave on the same bus. All participants on a bus must use the same baud rate, parity, data bits, and stop bits.

The Modbus Organization specifies an RS485 trunk bus with short branches and termination at exactly both ends. However, the device manufacturer's requirements always take precedence: [Modbus Serial Line Protocol and Implementation Guide](https://www.modbus.org/docs/Modbus_over_serial_line_V1_02.pdf).

## 2. Determine a stable Linux device name

Connect the adapter and run the following command on the host:

```bash
ls -l /dev/serial/by-id/
```

Example:

```text
/dev/serial/by-id/usb-FTDI_FT232R_USB_UART_A10ABC-if00-port0 -> ../../ttyUSB0
```

Use the path under `/dev/serial/by-id/` for Docker. `/dev/ttyUSB0` may change after a restart or when another USB device is connected.

If no `by-id` entry exists, run:

```bash
dmesg --follow
udevadm info --query=all --name=/dev/ttyUSB0
```

When using multiple identical adapters without unique serial numbers, create a custom udev rule on the host. The Docker container should still receive a unique alias such as `/dev/solarminer-rs485-inverter`.

## 3. Pass the adapter through to the SolarMiner container

Add the following configuration to the `frontend` service in `compose.yml`:

```yaml
services:
  frontend:
    devices:
      - "/dev/serial/by-id/usb-FTDI_FT232R_USB_UART_A10ABC-if00-port0:/dev/solarminer-rs485:rwm"
    group_add:
      - "${DIALOUT_GID:-20}"
```

Replace the path on the left with the actual host path. The path on the right remains the stable device name inside the container. Docker Compose officially supports this mapping as `HOST_PATH:CONTAINER_PATH[:CGROUP_PERMISSIONS]`: [Docker Compose reference](https://docs.docker.com/reference/compose-file/services/#devices).

Determine the group ID on the host:

```bash
getent group dialout
```

For example, `dialout:x:20:`. If the ID is not `20`, add it to `.env`:

```dotenv
DIALOUT_GID=20
```

`group_add` becomes relevant when the container image does not run as root. It grants access through the device group. `privileged: true` is neither required nor recommended.

Recreate the container afterward:

```bash
docker compose up -d --force-recreate frontend
```

Also recreate or restart the container after physically disconnecting and reconnecting the USB adapter. Docker binds the device that was resolved when the container was created.

## 4. Verify access inside the container

```bash
docker compose exec frontend sh -c 'id; ls -l /dev/solarminer-rs485; test -r /dev/solarminer-rs485 && test -w /dev/solarminer-rs485'
```

The command must finish with exit code `0`. If it reports `Permission denied`, first check the device owner and group ID on the host, then recreate the container.

## 5. Configure SolarMiner

Select `Modbus RTU` during setup and enter the following values:

- Serial interface: `/dev/solarminer-rs485`
- Device profile: the profile matching the manufacturer and model
- Baud rate: as specified in the device manual, commonly `9600` or `19200`
- Data bits, parity, and stop bits: as specified in the device manual, commonly `8/NONE/1` or `8/EVEN/1`
- Slave ID: the device address on the bus

Run the connection test afterward and select only the profile sections that are actually available on the device. A combined device may expose an inverter, battery, and smart meter through the same serial interface. SolarMiner serializes access to the same port so these components do not attempt to open the adapter simultaneously.

Where EVCC provides these values, imported profiles include recommended default serial parameters in `manifest.json`. The local device configuration always takes precedence if the values differ.

## 6. Multiple devices and adapters

Multiple slaves may share the same RS485 bus and container path as long as they use different slave IDs. Assign a separate alias to each USB adapter when using multiple adapters:

```yaml
devices:
  - "/dev/serial/by-id/usb-Adapter_A:/dev/solarminer-rs485-inverter:rwm"
  - "/dev/serial/by-id/usb-Adapter_B:/dev/solarminer-rs485-meter:rwm"
```

## Troubleshooting

### The connection test times out

- Verify the baud rate, parity, data bits, stop bits, and slave ID.
- Try swapping A and B, but only if the device manual does not clearly define the labels.
- Check whether another application or a second Modbus master is using the bus.
- Verify termination, biasing, cable length, and shielding according to the manufacturer's specifications.
- Try a lower baud rate if the cable is long or susceptible to interference.

### The serial interface is busy

Check on the host:

```bash
sudo fuser -v /dev/ttyUSB0
```

SolarMiner coordinates its own requests per port. An external service such as Home Assistant, evcc, mbpoll, or a Modbus proxy must not open the same adapter exclusively at the same time.

### The adapter is missing after a restart

```bash
ls -l /dev/serial/by-id/
docker compose config
docker compose up -d --force-recreate frontend
```

If the `by-id` name has changed, a different adapter was usually connected or the device does not provide a stable serial number. Use a udev rule in that case.

### No values despite opening the interface successfully

Successfully opening the serial device does not confirm that a Modbus response was received. The slave ID and profile must match the device. A manufacturer may also expose registers differently depending on the firmware, operating mode, or installed accessories. Document such differences in a dedicated, tested SolarMiner profile.
