package net.voidflame.duels;

import net.voidflame.core.api.ServiceRegistry;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;

final class CoreServices {
    private final ServiceRegistry registry;

    private CoreServices(ServiceRegistry registry) { this.registry = registry; }

    static CoreServices connect(ServicesManager servicesManager) {
        var registration = servicesManager.getRegistration(ServiceRegistry.class);
        if (registration == null || registration.getProvider() == null) return null;
        return new CoreServices(registration.getProvider());
    }

    <T> void register(Class<T> type, T service) {
        registry.register(type, Objects.requireNonNull(service));
    }

    <T> void unregister(Class<T> type) {
        registry.unregister(type);
    }

    <T> T get(Class<T> type) {
        return registry.get(type);
    }
}
