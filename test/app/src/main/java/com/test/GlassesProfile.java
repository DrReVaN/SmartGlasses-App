package com.test;

import java.util.UUID;

/** Complete project UUIDs; service identity is checked before pairing or sending. */
public final class GlassesProfile {
    private GlassesProfile() {}
    private static UUID service(String id) { return UUID.fromString(id + "-cc7a-482a-984a-7f2ed5b3e58f"); }
    private static UUID characteristic(String id) { return UUID.fromString(id + "-8e22-4541-9d4c-21edae82ed19"); }
    public static final UUID INFO = service("00000010");
    public static final UUID RECEIVE = service("00000020");
    public static final UUID OTA = service("0000fe20");
    public static final UUID VERSION = characteristic("00000011");
    public static final UUID NAME = characteristic("00000012");
    public static final UUID DIAGNOSTICS = characteristic("00000013");
    public static final UUID TIME = characteristic("00000021");
    public static final UUID MESSAGE = characteristic("00000022");
    public static final UUID CONTROL = characteristic("00000023");
    public static final UUID BEGIN = characteristic("0000fe21");
    public static final UUID DATA = characteristic("0000fe22");
    public static final UUID END = characteristic("0000fe23");
    public static boolean supportedVersion(byte[] version) {
        try { FirmwareIdentity.read(version); return true; }
        catch (IllegalArgumentException e) { return false; }
    }
}
