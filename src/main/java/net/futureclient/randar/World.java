package net.futureclient.randar;

import java.util.Objects;

public final class World {
    public final String server;
    public final short serverId;
    public final short dimension;
    public final long worldSeed;


    public World(String server, short serverId, short dimension, long worldSeed) {
        this.server = server;
        this.serverId = serverId;
        this.dimension = dimension;
        this.worldSeed = worldSeed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        World world = (World) o;
        return serverId == world.serverId && dimension == world.dimension && worldSeed == world.worldSeed && server.equals(world.server);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, serverId, dimension, worldSeed);
    }
}