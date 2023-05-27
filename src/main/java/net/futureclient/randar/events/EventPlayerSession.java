package net.futureclient.randar.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.UUID;

// this represents either joins or leaves
public class EventPlayerSession {
    public final long time;
    public final String server;
    //public final UUID[] players;
    public final UUID uuid;
    public final String username;

    public EventPlayerSession(JsonObject json) {
        this.time = json.get("tracker_timestamp").getAsLong();
        this.server = json.get("server").getAsString();
        /*this.players = json.get("players").getAsJsonArray().asList().stream()
                .map(JsonElement::getAsString)
                .map(UUID::fromString)
                .toArray(UUID[]::new);*/
        this.uuid = UUID.fromString(json.get("uuid").getAsString());
        this.username = json.get("username").getAsString();
    }
}