package de.verdox.pv_miner.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Typed, key-only SSH access to Linux GPU tools. No arbitrary command endpoint. */
@Service
public class SshGpuService {
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$");
    private static final Pattern USER = Pattern.compile("^[a-z_][a-z0-9_-]{0,31}$");
    private static final Pattern AMD_MIN = Pattern.compile("MIN_POWER_LIMIT:\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMD_MAX = Pattern.compile("MAX_POWER_LIMIT:\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private final Path identityFile;
    private final Path knownHostsFile;
    @Value("${solarminer.pearl.experimental-enabled:false}")
    private boolean pearlExperimentalEnabled;

    public SshGpuService(@Value("${solarminer.gpu.ssh.identity-file:}") String identityFile,
                         @Value("${solarminer.gpu.ssh.known-hosts-file:}") String knownHostsFile) {
        this.identityFile = identityFile.isBlank() ? null : Path.of(identityFile).toAbsolutePath().normalize();
        this.knownHostsFile = knownHostsFile.isBlank() ? null : Path.of(knownHostsFile).toAbsolutePath().normalize();
    }

    public record Target(String host, String user, int gpuIndex) { }
    public record Inventory(String nvidia, String amd) { }
    public record PowerLimits(double minWatts, double maxWatts) { }

    public Inventory inventory(String host, String user) throws IOException, InterruptedException {
        Target target = new Target(host, user, 0);
        validateTarget(target);
        String nvidia = tryRun(target, "nvidia-smi --query-gpu=index,name,uuid,power.draw,power.limit,temperature.gpu,clocks.gr --format=csv,noheader,nounits");
        String amd = tryRun(target, "amd-smi metric --json");
        if (nvidia.isBlank() && amd.isBlank()) throw new IOException("Neither nvidia-smi nor amd-smi is available on the remote host");
        return new Inventory(nvidia, amd);
    }

    public PowerLimits powerLimits(Target target, String vendor) throws IOException, InterruptedException {
        validateTarget(target);
        String index = Integer.toString(target.gpuIndex());
        if ("NVIDIA".equalsIgnoreCase(vendor)) {
            String output = run(target, "nvidia-smi -i " + index + " --query-gpu=power.min_limit,power.max_limit --format=csv,noheader,nounits");
            String[] parts = output.strip().split(",", 3);
            if (parts.length < 2) throw new IOException("NVIDIA driver did not report power limits");
            try {
                return validLimits(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException e) {
                throw new IOException("NVIDIA driver reported invalid power limits", e);
            }
        }
        if ("AMD".equalsIgnoreCase(vendor)) {
            String output = run(target, "amd-smi static -g " + index + " -l");
            Matcher min = AMD_MIN.matcher(output);
            Matcher max = AMD_MAX.matcher(output);
            if (!min.find() || !max.find()) throw new IOException("AMD driver did not report power limits");
            return validLimits(Double.parseDouble(min.group(1)), Double.parseDouble(max.group(1)));
        }
        throw new IllegalArgumentException("GPU vendor must be NVIDIA or AMD");
    }

    private static PowerLimits validLimits(double min, double max) throws IOException {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0 || max <= min) {
            throw new IOException("GPU power range is unavailable or invalid");
        }
        return new PowerLimits(min, max);
    }

    public void setPowerLimit(Target target, String vendor, int watts) throws IOException, InterruptedException {
        PowerLimits limits = powerLimits(target, vendor);
        if (watts <= 0 || watts < limits.minWatts() || watts > limits.maxWatts()) {
            throw new IllegalArgumentException("Requested power limit is outside the GPU's reported range");
        }
        String index = Integer.toString(target.gpuIndex());
        if ("NVIDIA".equalsIgnoreCase(vendor)) {
            run(target, "sudo -n nvidia-smi -i " + index + " -pl " + watts);
        } else {
            run(target, "sudo -n amd-smi set -g " + index + " -o " + watts);
        }
    }

    /** Clocks are bounded by the driver's supported list; voltage is never set directly. */
    public void lockNvidiaCoreClock(Target target, int mhz) throws IOException, InterruptedException {
        validateTarget(target);
        if (mhz < 100 || mhz > 5000) throw new IllegalArgumentException("Invalid GPU clock");
        String index = Integer.toString(target.gpuIndex());
        String supported = run(target, "nvidia-smi -i " + index + " --query-supported-clocks=graphics --format=csv,noheader,nounits");
        boolean found = Pattern.compile("(?m)^\\s*" + mhz + "\\s*$").matcher(supported).find();
        if (!found) throw new IllegalArgumentException("Clock is not in the GPU's supported clock list");
        run(target, "sudo -n nvidia-smi -i " + index + " -lgc " + mhz + "," + mhz);
    }

    public void resetNvidiaCoreClock(Target target) throws IOException, InterruptedException {
        validateTarget(target);
        run(target, "sudo -n nvidia-smi -i " + target.gpuIndex() + " -rgc");
    }

    /** Controls only the fixed SolarMiner Pearl systemd unit on an enrolled Linux rig. */
    public String pearlServiceStatus(String host, String user) throws IOException, InterruptedException {
        Target target = new Target(host, user, 0);
        validateTarget(target);
        return run(target, "systemctl show solarminer-pearl.service --property=ActiveState,SubState --no-pager").strip();
    }

    public void setPearlServiceRunning(String host, String user, boolean running) throws IOException, InterruptedException {
        Target target = new Target(host, user, 0);
        validateTarget(target);
        if (running && !pearlExperimentalEnabled) throw new IllegalStateException("Pearl start is disabled until fee routing is verified");
        run(target, "sudo -n systemctl " + (running ? "start" : "stop") + " solarminer-pearl.service");
    }

    private String tryRun(Target target, String command) throws InterruptedException {
        try { return run(target, command); } catch (IOException e) { return ""; }
    }

    private void validateTarget(Target target) {
        if (target == null || target.host() == null || !HOST.matcher(target.host()).matches() ||
                target.user() == null || !USER.matcher(target.user()).matches() ||
                target.gpuIndex() < 0 || target.gpuIndex() > 63) {
            throw new IllegalArgumentException("Invalid SSH host, user or GPU index");
        }
    }

    private String run(Target target, String command) throws IOException, InterruptedException {
        if (identityFile == null || knownHostsFile == null || !Files.isRegularFile(identityFile) || !Files.isRegularFile(knownHostsFile)) {
            throw new IOException("GPU SSH identity and known_hosts must be configured on solarminer-core");
        }
        List<String> args = new ArrayList<>(List.of("ssh", "-F", "none", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=yes",
                "-o", "ConnectTimeout=5", "-o", "ConnectionAttempts=1", "-o", "IdentitiesOnly=yes",
                "-o", "UserKnownHostsFile=" + knownHostsFile, "-i", identityFile.toString(), "--",
                target.user() + "@" + target.host(), command));
        Path outputFile = Files.createTempFile("solarminer-gpu-ssh-", ".log");
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile()).start();
            if (!process.waitFor(Duration.ofSeconds(12).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("GPU SSH command timed out");
            }
            if (Files.size(outputFile) > 65536) throw new IOException("GPU SSH output exceeded 64 KiB");
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IOException("GPU SSH command failed: " + output.strip());
            return output;
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }
}
