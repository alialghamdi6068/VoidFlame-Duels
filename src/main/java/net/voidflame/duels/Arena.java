package net.voidflame.duels;

import org.bukkit.Location;

public record Arena(String name, Location spawnA, Location spawnB) {
    public Arena {
        spawnA = spawnA.clone();
        spawnB = spawnB.clone();
    }
}
