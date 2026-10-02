package de.verdox.pv_miner.discovery;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Bounded LAN scans. Results are snapshots; late probes cannot change a returned report. */
@Service
public class PvDiscoveryScanner {
    public record Result<T>(List<T> devices, int checkedHosts, int totalHosts, boolean complete) {}
    private final Semaphore activeScan = new Semaphore(1);

    public static String validateSubnet(String prefix) {
        if (prefix == null || !prefix.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\."))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a private IPv4 subnet, for example 192.168.1.");
        String[] parts = prefix.split("\\.");
        int a = Integer.parseInt(parts[0]), b = Integer.parseInt(parts[1]), c = Integer.parseInt(parts[2]);
        if (a > 255 || b > 255 || c > 255 || !(a == 10 || a == 192 && b == 168 || a == 172 && b >= 16 && b <= 31))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discovery is limited to private IPv4 networks");
        return a + "." + b + "." + c + ".";
    }

    public <T> Result<T> scan(String prefix, Function<String, List<T>> probe) {
        List<String> hosts = new ArrayList<>();
        for (int i = 1; i <= 254; i++) hosts.add(validateSubnet(prefix) + i);
        return scan(hosts, probe, Duration.ofSeconds(25));
    }

    <T> Result<T> scan(List<String> hosts, Function<String, List<T>> probe, Duration timeout) {
        if (!activeScan.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "A discovery scan is already running");
        var executor = Executors.newFixedThreadPool(24, Thread.ofVirtual().factory());
        var found = new ArrayList<T>();
        var checked = new AtomicInteger();
        var completion = new ExecutorCompletionService<Void>(executor);
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            for (String host : hosts) completion.submit(() -> {
                if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) return null;
                var devices = probe.apply(host);
                synchronized (found) {
                    found.addAll(devices);
                    checked.incrementAndGet();
                }
                return null;
            });
            for (int i = 0; i < hosts.size(); i++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                var completed = completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (completed == null) break;
                completed.get();
            }
            synchronized (found) {
                int checkedHosts = checked.get();
                return new Result<>(List.copyOf(found), checkedHosts, hosts.size(), checkedHosts == hosts.size());
            }
        } catch (ExecutionException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A discovery probe failed", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Discovery was interrupted", exception);
        } finally {
            executor.shutdownNow();
            // Retain the scan slot until in-flight hardware I/O has actually stopped.
            Thread.startVirtualThread(() -> {
                try { while (!executor.awaitTermination(1, TimeUnit.SECONDS)) { /* wait for I/O */ } }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                finally { activeScan.release(); }
            });
        }
    }
}
