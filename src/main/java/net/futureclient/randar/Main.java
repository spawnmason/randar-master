package net.futureclient.randar;

import it.unimi.dsi.fastutil.objects.Object2ShortArrayMap;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    private static void processEvents() throws SQLException {
        try (Connection con = Database.POOL.getConnection()) {
            con.setAutoCommit(false);
            var events = Database.queryNewEvents(con);
            var serverIdCache = new Object2ShortArrayMap<String>();
            for (var event : events) {
                final String type = event.json.get("type").getAsString();
                switch (type) {
                    case "seed":
                        final var eventSeed = new EventSeed(event.json);
                        Database.addNewSeed(con, eventSeed, serverIdCache);
                        break;
                    default:
                        System.out.println("Unknown event type: type");
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