package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** Local owner switches. They are checked by every Node-facing command, not only by the UI. */
@Service
public class AgentControlSettingsService {
    private final ObjectMapper json;
    private final Path file;
    private volatile Settings settings;

    public record Settings(boolean dynamicPowerScalingEnabled, boolean externalControlEnabled,
                           Map<String, Boolean> workerExternalControl) {
        public Settings {
            workerExternalControl = workerExternalControl == null ? Map.of() : Map.copyOf(workerExternalControl);
        }
        public Settings(boolean dynamicPowerScalingEnabled, boolean externalControlEnabled) {
            this(dynamicPowerScalingEnabled, externalControlEnabled, Map.of());
        }
        public boolean workerEnabled(String workerId) {
            return !Boolean.FALSE.equals(workerExternalControl.get(workerId));
        }
    }

    public AgentControlSettingsService(ObjectMapper json,
            @Value("${solarminer.agent.control-settings-file:./solarminer-agent/control-settings.json}") String path) {
        this.json = json; this.file = Path.of(path).toAbsolutePath().normalize(); this.settings = load();
    }
    public Settings get() { return settings; }
    public boolean workerEnabled(String workerId) { return settings.workerEnabled(workerId); }
    public synchronized boolean update(Settings value) {
        if (value == null || !persist(value)) return false;
        settings = value; return true;
    }
    public synchronized boolean setWorkerEnabled(String workerId, boolean enabled) {
        if (workerId == null || workerId.isBlank()) return false;
        Map<String, Boolean> next = new java.util.HashMap<>(settings.workerExternalControl());
        if (enabled) next.remove(workerId); else next.put(workerId, false);
        return update(new Settings(settings.dynamicPowerScalingEnabled(), settings.externalControlEnabled(), next));
    }
    /** A new agent needs explicit local consent before a Node may control it over the LAN. */
    private Settings load() { try { return json.readValue(Files.readString(file), Settings.class); } catch (Exception ignored) { return new Settings(true, false); } }
    private boolean persist(Settings value) {
        try {
            Files.createDirectories(file.getParent()); Path temp = Files.createTempFile(file.getParent(), "agent-control-", ".json");
            try { json.writeValue(temp.toFile(), value); Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temp); }
            return true;
        } catch (IOException e) { return false; }
    }
}
