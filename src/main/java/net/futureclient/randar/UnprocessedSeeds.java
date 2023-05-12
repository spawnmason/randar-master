package net.futureclient.randar;

import it.unimi.dsi.fastutil.longs.LongArrayList;

public class UnprocessedSeeds {
    public final LongArrayList timestamps;
    public final LongArrayList seeds;


    public UnprocessedSeeds(LongArrayList timestamps, LongArrayList seeds) {
        this.timestamps = timestamps;
        this.seeds = seeds;
    }
}