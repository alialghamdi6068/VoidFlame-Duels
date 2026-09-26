package net.voidflame.duels;

import org.bukkit.Location;

import java.util.Objects;

public final class Arena {
    private final String name;
    private final Location spawnA;
    private final Location spawnB;

    public Arena(String name, Location spawnA, Location spawnB) {
        this.name = Objects.requireNonNull(name);
        this.spawnA = Objects.requireNonNull(spawnA).clone();
        this.spawnB = Objects.requireNonNull(spawnB).clone();
    }

    public String name() { return name; }
    public Location spawnA() { return spawnA.clone(); }
    public Location spawnB() { return spawnB.clone(); }
}
