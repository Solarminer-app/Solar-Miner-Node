package de.verdox.cgminerapi;

public record APIVersion(int major, int minor)
        implements Comparable<APIVersion> {

    public static APIVersion of(int major, int minor) {
        return new APIVersion(major, minor);
    }

    @Override
    public int compareTo(APIVersion o) {
        int cmp = Integer.compare(major, o.major);
        return cmp != 0
                ? cmp
                : Integer.compare(minor, o.minor);
    }

    public boolean supports(APIVersion required) {
        return compareTo(required) >= 0;
    }
}
