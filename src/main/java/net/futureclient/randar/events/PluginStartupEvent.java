package net.futureclient.randar.events;

import com.google.gson.JsonObject;

public class PluginStartupEvent {
    public final long time;

    public PluginStartupEvent(JsonObject json) {
        this.time = json.get("timestamp").getAsLong();
    }
}