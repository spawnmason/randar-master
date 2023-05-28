package net.futureclient.randar;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

public class Associator {
    // will do soon :)
    private static final long INTERVAL = TimeUnit.HOURS.toMillis(1);
    private static final long UNTIL = TimeUnit.HOURS.toMillis(24);

    private static final long UNOCCUPIED_DURATION = TimeUnit.MINUTES.toMillis(10); // an area must have had no hits for 10 minutes beforehand for us to count it as a potential login
    private static final long INTERVAL_EXPANSION = UNOCCUPIED_DURATION + TimeUnit.MINUTES.toMillis(1);


    // explain analyze with tmp as materialized (select count(*) from player_sessions where range && int8range(1685171344271-100000, 1685171344271+100000)) select * from tmp;

    // okay so first we grab the current progress through the player_sessions table, like the nocom associator used to do
    // then we step forwards by an hour at a time
    // for each hour we use something like the query above to get all sessions that overlapped with that hour (efficiently using GiST index)
    // then we find just the player join events that were within the hour (can be part of the same query just index bs)
    // then, discard any player join events that were within ±10 seconds of at least ten others (avoid server restarts)
    // then for each join, find all overworld rng hits within 1 second before to 5 seconds after
    // for each of those candidate locations, discard any that additionally had hits at the same (x,z) within the last 15 minutes (or, however long since this same player last left, if that's sooner than 15 minutes before)
    // then, divvy up one point of association between that player and all possible places it could have been

    public static class HitHistoryAtLocation {
        public final int x;
        public final int z;
        public final LongList timestamps;

