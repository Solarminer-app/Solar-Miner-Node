package de.verdox.solarminer.pcagent.mining;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps every line of each local miner's console in a separate, append-only file. */
@Service
public class MinerConsoleService {
    private static final Logger LOGGER = Logger.getLogger(MinerConsoleService.class.getName());
    private static final int CHUNK_BYTES = 64 * 1024;
    private final Path directory = Path.of("./solarminer-agent/logs").toAbsolutePath().normalize();

    public synchronized void started(String miner) {
        append(miner, "\n===== " + Instant.now() + " · Neuer Miner-Start =====");
    }

    public synchronized void append(String miner, String line) {
        try {
            Files.createDirectories(directory);
            Files.write(file(miner), (line + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save " + miner + " console output", e);
        }
    }

    public ConsoleChunk read(String miner, long offset) throws IOException {
        Path path = file(miner);
        if (!Files.isRegularFile(path)) return new ConsoleChunk(0, "", false);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long length = channel.size();
            long start = offset < 0 || offset > length ? 0 : offset;
            ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(CHUNK_BYTES, length - start));
            channel.position(start);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
            int count = buffer.position();
            return new ConsoleChunk(start + count,
                    Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(buffer.array(), count)),
                    start + count < length);
        }
    }

    public Path file(String miner) {
        if (miner.matches("pearl-(NVIDIA|AMD)-[0-9]{1,5}"))
            return directory.resolve(miner + "-console.log");
        return directory.resolve(switch (miner) {
            case "monero" -> "xmrig-console.log";
            case "pearl" -> "srbminer-console.log";
            default -> throw new IllegalArgumentException("Unknown miner");
        });
    }

    public record ConsoleChunk(long nextOffset, String data, boolean hasMore) { }
}
