package net.voidflame.duels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ArenaManager {
    private static final String PROVIDER_CLASS = "net.voidflame.arenas.ArenaManager";

    private final VoidFlameDuelsPlugin plugin;
    private Object provider;
    private Method acquireAvailable;
    private Method release;

    public ArenaManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        connect();
    }

    public synchronized void connect() {
        try {
            Class<?> providerType = Bukkit.getServicesManager().getKnownServices().stream()
                    .filter(type -> type.getName().equals(PROVIDER_CLASS))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("VoidFlame-Arenas service type is unavailable."));
            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager().getRegistration(providerType);
            if (registration == null || registration.getProvider() == null) {
                throw new IllegalStateException("VoidFlame-Arenas service is not registered.");
            }
            provider = registration.getProvider();
            acquireAvailable = providerType.getMethod("acquireAvailable");
            release = providerType.getMethod("release", providerType.getDeclaredClasses().length == -1 ? Object.class : providerType.getDeclaredClasses()[0]);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to connect to VoidFlame-Arenas.", ex);
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
            release = provider.getClass().getMethod("release", arena.getClass());
            return new Arena(
                    arena,
                    String.valueOf(name.invoke(arena)),
                    (Location) spawnA.invoke(arena),
                    (Location) spawnB.invoke(arena)
            );
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().severe("Failed to acquire arena: " + ex.getMessage());
            return null;
        }
    }

    public synchronized void release(Arena arena) {
        if (arena == null) return;
        ensureConnected();
        try {
            release.invoke(provider, arena.providerArena());
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().severe("Failed to release arena '" + arena.name() + "': " + ex.getMessage());
        }
    }

    public int available() {
        ensureConnected();
        try {
            Method method = provider.getClass().getMethod("availableCount");
            return ((Number) method.invoke(provider)).intValue();
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    public List<String> allNames() {
        ensureConnected();
        try {
            Method all = provider.getClass().getMethod("all");
            Object value = all.invoke(provider);
            if (!(value instanceof Iterable<?> iterable)) return List.of();
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .map(Object::toString)
                    .toList();
        } catch (ReflectiveOperationException ex) {
            return List.of();
        }
    }

    private void ensureConnected() {
        if (provider == null) connect();
    }
}
