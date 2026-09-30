package com.test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.CRC32;

/** CPU1-only package validation and wire format, matching firmware tools/package.py. */
public final class OtaImage {
    public static final int ADDRESS = 0x08010000;
    public static final int MAX_SIZE = 0x30000;
    private final byte[] image;
    private final long crc;
    public final long crc32;
    public final String version;
    public final String sha256;
    public OtaImage(byte[] data, int format, String target, String profile, long address,
                    int size, long expectedCrc, String expectedSha, String version) {
        if (format != 1 || !"STM32WB35CE".equals(target) || !"application".equals(profile)
                || address != ADDRESS) throw new IllegalArgumentException("Falsches Firmwareziel oder Paketformat.");
        if (data == null || data.length < 0x140 || data.length > MAX_SIZE || size != data.length)
            throw new IllegalArgumentException("Ungültige Firmwaregröße.");
        CRC32 check = new CRC32(); check.update(data);
        String hash = hash(data);
        if (check.getValue() != expectedCrc || !hash.equalsIgnoreCase(expectedSha))
            throw new IllegalArgumentException("Firmware und Prüfdaten stimmen nicht überein.");
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        long stack = b.getInt() & 0xffffffffL;
        long entry = b.getInt() & 0xffffffffL;
        if ((stack & 7) != 0 || stack <= 0x20000008L || stack > 0x20008000L
                || (entry & 1) == 0 || (entry & ~1L) < ADDRESS || (entry & ~1L) >= ADDRESS+data.length)
            throw new IllegalArgumentException("Ungültiger Startvektor der Firmware.");
        FirmwareVersion release=FirmwareVersion.parse(version);
        if(release.equals(FirmwareVersion.parse("0.2.0")) && data.length>=0x14c
                && ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(0x140)==0x31564753)
            throw new IllegalArgumentException("Firmwareidentität darf nicht als Altstand deklariert werden.");
        if(!release.equals(FirmwareVersion.parse("0.2.0"))) {
            if(release.compareTo(FirmwareVersion.parse("0.3.0"))<0 || data.length<0x14c)
                throw new IllegalArgumentException("Nicht unterstützte Firmwareversion.");
            ByteBuffer identity=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); identity.position(0x140);
            if(identity.getInt()!=0x31564753 || (identity.getShort() & 65535)!=release.major
                    || (identity.getShort() & 65535)!=release.minor || (identity.getShort() & 65535)!=release.patch
                    || identity.get()!=1 || identity.get()!=0)
                throw new IllegalArgumentException("Versionsnummer passt nicht zur Firmwaredatei.");
        }
        image = data.clone(); crc = check.getValue(); crc32=crc; this.version = version; sha256 = hash;
    }
    public static OtaImage fromManifest(byte[] data,org.json.JSONObject m) throws org.json.JSONException {
        String version=m.getString("version");
        if(!version.equals("0.2.0") && (!m.has("protocol") || !m.has("min_bootloader")))
            throw new IllegalArgumentException("Unvollständige Firmware-Kompatibilitätsangaben.");
        Object protocol=m.has("protocol") ? m.get("protocol") : Integer.valueOf(1);
        if(!(protocol instanceof Number) || ((Number)protocol).doubleValue()!=1 || FirmwareVersion.parse(m.has("min_bootloader") ? m.getString("min_bootloader") : "0.2.0")
                .compareTo(FirmwareVersion.parse("0.2.0"))>0)
            throw new IllegalArgumentException("Dieses Paket benötigt einen neueren Bootloader per Kabel.");
        return new OtaImage(data,m.getInt("format"),m.getString("target"),m.getString("profile"),
            Long.decode(m.getString("address")),m.getInt("size"),Long.parseLong(m.getString("crc32"),16),
            m.getString("sha256"),version);
    }
    public int size() { return image.length; }
    public static String hash(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder text = new StringBuilder();
            for (byte value : digest) text.append(String.format(java.util.Locale.US,"%02x",value & 255));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public byte[] begin() {
        return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .put("SGU1".getBytes(StandardCharsets.US_ASCII)).putInt(image.length).putInt((int)crc).array();
    }
    public byte[] packet(int offset) {
        if (offset < 0 || offset >= image.length || offset % 16 != 0) throw new IllegalArgumentException("Offset");
        int end = Math.min(offset+16,image.length);
        return ByteBuffer.allocate(4+end-offset).order(ByteOrder.LITTLE_ENDIAN).putInt(offset)
            .put(Arrays.copyOfRange(image,offset,end)).array();
    }
    public byte[] end() { return "END1".getBytes(StandardCharsets.US_ASCII); }
}
