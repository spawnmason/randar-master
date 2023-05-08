package net.futureclient.randar;

public class ProcessedSeed {
    public final long rng_seed;
    public final int steps;
    public final int x;
    public final int z;

    public ProcessedSeed(long rng_seed, int steps, int x, int z) {
        this.rng_seed = rng_seed;
        this.steps = steps;
        this.x = x;
        this.z = z;
    }
}