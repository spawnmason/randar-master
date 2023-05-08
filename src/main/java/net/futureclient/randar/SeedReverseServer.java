package net.futureclient.randar;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SeedReverseServer {
    private final ServerSocket server;
    private final Thread listenThread;
    private final Executor socketIOExecutor;

    public SeedReverseServer() throws Exception {
        this.server = new ServerSocket(3459, 1, InetAddress.getByName("192.168.69.1"));
        this.socketIOExecutor = Executors.newSingleThreadExecutor();
        this.listenThread = new Thread(this::listen);
        this.listenThread.start();
        System.out.println("nyanserver listening");
    }

    private void listen() {
        try {
            while (true) {
                Socket s = server.accept();
                socketIOExecutor.execute(() -> io(s));
            }
        } catch (IOException ex) {
            // when shutdown is called, .accept will fail
            System.out.println("NyanServer shutdown");
            ex.printStackTrace();
        }
    }

    private void io(Socket s) {
        try (Connection con = Database.POOL.getConnection()) {
            con.setAutoCommit(false);
            System.out.println("got connection to nyanserver");
            s.setSoTimeout(10000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            String secretToken = in.readUTF();
            if (!secretToken.equals("def7fa9dd0c96bc1dcd2913d19120b7c89c026407fd67053774d316bd90b3459815479eef495f7f93f8f845c544515b77360b60e1214ed786820cb4d94e6abcd v1")) {
                System.out.println("wrong token");
                return;
            }
            UnprocessedSeeds result = Database.getAndDeleteSeedsToReverse(con);
            // TODO: THIS IS NEW, ADD TO SEED REVERSER
            out.writeByte(result.dimension);
            out.writeLong(result.server_Seed);
            LongArrayList seeds = result.seeds;
            System.out.println("nyanserver sending " + seeds.size() + " seeds");
            out.writeInt(seeds.size());
            for (long seed : seeds) {
                out.writeLong(seed);
            }
            // some time passes...
            int numProcessed = in.readInt();
            System.out.println("nyanserver got back " + numProcessed + " seeds");
            if (numProcessed != seeds.size()) {
                System.out.println("Expected " + seeds.size() + " seeds back but got " + numProcessed);
                return;
            }
            List<ProcessedSeed> processed = new ArrayList<>(numProcessed);
            for (int i = 0; i < numProcessed; i++) {
                long seed = in.readLong();
                if (seeds.getLong(i) != seed) {
                    System.out.println("naughty");
                    return;
                }
                int steps = in.readInt();
                short x = in.readShort();
                short z = in.readShort();
                processed.add(new ProcessedSeed(seed, steps, x, z));
            }
            System.out.println("received all processed seeds");
            // dim 0, 2b2t server id
            Database.saveProcessedSeeds(con, processed, result.timestamps, 0, 1);
            System.out.println("saved all processed seeds to db");
            con.commit();
            System.out.println("All changes are committed");
        } catch (SQLException ex) {
            System.out.println("SQL Error");
            ex.printStackTrace();
        } catch (IOException ex) {
            System.out.println("IO Failed");
            ex.printStackTrace();
        } finally {
            try {
                s.close();
            } catch (Throwable th) {}
        }
    }


    public void shutdown() {
        try {
            server.close();
        } catch (IOException ex) {
            System.out.println("Failed to stop executor");
            ex.printStackTrace();
        }
    }
}