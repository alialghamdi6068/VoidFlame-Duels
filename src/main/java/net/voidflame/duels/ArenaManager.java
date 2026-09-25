package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public final class ArenaManager {
    private static final String PROVIDER_CLASS = "net.voidflame.arenas.ArenaManager";

    private final VoidFlameDuelsPlugin plugin;
    private Object provider;
    private Method acquireAvailable;
    private Method availableCount;
    private Method all;

    public ArenaManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        connect();
    }

    public synchronized void connect() {
        try {
            Class<?> providerType = Class.forName(PROVIDER_CLASS);
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration(providerType);
            if (registration == null || registration.getProvider() == null) {
                throw new IllegalStateException("VoidFlame-Arenas service is not registered.");
            }
            provider = registration.getProvider();
            acquireAvailable = providerType.getMethod("acquireAvailable");
            availableCount = providerType.getMethod("availableCount");
            all = providerType.getMethod("all");
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            throw new IllegalStateException("Unable to connect to VoidFlame-Arenas service.", ex);
        }
    }

    public synchronized Arena acquire() {
        ensureConnected();
        try {
            Object result = acquireAvailable.invoke(provider);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;

            Object arena = optional.get();
            Method name = arena.getClass().getMethod("name");
            Method spawnA = arena.getClass().getMethod("spawnA");
            Method spawnB = arena.getClass().getMethod("spawnB");

            Location first = (Location) spawnA.invoke(arena);
            Location second = (Location) spawnB.invoke(arena);
            if (first == null || second == null) {
                releaseProviderArena(arena);
                return null;
            }

            return new Arena(
                    arena,
                    String.valueOf(name.invoke(arena)),
                    first,
                    second
            );
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().severe("Failed to acquire arena: " + ex.getMessage());
            return null;
        }
    }

    public synchronized void release(Arena arena) {
        if (arena == null) return;
        ensureConnected();
        releaseProviderArena(arena.providerArena());
    }

    private void releaseProviderArena(Object arena) {
        try {
            Method method = provider.getClass().getMethod("release", arena.getClass());
            method.invoke(provider, arena);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().severe("Failed to release arena: " + ex.getMessage());
        }
    }

    public int available() {
        ensureConnected();
        try {
            return ((Number) availableCount.invoke(provider)).intValue();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not read available arena count: " + ex.getMessage());
            return 0;
        }
    }

    public List<String> allNames() {
        ensureConnected();
        try {
            Object value = all.invoke(provider);
            if (!(value instanceof Iterable<?> iterable)) return List.of();
            return StreamSupport.stream(iterable.spliterator(), false)
                    .map(this::arenaName)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not read arena list: " + ex.getMessage());
            return List.of();
        }
    }

    private String arenaName(Object arena) {
        try {
            return String.valueOf(arena.getClass().getMethod("name").invoke(arena));
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private void ensureConnected() {
        if (provider == null) connect();
    }
}
