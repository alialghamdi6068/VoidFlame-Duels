package net.voidflame.duels;

import org.bukkit.Location;

import java.util.Objects;

public final class Arena {
    private final Object providerArena;
    private final String name;
    private final Location spawnA;
    private final Location spawnB;

    public Arena(Object providerArena, String name, Location spawnA, Location spawnB) {
        this.providerArena = Objects.requireNonNull(providerArena);
        this.name = Objects.requireNonNull(name);
        this.spawnA = Objects.requireNonNull(spawnA).clone();
        this.spawnB = Objects.requireNonNull(spawnB).clone();
    }

    public Object providerArena() { return providerArena; }
    public String name() { return name; }
    public Location spawnA() { return spawnA.clone(); }
    public Location spawnB() { return spawnB.clone(); }
}