        public HitHistoryAtLocation(int x, int z, long rangeStart, long rangeEnd, Connection conn) throws SQLException {
            this.x = x;
            this.z = z;
            this.timestamps = new LongArrayList();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT received_at FROM rng_seeds WHERE server_id = 1 AND dimension = 0 AND structure_x = ? AND structure_z = ? AND received_at >= ? AND received_at <= ? ORDER BY received_at ASC")) {
                stmt.setInt(1, x);
                stmt.setInt(2, z);
                stmt.setLong(3, rangeStart);
                stmt.setLong(4, rangeEnd);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        timestamps.add(rs.getLong("received_at"));
                    }
                }
            }
        }
    }

    public static boolean doAssociate() {
        try (Connection connection = Database.POOL.getConnection()) {
            connection.setAutoCommit(false);
            long prevFence = 0;
            boolean needsInsert = false;
            try (PreparedStatement stmt = connection.prepareStatement("SELECT max_timestamp_processed FROM associator_progress");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    prevFence = rs.getLong("max_timestamp_processed");
                } else {
                    needsInsert = true;
                }
            }
            if (prevFence == 0) {
                // calculate it for real
                // this query is sorta slow (50ms) but it'll only run once, ever, so I don't care
                try (PreparedStatement stmt = connection.prepareStatement("SELECT MIN(enter) AS first_timestamp FROM player_sessions");
                     ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    prevFence = rs.getLong("first_timestamp");
                }
            }
            long fence = prevFence + INTERVAL;
            if (System.currentTimeMillis() - fence < UNTIL) {
                System.out.println("We are associated up till less than 1 day ago so, no");
                return false;
            }
            System.out.println(fence + " " + prevFence + " " + (fence - prevFence));

            List<Database.PlayerSession> relevantSessions = Database.getAllSessionsOverlapping(connection, prevFence - INTERVAL_EXPANSION, fence + INTERVAL_EXPANSION);
            System.out.println("Got " + relevantSessions.size() + " sessions");

            List<Database.PlayerSession> candidateJoins = new ArrayList<>();
            int rangeSkip = 0;
            int nearbySkip = 0;
            for (int i = 0; i < relevantSessions.size(); i++) {
                Database.PlayerSession session = relevantSessions.get(i);
                if (session.join < prevFence || session.leave >= fence) {
                    rangeSkip++;
                    continue;
                }
                int countJoinsWithinPreviousTenSeconds = 0;
                while (i - countJoinsWithinPreviousTenSeconds - 1 >= 0 &&
                        relevantSessions.get(i - countJoinsWithinPreviousTenSeconds - 1).join >= session.join - 10000) {
                    countJoinsWithinPreviousTenSeconds++;
                }
                int countJoinsWithinNextTenSeconds = 0;
                while (i + countJoinsWithinNextTenSeconds + 1 < relevantSessions.size() &&
                        relevantSessions.get(i + countJoinsWithinNextTenSeconds + 1).join <= session.join + 10000) {
                    countJoinsWithinNextTenSeconds++;
                }
                if (countJoinsWithinNextTenSeconds + countJoinsWithinPreviousTenSeconds >= 10) {
                    // maybe server restart
                    nearbySkip++;
                    continue;
                }
                candidateJoins.add(session);
            }
            System.out.println("Got " + candidateJoins.size() + " candidate joins range skip: " + rangeSkip + " nearby skip: " + nearbySkip);

            Long2ObjectOpenHashMap<HitHistoryAtLocation> cache = new Long2ObjectOpenHashMap<>();
            for (Database.PlayerSession event : candidateJoins) {
                // decide the cutoff for previous activity
                OptionalLong lastTimeThisPlayerLeftBeforeThisJoinEvent = relevantSessions
                        .stream()
                        .filter(other -> other.playerId == event.playerId && other.leave < event.join)
                        .mapToLong(other -> other.leave)
                        .map(leave -> leave + TimeUnit.SECONDS.toMillis(10)) // just in case theres server lag - allow 10 seconds of chunk loads after the leave
                        .max();
                long cutoff = lastTimeThisPlayerLeftBeforeThisJoinEvent.orElse(event.join - UNOCCUPIED_DURATION); // otherwise it's the time they joined minutes ten minutes

                // tldr: if there is any activity at a given location between cutoff and the join event, discard that location as a candidate

                // find all rng hits within 1 second before to 5 seconds after
                long rangeStart = event.join - 1000;
                long rangeEnd = event.join + 5000;
                LongList candidateLocationsForThisJoin = new LongArrayList();
                for (long hit : getRngHits(connection, rangeStart, rangeEnd)) {
                    int x = XFromLong(hit);
                    int z = ZFromLong(hit);
                    if (!cache.containsKey(hit)) {
                        cache.put(hit, new HitHistoryAtLocation(x, z, prevFence, fence, connection));
                    }
                    HitHistoryAtLocation history = cache.get(hit);
                    if (history.timestamps.stream().anyMatch(timestamp -> timestamp >= cutoff && timestamp < rangeStart)) {
                        // this hit is not a candidate
                        continue;
                    }
                    candidateLocationsForThisJoin.add(hit);
                }
                if (!candidateLocationsForThisJoin.isEmpty()) {
                    insertAssociations(connection, event.playerId, event.join, candidateLocationsForThisJoin);
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(needsInsert ?
                    "INSERT INTO associator_progress (max_timestamp_processed) VALUES (?)"
                    : "UPDATE associator_progress SET max_timestamp_processed = ?")) {
                stmt.setLong(1, fence);
                stmt.execute();
            }
            connection.commit();
            return true;
        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

    public static void insertAssociations(Connection conn, int playerId, long joinedAt, LongList coords) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO associations (server_id, dimension, x, z, player_id, denominator, created_at) VALUES (1, 0, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < coords.size(); i++) {
                stmt.setInt(1, XFromLong(coords.getLong(i)));
                stmt.setInt(2, ZFromLong(coords.getLong(i)));
                stmt.setInt(3, playerId);
                stmt.setInt(4, coords.size());
                stmt.setLong(5, joinedAt);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public static LongList getRngHits(Connection connection, long rangeStart, long rangeEnd) throws SQLException {
        LongList hits = new LongArrayList();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT structure_x, structure_z FROM rng_seeds WHERE server_id = 1 AND dimension = 0 AND received_at >= ? AND received_at <= ?")) {
            stmt.setLong(1, rangeStart);
            stmt.setLong(2, rangeEnd);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    hits.add(chunkPosToLong(rs.getInt("structure_x"), rs.getInt("structure_z")));
                }
            }
        }
        return hits;
    }

    public static long chunkPosToLong(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    public static int XFromLong(long pos) {
        return (int) (pos);
    }

    public static int ZFromLong(long pos) {
        return (int) (pos >> 32);
    }
}
