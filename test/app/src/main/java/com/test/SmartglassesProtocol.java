package com.test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
public final class SmartglassesProtocol {
    private SmartglassesProtocol() {}
    /** Bound bytes, preserving whole Unicode code points. Matches firmware's 252-byte staging buffer. */
    public static List<byte[]> frames(String text) {
        if (text == null) return new ArrayList<>();
        StringBuilder limited = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset); offset += Character.charCount(cp);
            if (cp == 0) cp = ' ';
            String glyph = new String(Character.toChars(cp));
            int n = glyph.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + n > 252) break;
            limited.append(glyph); bytes += n;
        }
        byte[] raw = limited.toString().getBytes(StandardCharsets.UTF_8);
        List<byte[]> result = new ArrayList<>();
        int count = (raw.length + 17) / 18;
        for (int offset = 0; offset < raw.length; offset += 18) {
            int length = Math.min(18, raw.length - offset);
            byte[] frame = new byte[length + 2];
            frame[0] = (byte)(offset / 18); frame[1] = (byte)count;
            System.arraycopy(raw, offset, frame, 2, length); result.add(frame);
        }
        return result;
    }
}
