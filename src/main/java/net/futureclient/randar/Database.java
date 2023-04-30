package net.futureclient.randar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    public static List<Event> queryNewEvents(Connection con) throws SQLException {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement statement = con.prepareStatement("SELECT id,event FROM events WHERE id > (SELECT id FROM event_progress)")) {
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
}
