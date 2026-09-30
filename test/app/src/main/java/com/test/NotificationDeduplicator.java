package com.test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
/** Bounded keys and hashes; no additional plaintext notification storage. */
public final class NotificationDeduplicator {
    private static final class Entry {
        final String hash; final long time;
        Entry(String hash,long time) { this.hash=hash; this.time=time; }
    }
    private final LinkedHashMap<String,Entry> entries = new LinkedHashMap<>();
    public boolean accept(String key,String text,long now) {
        Iterator<Map.Entry<String,Entry>> iterator=entries.entrySet().iterator();
        while (iterator.hasNext()) if (now-iterator.next().getValue().time >= 300000) iterator.remove();
        String hash=OtaImage.hash(text.getBytes(StandardCharsets.UTF_8));
        Entry previous=entries.get(key);
        if (previous!=null && previous.hash.equals(hash)) return false;
        entries.remove(key); entries.put(key,new Entry(hash,now));
        if (entries.size()>128) entries.remove(entries.keySet().iterator().next());
        return true;
    }
    public void remove(String key) { entries.remove(key); }
    public void clear() { entries.clear(); }
}
