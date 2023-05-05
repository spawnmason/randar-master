package net.futureclient.randar.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class EventHeartbeat {
    public final long timestamp;
    //public final String[] servers;

    public EventHeartbeat(JsonObject json) {
        this.timestamp = json.get("timestamp").getAsLong();
        /*this.servers = json.get("servers").getAsJsonArray().asList().stream()
                .map(JsonElement::getAsString)
                .toArray(String[]::new);*/
    }
}