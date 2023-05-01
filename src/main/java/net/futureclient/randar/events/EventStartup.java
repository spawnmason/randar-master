package net.futureclient.randar.events;

import com.google.gson.JsonObject;

public class EventStartup {
    public final long timestamp;
    public final String server;

    public EventStartup(JsonObject json) {
        this.timestamp = json.get("timestamp").getAsLong();
        this.server = json.get("server").getAsString();
    }
}