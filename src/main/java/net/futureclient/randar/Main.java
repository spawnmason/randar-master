package net.futureclient.randar;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.futureclient.randar.events.*;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    private static void processEvents() throws SQLException {
        try (Connection con = Database.POOL.getConnection()) {
            con.setAutoCommit(false);
            var events = Database.queryNewEvents(con);
            var serverIdCache = new Object2IntArrayMap<String>();
            var playerIdCache = new Object2IntArrayMap<String>();
            for (var event : events) {
                final String type = event.json.get("type").getAsString();
                switch (type) {
                    // TODO: probably want to copy seeds separately
                    case "seed":
                        final var eventSeed = new EventSeed(event.json);
                        Database.addNewSeed(con, eventSeed, serverIdCache);
                        break;
                    case "player_join":
                        final var eventJoin = new EventPlayerSession(event.json);
                        Database.onPlayerJoin(con, eventJoin, serverIdCache, playerIdCache);
                        break;
                    case "player_leave":
                        final var eventLeave = new EventPlayerSession(event.json);
                        Database.onPlayerLeave(con, eventLeave, serverIdCache, playerIdCache);
                        break;
                    case "start":
                        final var eventStart = new EventStart(event.json);
                        // TODO: close open sessions using the timestamp of the last heartbeat
                        break;
                    case "stop":
                        final var eventStop = new EventStop(event.json);
                        Database.onStop(con, eventStop, serverIdCache);
                        break;
                    case "heartbeat":
                        //final var eventHeartbeat = new EventHeartbeat(event.json);
                        // nothing to be done here
                        break;
                    default:
                        throw new IllegalStateException("Unknown event type:" + type);
                }
            }
            if (!events.isEmpty()) {
                Database.updateEventProgress(con, events.get(events.size() - 1).id);
            }
            con.commit();
        }
    }


    public static void main(String[] args) throws SQLException {
        System.out.println("Hello world!");
        new Database();

    }
}