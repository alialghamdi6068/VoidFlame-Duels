package net.voidflame.duels;

import net.voidflame.core.api.ArenaService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ArenaManager {
    private final VoidFlameDuelsPlugin plugin;
    private ArenaService provider;

    public ArenaManager(VoidFlameDuelsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        connect();
    }

    public synchronized void connect() {
        RegisteredServiceProvider<ArenaService> registration =
                Bukkit.getServicesManager().getRegistration(ArenaService.class);
        if (registration == null || registration.getProvider() == null) {
            provider = null;
            return;
        }
        provider = registration.getProvider();
    }

    public synchronized Arena acquire() {
        ensureConnected();
        if (provider == null) return null;
        Optional<ArenaService.ArenaHandle> result = provider.acquireHandle();
        if (result.isEmpty()) return null;
        ArenaService.ArenaHandle handle = result.get();
        if (handle.spawnA() == null || handle.spawnB() == null) return null;
        return new Arena(handle.name(), handle.spawnA(), handle.spawnB());
    }

    public synchronized CompletableFuture<Boolean> reset(Arena arena) {
        if (arena == null) return CompletableFuture.completedFuture(false);
        ensureConnected();
        if (provider == null) return CompletableFuture.completedFuture(false);
        return provider.reset(arena.name());
    }

    public int available() {
        ensureConnected();
        return provider == null ? 0 : (int) provider.availableCount();
    }

    public List<String> allNames() {
        ensureConnected();
        return provider == null ? List.of() : List.copyOf(provider.allNames());
    }

    private void ensureConnected() {
        if (provider == null) connect();
    }
}
