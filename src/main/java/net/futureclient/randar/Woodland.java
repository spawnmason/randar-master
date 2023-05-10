package net.futureclient.randar;

public class Woodland {
    private static final long MASK = (1L << 48) - 1;
    public static final int WOODLAND_BOUNDS = 23440;

    public static long stepRng(long steps, long seed) {
        steps = steps & MASK;
        long resultMult = 1;
        long resultAdd = 0;
        long currentMult = 0x5DEECE66DL;
        long currentAdd = 0xB;
        while (steps > 0) {
            if (steps % 2 == 1) {
                resultMult = resultMult * currentMult;
                resultAdd = currentAdd + resultAdd * currentMult;
            }
            steps /= 2;
            currentAdd = currentAdd * currentMult + currentAdd;
            currentMult = currentMult * currentMult;
        }
        return (seed * resultMult + resultAdd) & MASK;
    }

    private static final long chunkZMultInv248 = 211541297333629L; // https://www.wolframalpha.com/input?i=132897987541%5E-1+mod+2%5E48

    public static long woodlandMansionSeed(int x, int z, long worldSeed) { // from World and WoodlandMansion
        return ((long) z * 341873128712L + (long) z * 132897987541L + worldSeed + (long) 10387319) & ((1L << 48) - 1); // rand drops highest 16 bits anyway
    }

    public static long reverseWoodlandZGivenX(long seed48Bits, int x, long worldSeed) {
        return ((seed48Bits - worldSeed - 10387319 - (long) x * 341873128712L) * chunkZMultInv248) << 16 >> 16;
    }
}