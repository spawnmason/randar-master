package net.futureclient.randar;

import com.google.gson.JsonObject;

public class EventSeed {
    public final long seed;
    public final long timestamp;
    public final short dimension;
    public final String server;

    public EventSeed(JsonObject json) {
        this.seed = json.get("seed").getAsLong();
        this.timestamp = json.get("timestamp").getAsLong();
        this.dimension = json.get("dimension").getAsShort();
        this.server = json.get("server").getAsString();
    }
}
