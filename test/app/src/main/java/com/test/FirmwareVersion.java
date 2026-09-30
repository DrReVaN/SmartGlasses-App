package com.test;

import java.util.Objects;

/** Canonical numeric release versions; each component fits the firmware's uint16. */
public final class FirmwareVersion implements Comparable<FirmwareVersion> {
    public final int major, minor, patch;
    public FirmwareVersion(int major,int minor,int patch) {
        if (major<0 || minor<0 || patch<0 || major>65535 || minor>65535 || patch>65535)
            throw new IllegalArgumentException("Ungültige Versionsnummer.");
        this.major=major; this.minor=minor; this.patch=patch;
    }
    public static FirmwareVersion parse(String text) {
        if (text==null || !text.matches("(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})"))
            throw new IllegalArgumentException("Ungültige Versionsnummer.");
        String[] parts=text.split("\\.");
        return new FirmwareVersion(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
    }
    @Override public int compareTo(FirmwareVersion other) {
        int n=Integer.compare(major,other.major);
        if(n==0)n=Integer.compare(minor,other.minor);
        return n==0 ? Integer.compare(patch,other.patch) : n;
    }
    @Override public String toString() { return major+"."+minor+"."+patch; }
    @Override public boolean equals(Object other) {
        return other instanceof FirmwareVersion && compareTo((FirmwareVersion)other)==0;
    }
    @Override public int hashCode() { return Objects.hash(major,minor,patch); }
}
