package net.futureclient.randar;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import net.futureclient.randar.events.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

public class Main {
    private static Thread eventProcessingThread;
    private static Thread seedCopyingThread;
    private static SeedReverseServer server;

    private static int copyNewSeedEvents() throws SQLException {
        try (Connection con = Database.POOL.getConnection()) {
            con.setAutoCommit(false);

            final OptionalLong maxID = Database.maxSeedID(con);
            if (maxID.isEmpty()) {
                return 0;
            }
            final long progress = Database.getSeedCopyProgress(con);
            if (progress >= maxID.getAsLong()) {
                return 0;
            }
            final long copyUpTo = Math.min(maxID.getAsLong(), progress + 100000);
            final int updates = Database.copyNewSeedEvents(con, copyUpTo);
            Database.updateSeedCopyProgress(con, copyUpTo);

            con.commit();
            return updates;
        }
    }

    private static int processEvents(int limit) throws SQLException {
        try (Connection con = Database.POOL.getConnection()) {
            con.setAutoCommit(false);
            var events = Database.queryNewEvents(con, limit);
            System.out.println("Got " + events.size() + " events");
            var serverIdCache = new Object2IntArrayMap<String>();
            for (var event : events) {
                final String type = event.json.get("type").getAsString();
                final String id = event.json.get("nyan4id").getAsString();
                // troll instances of plugin
                if (id.equals("1959688@blahaj:09kMbUMBWm6S-3GmDfTAcA") || id.equals("1959688@blahaj:15USeyDL5v7Fa8Km9hNf7w")) continue;
                switch (type) {
                    // TODO: probably want to copy seeds separately
                    case "seed":
                        final var eventSeed = new EventSeed(event.json);
                        Database.addNewSeed(con, eventSeed, serverIdCache);
                        break;
                    case "player_join":
                        final var eventJoin = new EventPlayerSession(event.json);
                        Database.onPlayerJoin(con, eventJoin, serverIdCache);
                        break;
                    case "player_leave":
                        final var eventLeave = new EventPlayerSession(event.json);
                        Database.onPlayerLeave(con, eventLeave, serverIdCache);
                        break;
                    case "start":
                        final var eventStart = new EventStart(event.json);
                        Database.onStart(con, eventStart, event.id, serverIdCache);
                        break;
                    case "stop":
                        final var eventStop = new EventStop(event.json);
                        Database.onStop(con, eventStop, serverIdCache);
                        break;
                    case "heartbeat":
                        //final var eventHeartbeat = new EventHeartbeat(event.json);
                        // nothing to be done here
                        break;
                    case "plugin_startup":
                        final var eventStartup = new PluginStartupEvent(event.json);
                        Database.onPluginStart(con, eventStartup, event.id);
                        break;
                    case "detach":
                        break;
                    default:
                        System.out.println("Unknown event type: " + type);
                        //throw new IllegalStateException("Unknown event type:" + type);
                }
            }
            if (!events.isEmpty()) {
                Database.updateEventProgress(con, events.get(events.size() - 1).id);
            }
            con.commit();
            return events.size();
        }
    }


    public static void main(String[] args) {
        new Database();
        //Database.backfillPlayerNameHistoryFromEvents();
        new Thread(() -> {
            try {
                while (true) {
                    while (Associator.doAssociate()) System.out.println("Associating");
                    Thread.sleep(TimeUnit.MINUTES.toMillis(1));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        eventProcessingThread = new Thread(() -> {
            while (true) {
                final int limit = 1000;
                try {
                    int processed;
                    do {
                        processed = processEvents(limit);
                        System.out.println("Processed " + processed + " events");
                    } while (processed >= limit);

                    Thread.sleep(5000);
                } catch (SQLException ex) {
                    System.out.println("Caught SQL exception while processing events");
                    ex.printStackTrace();
                    System.exit(1);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        });
        seedCopyingThread = new Thread(() -> {
            while (true) {
                try {
                    final int copies = copyNewSeedEvents();
                    System.out.println("Copied " + copies + " seeds from events table");

                    Thread.sleep(5000);
                } catch (SQLException ex) {
                    System.out.println("Caught SQL exception from copying seed events");
                    ex.printStackTrace();
                    System.exit(1);
                } catch (InterruptedException ex) {
                    return;
                }
            }
        });
        try {
            server = new SeedReverseServer();
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        server.start();
        eventProcessingThread.start();
        seedCopyingThread.start();
    }
}
