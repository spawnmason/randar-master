package net.futureclient.randar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

// this represents either joins or leaves
public class EventPlayerSession {
    public final long time;
    public final String server;
    public final UUID[] players;

    public EventPlayerSession(JsonObject json) {
        this.time = json.get("time").getAsLong();
        this.server = json.get("server").getAsString();
        this.players = json.get("players").getAsJsonArray().asList().stream()
                .map(JsonElement::getAsString)
                .map(UUID::fromString)
                .toArray(UUID[]::new);
    }
}
