package de.verdox.pv_miner.pvconfig;

import de.verdox.pv_miner.dto.SetupRequests;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner.setup.SetupService;
import de.verdox.pv_miner_extensions.device.message.MessageEntity;
import de.verdox.pv_miner_extensions.device.modbus.ModbusEntity;
import de.verdox.pv_miner_extensions.device.modbus.ModbusProfileEntity;
import de.verdox.pv_miner_extensions.device.modbusrtu.ModbusRtuEntity;
import de.verdox.pv_miner_extensions.device.mqtt.MqttEntity;
import de.verdox.pv_miner_extensions.device.rest.RestEntity;
import de.verdox.pv_miner_extensions.device.websocket.WebSocketEntity;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeviceProfileCompatibilityService {
    private final SetupService setupService;
    private final EntityService entityService;

    public DeviceProfileCompatibilityService(SetupService setupService, EntityService entityService) {
        this.setupService = setupService;
        this.entityService = entityService;
    }

    public CompatibilityResult inspect(PVSiteEntity site) {
        List<ProfileIssue> issues = new ArrayList<>();
        Map<String, List<ProfileCandidate>> candidateCache = new HashMap<>();
        for (Object device : devices(site)) {
            String providerId = providerId(device);
            String deviceType = deviceType(device);
            String profileName = profileName(device);
            String sectionKey = sectionKey(device);
            List<ProfileCandidate> candidates = candidateCache.computeIfAbsent(
                    providerId + "\u0000" + deviceType,
                    ignored -> candidates(providerId, deviceType)
            );
            ProfileCandidate current = candidates.stream()
                    .filter(candidate -> sameProfileName(candidate.profileName(), profileName))
                    .findFirst()
                    .orElse(null);
            boolean compatible = current != null && current.sections().stream()
                    .anyMatch(section -> section.sectionKey().equals(sectionKey));
            if (!compatible) {
                issues.add(new ProfileIssue(
                        id(device), name(device), deviceType, providerId,
                        profileName, sectionKey, candidates
                ));
            }
        }
        return new CompatibilityResult(issues.isEmpty(), issues);
    }

    @Transactional
    public CompatibilityResult repair(PVSiteEntity site, List<ProfileRepair> repairs) {
        if (repairs == null || repairs.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one profile repair is required");
        }
        for (ProfileRepair repair : repairs) {
            Object device = devices(site).stream()
                    .filter(candidate -> id(candidate).equals(repair.deviceId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "PV device not found"));
            ProfileCandidate candidate = candidates(providerId(device), deviceType(device)).stream()
                    .filter(entry -> entry.profileName().equals(repair.profileName()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Selected profile is not available"));
            boolean sectionAvailable = candidate.sections().stream()
                    .anyMatch(section -> section.sectionKey().equals(repair.sectionKey()));
            if (!sectionAvailable) {
                throw new ResponseStatusException(BAD_REQUEST, "Selected profile section is not compatible with the device");
            }
            applyProfile(device, repair.profileName(), repair.sectionKey());
            save(device);
        }
        return inspect(site);
    }

    private List<ProfileCandidate> candidates(String providerId, String deviceType) {
        return setupService.getPvDeviceProfiles(providerId, "").stream()
                .map(profile -> new ProfileCandidate(
                        profile.profileName(),
                        profile.sections().stream()
                                .filter(section -> deviceType.equals(section.deviceType()))
                                .map(section -> new ProfileSection(section.sectionKey(), section.name()))
                                .toList()
                ))
                .filter(candidate -> !candidate.sections().isEmpty())
                .toList();
    }

    private List<Object> devices(PVSiteEntity site) {
        List<Object> devices = new ArrayList<>();
        devices.addAll(site.getInverters());
        devices.addAll(site.getBatteries());
        devices.addAll(site.getSmartMeters());
        return devices;
    }

    private String providerId(Object device) {
        if (device instanceof ModbusRtuEntity<?>) return SetupService.MODBUS_RTU;
        if (device instanceof ModbusEntity<?>) return SetupService.MODBUS;
        if (device instanceof RestEntity<?>) return SetupService.REST;
        if (device instanceof MqttEntity<?>) return SetupService.MQTT;
        if (device instanceof WebSocketEntity<?>) return SetupService.WEBSOCKET;
        throw new ResponseStatusException(BAD_REQUEST, "Unsupported PV device provider");
    }

    private String profileName(Object device) {
        if (device instanceof ModbusProfileEntity<?> value) return value.getModbusConfigName();
        if (device instanceof RestEntity<?> value) return value.getRestConfigName();
        if (device instanceof MessageEntity<?> value) return value.getMessageConfigName();
        throw new ResponseStatusException(BAD_REQUEST, "Unsupported PV device provider");
    }

    private String sectionKey(Object device) {
        if (device instanceof ModbusProfileEntity<?> value) return value.getSectionKey();
        if (device instanceof RestEntity<?> value) return value.getSectionKey();
        if (device instanceof MessageEntity<?> value) return value.getSectionKey();
        throw new ResponseStatusException(BAD_REQUEST, "Unsupported PV device provider");
    }

    private void applyProfile(Object device, String profileName, String sectionKey) {
        if (device instanceof ModbusProfileEntity<?> value) {
            value.setModbusConfigName(profileName);
            value.setSectionKey(sectionKey);
        } else if (device instanceof RestEntity<?> value) {
            value.setRestConfigName(profileName);
            value.setSectionKey(sectionKey);
        } else if (device instanceof MessageEntity<?> value) {
            value.setMessageConfigName(profileName);
            value.setSectionKey(sectionKey);
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported PV device provider");
        }
    }

    private void save(Object device) {
        if (device instanceof InverterEntity value) entityService.save(value);
        else if (device instanceof BatteryEntity value) entityService.save(value);
        else if (device instanceof SmartMeterEntity value) entityService.save(value);
    }

    private UUID id(Object device) {
        if (device instanceof InverterEntity value) return value.getId();
        if (device instanceof BatteryEntity value) return value.getId();
        if (device instanceof SmartMeterEntity value) return value.getId();
        throw new ResponseStatusException(BAD_REQUEST, "Unsupported PV device");
    }

    private String name(Object device) {
        if (device instanceof InverterEntity value) return value.getName();
        if (device instanceof BatteryEntity value) return value.getName();
        if (device instanceof SmartMeterEntity value) return value.getName();
        return "PV device";
    }

    private String deviceType(Object device) {
        if (device instanceof InverterEntity) return "INVERTER";
        if (device instanceof BatteryEntity) return "BATTERY";
        if (device instanceof SmartMeterEntity) return "SMART_METER";
        throw new ResponseStatusException(BAD_REQUEST, "Unsupported PV device");
    }

    private boolean sameProfileName(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record CompatibilityResult(boolean compatible, List<ProfileIssue> issues) {}
    public record ProfileIssue(UUID deviceId, String deviceName, String deviceType, String providerId,
                               String currentProfileName, String currentSectionKey,
                               List<ProfileCandidate> candidates) {}
    public record ProfileCandidate(String profileName, List<ProfileSection> sections) {}
    public record ProfileSection(String sectionKey, String name) {}
    public record ProfileRepair(UUID deviceId, String profileName, String sectionKey) {}
    public record RepairRequest(List<ProfileRepair> repairs) {}
}
