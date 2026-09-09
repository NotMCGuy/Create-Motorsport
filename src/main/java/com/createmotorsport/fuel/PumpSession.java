package com.createmotorsport.fuel;

import java.util.UUID;

public final class PumpSession {
    private UUID owner;
    private UUID id;
    private int slot = -1;
    private long nextTransfer;

    public UUID owner() { return owner; }
    public UUID id() { return id; }
    public int slot() { return slot; }
    public boolean held() { return owner != null; }
    public void start(UUID owner, int slot) {
        this.owner = owner;
        this.id = UUID.randomUUID();
        this.slot = slot;
        nextTransfer = 0;
    }
    public boolean matches(UUID owner, UUID id) {
        return held() && this.owner.equals(owner) && this.id.equals(id);
    }
    public boolean claimTransfer(long tick) {
        if (!held() || tick < nextTransfer) return false;
        nextTransfer = tick + 5;
        return true;
    }
    public void reset() { owner = null; id = null; slot = -1; }
    public void readClient(UUID owner, UUID id) { this.owner = owner; this.id = id; }
}
