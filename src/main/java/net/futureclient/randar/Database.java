package net.futureclient.randar;

import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.*;
import java.util.Objects;
import java.util.Optional;

public class Database {
    private static final BasicDataSource POOL = connect();

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
}
