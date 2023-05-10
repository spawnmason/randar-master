package net.futureclient.randar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.futureclient.randar.events.*;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Database {
    public static final BasicDataSource POOL = connect();

    private static BasicDataSource connect() {
        for (int i = 0; i < 60; i++) {
            var pool = tryConnect();
            if (pool.isPresent()) {
                return pool.get();
            } else {
                System.out.println("Waiting 1 second then trying again...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException iex) {}
            }
        }
        System.out.println("Unable to connect, giving up.");
        System.exit(1);
        return null;
    }

    private static Optional<BasicDataSource> tryConnect() {
        System.out.println("Connecting to database...");
        BasicDataSource POOL = new BasicDataSource();
        POOL.setUsername(Objects.requireNonNull(System.getenv("PSQL_USER"), "Missing username for database"));
        POOL.setPassword(Objects.requireNonNull(System.getenv("PSQL_PASS"), "Missing password for database"));
        POOL.setDriverClassName("org.postgresql.Driver");
        POOL.setUrl(Objects.requireNonNull(System.getenv("PSQL_URL"), "Missing url for database"));
        POOL.setInitialSize(1);
        POOL.setMaxTotal(75);
        POOL.setAutoCommitOnReturn(true); // make absolutely sure
        POOL.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        POOL.setRollbackOnReturn(true);
        //POOL.setDefaultReadOnly(NoComment.DRY_RUN);
        try {
            if (!POOL.getConnection().isValid(5)) {
                System.out.println("POOL.getConnection().isValid() returned false");
                return Optional.empty();
            }
        } catch (SQLException ex) {
            System.out.println("POOL.getConnection() threw an exception!");
            ex.printStackTrace();
            try {
                POOL.close();
            } catch (SQLException ex2) {}
            return Optional.empty();
        }
        System.out.println("Connected.");
        return Optional.of(POOL);
    }

    public static class Event {
        public final long id;
        public final JsonObject json;

        public Event(long id, JsonObject json) {
            this.id = id;
            this.json = json;
        }
    }

    public static List<Event> queryNewEvents(Connection con, int limit) throws SQLException {
        List<Event> out = new ArrayList<>();
        // should we have an index?
        try (PreparedStatement statement = con.prepareStatement("SELECT id,event FROM events WHERE id > (SELECT id FROM event_progress) AND event->>'type' NOT IN ('seed', 'attach') ORDER BY id LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    final long id = rs.getLong(1);
                    final String json = rs.getString(2);
                    out.add(new Event(id, JsonParser.parseString(json).getAsJsonObject()));
                }
            }
        }

        return out;
    }

    public static void updateEventProgress(Connection con, long id) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement("UPDATE event_progress SET id = ?")) {
            statement.setLong(1, id);
            statement.execute();
        }
    }

    private static OptionalInt queryId(Connection con, String name, String query) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement(query)) {
            statement.setString(1, name);
            statement.execute();
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return OptionalInt.empty();
                }
                return OptionalInt.of(rs.getInt(1));
            }
        }
    }

    private static OptionalInt getIdByString(Connection con, String name, Object2IntMap<String> cache, String query) throws SQLException {
        int id = cache.getOrDefault(name, -1);
        if (id == -1) {
            id = queryId(con, name, query).orElse(-1);
            if (id != -1) {
                cache.put(name, id);
            }
        }
        if (id == -1) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(id);
    }

    private static short getServerId(Connection con, String server, Object2IntMap<String> cache) throws SQLException {
        return (short) getIdByString(con, server, cache, "SELECT id FROM servers WHERE name = ?").orElseThrow(() -> new SQLException("No server id matching \"" + server + "\""));
    }

    private static int insertPlayer(Connection con, String uuid) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement("INSERT INTO players(uuid) VALUES(?::UUID) RETURNING id")) {
            statement.setString(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("no id returned from insertPlayer?");
                return rs.getInt(1);
            }
        }
    }

    private static int getOrInsertPlayerId(Connection con, UUID uuid, Object2IntMap<String> cache) throws SQLException {
        final String uuidString = uuid.toString();
        OptionalInt existing = getIdByString(con, uuidString, cache, "SELECT id FROM players WHERE uuid = ?::UUID");
        if (existing.isPresent()) {
            return existing.getAsInt();
        }
        int newId = insertPlayer(con, uuidString);
        cache.put(uuidString, newId);
        return newId;
    }

    public static void addNewSeed(Connection con, EventSeed event, Object2IntMap<String> serverIdCache) throws SQLException {
        final short serverId = getServerId(con, event.server, serverIdCache);

        for (long seed : event.seeds) {
            try (PreparedStatement statement = con.prepareStatement("INSERT INTO rng_seeds_not_yet_processed VALUES(?, ?, ?, ?)")) {
                statement.setShort(1, serverId);
                statement.setLong(2, event.dimension);
                statement.setLong(3, event.timestamp);
                statement.setLong(4, seed);
                statement.execute();
            }
        }
    }


    public static void onPlayerJoin(Connection con, EventPlayerSession event, Object2IntMap<String> serverIdCache, Object2IntMap<String> playerIdCache) throws SQLException {
        final int serverId = getServerId(con, event.server, serverIdCache);
        final int playerId = getOrInsertPlayerId(con, event.uuid, playerIdCache);

        try (PreparedStatement statement = con.prepareStatement("INSERT INTO player_sessions(player_id, server_id, enter, exit) VALUES (?, ?, ?, null)")) {
            statement.setInt(1, playerId);
            statement.setShort(2, (short) serverId);
            statement.setLong(3, event.time);
            statement.execute();
        }
    }


    public static void onPlayerLeave(Connection con, EventPlayerSession event, Object2IntMap<String> serverIdCache, Object2IntMap<String> playerIdCache) throws SQLException {
        final int serverId = getServerId(con, event.server, serverIdCache);
        final int playerId = getOrInsertPlayerId(con, event.uuid, playerIdCache);

        try (PreparedStatement statement = con.prepareStatement("UPDATE online_players SET exit = ? WHERE player_id = ? AND server_id = ?")) {
            statement.setLong(1, event.time);
            statement.setInt(2, playerId);
            statement.setShort(3, (short) serverId);
            statement.execute();
        }
    }


    public static void onStop(Connection con, EventStop event, Object2IntMap<String> serverIdCache) throws SQLException {
        for (String server : event.servers) {
            final int serverId = getServerId(con, server, serverIdCache);

            try (PreparedStatement statement = con.prepareStatement("UPDATE online_players SET exit = ? WHERE server_id = ?")) {
                statement.setLong(1, event.timestamp);
                statement.setInt(2, serverId);
                statement.execute();
            }
        }
    }

    public static void onStart(Connection con, EventStart event, long rowId, Object2IntMap<String> serverIdCache) throws SQLException {
        for (String server : event.servers) {
            final int serverId = getServerId(con, server, serverIdCache);

            try (PreparedStatement statement = con.prepareStatement("UPDATE online_players SET exit = (SELECT COALESCE((event->'timestamp')::bigint, ?) FROM events WHERE ((event->>'type' = 'seed' AND event->>'server' = ?) OR (event->>'type' = 'heartbeat' AND event->'servers' ?? ?)) AND id < ? ORDER BY id DESC LIMIT 1) WHERE server_id = ?")) {
                statement.setLong(1, event.timestamp);
                statement.setString(2, server);
                statement.setString(3, server);
                statement.setLong(4, rowId);
                statement.setInt(5, serverId);
                statement.execute();
            }
        }
    }

    // this is probably not useful
    public static void onPluginStart(Connection con, PluginStartupEvent event, long rowId) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement("UPDATE online_players SET exit = (SELECT COALESCE((event->'timestamp')::bigint, ?) FROM events WHERE (event->>'type' = 'seed' OR event->>'type' = 'heartbeat') AND id < ? ORDER BY id DESC LIMIT 1)")) {
            statement.setLong(1, event.time);
            statement.setLong(2, rowId);
            statement.execute();
        }
    }

    public static int copyNewSeedEvents(Connection con, long maxSeedID) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement("INSERT INTO rng_seeds_not_yet_processed (server_id, dimension, received_at, rng_seed) SELECT (SELECT id FROM servers WHERE name = event->>'server'), (event->'dimension')::smallint, (event->'timestamp')::bigint, jsonb_array_elements_text(event->'seeds')::bigint FROM events WHERE id > (SELECT id FROM seed_copy_progress) AND id <= ? AND event->>'type' = 'seed' ORDER BY id LIMIT 100000 ON CONFLICT DO NOTHING")) {
            statement.setLong(1, maxSeedID);
            return statement.executeUpdate();
        }
    }

    public static void updateSeedCopyProgress(Connection con, long maxSeedID) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement("UPDATE seed_copy_progress SET id = ?")) {
            statement.setLong(1, maxSeedID);
            statement.execute();
        }
    }

    public static OptionalLong maxSeedID(Connection con) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement("SELECT MAX(id) FROM events WHERE event->>'type' = 'seed'");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return OptionalLong.of(rs.getLong(1));
            } else {
                return OptionalLong.empty();
            }
        }
    }

    public static UnprocessedSeeds getAndDeleteSeedsToReverse(Connection con) throws SQLException {
        final int LIMIT = 100000;
        LongArrayList seeds = new LongArrayList(LIMIT);
        LongArrayList timestamps = new LongArrayList(LIMIT);
        try (PreparedStatement statement = con.prepareStatement("DELETE FROM rng_seeds_not_yet_processed WHERE id IN (SELECT id FROM rng_seeds_not_yet_processed WHERE dimension = 0 AND server_id = 1 ORDER BY id LIMIT ?) RETURNING rng_seed, received_at")) {
            statement.setInt(1, LIMIT);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    seeds.add(rs.getLong(1));
                    timestamps.add(rs.getLong(2));
                }
                return new UnprocessedSeeds(0, -4172144997902289642L, timestamps, seeds);
            }
        }
    }

    public static void saveProcessedSeeds(Connection con, List<ProcessedSeed> seeds, LongArrayList timestamps, int dimension, int serverId) throws SQLException {
        if (seeds.size() != timestamps.size()) throw new IllegalStateException();
        try (PreparedStatement statement = con.prepareStatement("INSERT INTO rng_seeds(server_id, dimension, received_at, rng_seed, steps_back, structure_x, structure_z) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < seeds.size(); i++) {
                statement.setShort(1, (short) serverId);
                statement.setShort(2, (short) dimension);
                statement.setLong(3, timestamps.getLong(i));
                ProcessedSeed processed = seeds.get(i);
                statement.setLong(4, processed.rng_seed);
                statement.setInt(5, processed.steps);
                statement.setInt(6, processed.x);
                statement.setInt(7, processed.z);

                statement.addBatch();
            }
            statement.executeBatch();
        }
        for (short timescale = 0; timescale < TIMESCALES.length; timescale++) {
            try (PreparedStatement statement = con.prepareStatement("INSERT INTO time_aggregated_seeds(server_id, dimension, timescale, time_idx, x, z, quantity) VALUES (?, ?, ?, ?, ?, ?, 1) ON CONFLICT (server_id, dimension, timescale, time_idx, x, z) DO UPDATE SET quantity = time_aggregated_seeds.quantity + 1")) {
                for (int i = 0; i < seeds.size(); i++) {
                    statement.setShort(1, (short) serverId);
                    statement.setShort(2, (short) dimension);
                    statement.setShort(3, timescale);
                    statement.setInt(4, (int) (timestamps.getLong(i) / TIMESCALES[timescale]));
                    ProcessedSeed processed = seeds.get(i);
                    statement.setInt(5, processed.x);
                    statement.setInt(6, processed.z);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private static final long[] TIMESCALES = {Long.MAX_VALUE, TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(1)};
}