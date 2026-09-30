package com.test;

import java.util.ArrayDeque;

/** Message-level flow control. Kept in memory only, with expiry and observable rejection. */
public final class MessageOutbox {
    public static final int CAPACITY = 16;
    public static final long TTL_MS = 60000;
    public static final class Message {
        public final String text;
        public final long created;
        Message(String text, long now) { this.text = text; this.created = now; }
    }
    private final ArrayDeque<Message> messages = new ArrayDeque<>();
    private int dropped;
    private Message inFlight;
    public boolean offer(String text, long now) {
        expire(now);
        if (text == null || text.trim().isEmpty()) return false;
        if (messages.size() >= CAPACITY) { dropped++; return false; }
        // Store only what the firmware can receive; never retain unbounded notification text.
        StringBuilder limited = new StringBuilder();
        int bytes = 0;
        for (int offset=0; offset<text.length();) {
            int cp = text.codePointAt(offset); offset += Character.charCount(cp);
            if (cp == 0) cp = ' ';
            String glyph = new String(Character.toChars(cp));
            int length = glyph.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes + length > 252) break;
            limited.append(glyph); bytes += length;
        }
        messages.add(new Message(limited.toString(), now));
        return true;
    }
    public void expire(long now) {
        java.util.Iterator<Message> iterator=messages.iterator();
        while (iterator.hasNext()) {
            Message message=iterator.next();
            if (message!=inFlight && now-message.created >= TTL_MS) { iterator.remove(); dropped++; }
        }
    }
    public void started(Message message) { inFlight=message; }
    public Message peek(long now) { expire(now); return messages.peek(); }
    public void delivered(Message message) { messages.remove(message); if (inFlight==message) inFlight=null; }
    public void failed(Message message) { if (messages.remove(message)) dropped++; if (inFlight==message) inFlight=null; }
    public void discardWaiting() {
        java.util.Iterator<Message> iterator=messages.iterator();
        while (iterator.hasNext()) if (iterator.next()!=inFlight) { iterator.remove(); dropped++; }
    }
    public void clear() { dropped += messages.size(); messages.clear(); inFlight=null; }
    public int size(long now) { expire(now); return messages.size(); }
    public int dropped() { return dropped; }
}
