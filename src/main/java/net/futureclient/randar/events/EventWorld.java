package net.futureclient.randar.events;

import com.google.gson.JsonObject;

@Deprecated // worlds table should probably be managed separately/manually
public class EventWorld {
    public final String server;
    final short dimension;
    public final long seed;

    public EventWorld(JsonObject json) {
        this.server = json.get("server").getAsString();
        this.dimension = json.get("dimension").getAsShort();
        this.seed = json.get("seed").getAsLong();
    }
}