package net.futureclient.randar.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class EventSeed {
    public final long[] seeds;
    public final long timestamp;
    public final short dimension;
    public final String server;

    public EventSeed(JsonObject json) {
        this.seeds = json.get("seeds").getAsJsonArray().asList().stream()
                .map(JsonElement::getAsString)
                .mapToLong(Long::parseLong)
                .toArray();
        this.timestamp = json.get("timestamp").getAsLong();
        this.dimension = json.get("dimension").getAsShort();
        this.server = json.get("server").getAsString();
    }
}