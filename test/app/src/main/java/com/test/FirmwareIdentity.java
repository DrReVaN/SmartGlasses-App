package com.test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Discovery protocol bytes stay separate from the actual running release. */
public final class FirmwareIdentity {
    public final FirmwareVersion version;
    public final int mode;
    public final long size, crc32;
    private FirmwareIdentity(FirmwareVersion version,int mode,long size,long crc32) {
        this.version=version; this.mode=mode; this.size=size; this.crc32=crc32;
    }
    public static FirmwareIdentity read(byte[] data) {
        if (data==null || (data.length!=4 && data.length!=20) || data[0]!=0 || data[1]!=2 || data[2]!=0
                || (data[3]!=0 && data[3]!=1)) throw new IllegalArgumentException("Unbekanntes BLE-Protokoll.");
        if(data.length==4) return new FirmwareIdentity(null,data[3],0,0);
        if(data[10]!=1 || data[11]!=0) throw new IllegalArgumentException("Unbekanntes OTA-Protokoll.");
        ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); b.position(4);
        FirmwareVersion v=new FirmwareVersion(b.getShort() & 65535,b.getShort() & 65535,b.getShort() & 65535);
        if(v.compareTo(FirmwareVersion.parse("0.3.0"))<0) throw new IllegalArgumentException("Ungültige Firmwareidentität.");
        b.position(12); long size=b.getInt() & 0xffffffffL, crc=b.getInt() & 0xffffffffL;
        if(data[3]==0 && (size<0x140 || size>OtaImage.MAX_SIZE)) throw new IllegalArgumentException("Ungültige Firmwaregröße.");
        return new FirmwareIdentity(v,data[3],size,crc);
    }
    public boolean confirms(OtaImage image) {
        if(mode!=0 || image==null) return false;
        if(version==null) return image.version.equals("0.2.0"); // Legacy cannot expose its build fingerprint.
        return version.toString().equals(image.version) && size==image.size() && crc32==image.crc32;
    }
}
