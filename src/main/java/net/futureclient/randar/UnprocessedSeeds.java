package net.futureclient.randar;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public class UnprocessedSeeds {
    public final int dimension;
    public final long server_Seed;
    public final LongArrayList timestamps;
    public final LongArrayList seeds;


    public UnprocessedSeeds(int dimension, long server_Seed, LongArrayList timestamps, LongArrayList seeds) {
        this.dimension = dimension;
        this.server_Seed = server_Seed;
        this.timestamps = timestamps;
        this.seeds = seeds;
    }
}