package com.test;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** Call from one event loop. Each ATT operation completes before the next starts. */
public final class GattQueue {
    public interface Driver {
        long now();
        boolean ready();
        boolean start(Operation operation);
        void later(Runnable task, long delayMs);
        void failed(String reason);
    }
    public static final class Operation {
        public final UUID uuid;
        public final byte[] data; // null means read
        public Operation(UUID uuid, byte[] data) {
            this.uuid = uuid; this.data = data == null ? null : data.clone();
        }
    }
    private final Driver driver;
    private final ArrayDeque<Operation> operations = new ArrayDeque<>();
    private boolean inFlight;
    private int attempts;
    private long generation, queuedAt;
    public GattQueue(Driver driver) { this.driver = driver; }
    public boolean enqueue(List<Operation> batch) {
        if (batch.isEmpty() || operations.size() + batch.size() > 64) return false;
        boolean idle = operations.isEmpty();
        operations.addAll(batch);
        if (idle) { queuedAt = driver.now(); attempts = 0; }
        pump(); return true;
    }
    public void wake() { pump(); }
    public void clear() { operations.clear(); inFlight = false; attempts = 0; ++generation; }
    public void complete(UUID uuid, boolean success) {
        if (!inFlight || operations.isEmpty() || !operations.peek().uuid.equals(uuid)) return;
        if (!success) { fail("Bluetooth write/read rejected"); return; }
        operations.remove(); inFlight = false; attempts = 0; ++generation;
        queuedAt = driver.now(); pump();
    }
    private void fail(String reason) { clear(); driver.failed(reason); }
    private void pump() {
        if (inFlight || operations.isEmpty()) return;
        final long token = ++generation;
        if (!driver.ready()) {
            if (driver.now() - queuedAt >= 60000) { fail("Bluetooth pairing timed out"); return; }
            driver.later(() -> { if (generation == token) pump(); }, 200);
            return;
        }
        if (!driver.start(operations.peek())) {
            if (++attempts >= 10) { fail("Bluetooth is busy"); return; }
            driver.later(() -> { if (generation == token) pump(); }, 100); return;
        }
        inFlight = true;
        driver.later(() -> {
            if (generation == token && inFlight) fail("Bluetooth operation timed out");
        }, 5000);
    }
}
