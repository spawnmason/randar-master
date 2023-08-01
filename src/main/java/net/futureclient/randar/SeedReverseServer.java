package net.futureclient.randar;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.futureclient.randar.util.DiscordWebhook;
import net.futureclient.randar.util.Point;
import net.futureclient.randar.util.Vec2i;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static net.futureclient.randar.Woodland.*;

public class SeedReverseServer {
    private final ServerSocket server;
    private final Thread listenThread;
    private final Executor socketIOExecutor;
    private DiscordWebhook webhook;

    public SeedReverseServer() throws IOException {
        this.server = new ServerSocket(3460, 1, InetAddress.getByName("192.168.69.1"));
        this.socketIOExecutor = Executors.newSingleThreadExecutor();
        this.listenThread = new Thread(this::listen);
        System.out.println("nyanserver listening");

        try {
            this.webhook = new DiscordWebhook(new String(Files.readAllBytes(Path.of("./discord.txt"))));
            this.webhook.setContent("hi uwu");
            this.webhook.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        this.listenThread.start();
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
            if (!secretToken.equals("def7fa9dd0c96bc1dcd2913d19120b7c89c026407fd67053774d316bd90b3459815479eef495f7f93f8f845c544515b77360b60e1214ed786820cb4d94e6abcd v3")) {
                System.out.println("wrong token");
                return;
            }
            Map<World, UnprocessedSeeds> results = Database.getAndDeleteSeedsToReverse(con);
            out.writeByte(results.size());
            for (var entry : results.entrySet()) {
                final World world = entry.getKey();
                final UnprocessedSeeds result = entry.getValue();
                out.writeByte(world.dimension);
                out.writeLong(world.worldSeed);
                LongArrayList seeds = result.seeds;
                System.out.println("nyanserver sending " + seeds.size() + " seeds");
                out.writeInt(seeds.size());
                if (seeds.isEmpty())
                    continue; // nothing needs to be done if there are no seeds (important for the client to match this behavior)
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
                if (!verifySeeds(processed, world.worldSeed)) {
                    return;
                }
                Database.saveProcessedSeeds(con, processed, result.timestamps, world.dimension, world.serverId);
                System.out.println("saved all processed seeds to db");

                if (webhook != null) {
                    List<Point> trackedPoints = Database.getTrackedPoints(con);
                    Set<Vec2i> notifiedPositions = new HashSet<>();
                    for (ProcessedSeed seed : processed) {
                        for (Point point : trackedPoints) {
                            if (point.pos.equals(seed.pos) && !notifiedPositions.contains(point.pos)) {
                                DiscordWebhook.EmbedObject embeds = new DiscordWebhook.EmbedObject();
                                embeds.setTitle("Player detected");
                                embeds.setDescription(
                                        "Player detected at " + point.title + "!\n Position: " +
                                                (point.pos.x * 1280 + 512) + ", " + (point.pos.z * 1280 + 512));
                                embeds.setColor(Color.RED);

                                webhook.addEmbed(embeds);
                                webhook.execute();
                                notifiedPositions.add(point.pos);
                            }
                        }
                    }
                }
            }

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
            } catch (Throwable th) {
            }
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

    private static boolean verifySeeds(List<ProcessedSeed> processed, long worldSeed) {
        for (ProcessedSeed seed : processed) {
            if (Woodland.stepRng(seed.steps, Woodland.woodlandMansionSeed(seed.pos.x, seed.pos.z, worldSeed) ^ 0x5DEECE66DL) != seed.rng_seed || seed.pos.x < -WOODLAND_BOUNDS || seed.pos.x > WOODLAND_BOUNDS || seed.pos.z < -WOODLAND_BOUNDS || seed.pos.z > WOODLAND_BOUNDS || seed.steps < 0 || seed.steps > 2750000) {
                System.out.println("Bad RNG data! " + seed.rng_seed + " " + seed.steps + " " + seed.pos.x + " " + seed.pos.z);
                return false;
            }
        }
        return true;
    }
}