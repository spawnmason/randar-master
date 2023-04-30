package net.futureclient.randar;

import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    private static void processEvents() throws SQLException {
        try (Connection con = Database.POOL.getConnection()) {
            con.setAutoCommit(false);
            var events = Database.queryNewEvents(con);
            for (var event : events) {
                // ...
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