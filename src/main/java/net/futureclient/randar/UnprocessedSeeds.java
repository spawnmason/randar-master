package net.futureclient.randar;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public class UnprocessedSeeds {
    public final long minId;
    public final long maxId;
    public final int dimension;
    public final LongArrayList seeds;


    public UnprocessedSeeds(long minId, long maxId, int dimension, LongArrayList seeds) {
        this.minId = minId;
        this.maxId = maxId;
        this.dimension = dimension;
        this.seeds = seeds;
    }
}