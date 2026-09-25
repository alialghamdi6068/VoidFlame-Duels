package net.voidflame.duels;

import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Small runtime bridge to VoidFlame-Core.
 * The Duels artifact stays independently buildable while still registering
 * its public services with Core when Core is installed.
 */
final class CoreServices {
    private static final String REGISTRY_CLASS = "net.voidflame.core.api.ServiceRegistry";

    private final Object registry;
    private final Method register;
    private final Method unregister;

    private CoreServices(Object registry, Method register, Method unregister) {
        this.registry = registry;
        this.register = register;
        this.unregister = unregister;
    }

    static CoreServices connect(ServicesManager servicesManager) {
        try {
            Class<?> type = Class.forName(REGISTRY_CLASS);
            Object registry = servicesManager.load(type);
            if (registry == null) return null;
            Method register = type.getMethod("register", Class.class, Object.class);
            Method unregister = type.getMethod("unregister", Class.class);
            return new CoreServices(registry, register, unregister);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    void register(Class<?> type, Object service) {
        try {
            register.invoke(registry, type, Objects.requireNonNull(service));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not register VoidFlame-Duels service: " + type.getName(), e);
        }
    }

    void unregister(Class<?> type) {
        try {
            unregister.invoke(registry, type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not unregister VoidFlame-Duels service: " + type.getName(), e);
        }
    }
}
