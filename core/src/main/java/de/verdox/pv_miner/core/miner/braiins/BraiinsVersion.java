package de.verdox.pv_miner.core.miner.braiins;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record BraiinsVersion(String raw, int major, int minor, int patch,
                             boolean plus) implements Comparable<BraiinsVersion> {

    public static final BraiinsVersion FIRST_GRPC_VERSION = BraiinsVersion.of(23, 3, 0, false);

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public static BraiinsVersion parse(String raw) {
        Objects.requireNonNull(raw, "raw");

        Matcher matcher = VERSION_PATTERN.matcher(raw);

        if (!matcher.find()) {
            throw new IllegalArgumentException("Could not parse Braiins version: " + raw);
        }

        int major = Integer.parseInt(matcher.group(1));

        int minor = Integer.parseInt(matcher.group(2));

        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

        boolean plus = raw.endsWith("-plus") || raw.contains("-plus-") || raw.contains("-plus");

        return new BraiinsVersion(raw, major, minor, patch, plus);
    }

    public static BraiinsVersion of(int major, int minor, int patch, boolean plus) {
        String raw = String.format("%02d.%02d.%d%s", major, minor, patch, plus ? "-plus" : "");
        return new BraiinsVersion(raw, major, minor, patch, plus);
    }

    public boolean supportsGraphQL() {
        return true;
    }

    public boolean supportsGrpc() {
        return compareTo(FIRST_GRPC_VERSION) >= 0;
    }

    public boolean isLegacy() {
        return !supportsGrpc();
    }

    @Override
    public int compareTo(BraiinsVersion other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) return result;

        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;

        return Integer.compare(patch, other.patch);
    }

    public String semanticVersion() {
        return String.format("%02d.%02d.%d", major, minor, patch);
    }

    @Override
    public String toString() {
        return raw;
    }
}