package net.futureclient.randar;

public class ProcessedSeed {
    public final long rng_seed;
    public final int steps;
    public final Vec2i pos;

    public ProcessedSeed(long rng_seed, int steps, int x, int z) {
        this(rng_seed, steps, new Vec2i(x,z));
    }

    public ProcessedSeed(long rng_seed, int steps, Vec2i pos) {
        this.rng_seed = rng_seed;
        this.steps = steps;
        this.pos = pos;
    }
}