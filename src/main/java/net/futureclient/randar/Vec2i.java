package net.futureclient.randar;

import java.util.Objects;

public class Vec2i {
    public int x;
    public int z;

    public Vec2i(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public Vec2i add(Vec2i vec) {
        return this.add(vec.x, vec.z);
    }

    public Vec2i add(int x, int y) {
        this.x += x;
        this.z += y;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vec2i vec2i = (Vec2i) o;
        return x == vec2i.x && z == vec2i.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return String.format("Vec2i(%d,%d)", this.x, this.z);
    }
}
