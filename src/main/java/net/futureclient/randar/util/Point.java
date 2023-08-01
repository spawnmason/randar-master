package net.futureclient.randar.util;


import java.util.Objects;

public class Point {
    public final String title;
    public final Vec2i pos;

    public Point(String title, int x, int z) {
        this(title, new Vec2i(x, z));
    }

    public Point(String title, Vec2i pos) {
        this.title = title;
        this.pos = pos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return Objects.equals(title, point.title) && Objects.equals(pos, point.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, pos);
    }
}
