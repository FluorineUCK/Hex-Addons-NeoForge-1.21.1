package org.eu.net.pool.hexic.hexcompat;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional Hexical/Hexportation compatibility kept behind reflection so Hexic
 * can still load when either addon is absent.
 *
 * <p>This restores both behaviours from the 2.1.0 Fabric implementation:
 * targeting another player resolves their Hexical wristpocket, and a
 * Hexportation conduit resolves to the source/sink sided inventories.</p>
 */
public final class HexicalHopperCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hexic/HexicalHopperCompat");

    private static final String REGISTRY_CLASS =
        "miyucomics.hexical.features.hopper.HopperEndpointRegistry";
    private static final String RESOLVER_CLASS =
        "miyucomics.hexical.features.hopper.HopperEndpointResolver";
    private static final String SOURCE_CLASS =
        "miyucomics.hexical.features.hopper.HopperSource";
    private static final String DESTINATION_CLASS =
        "miyucomics.hexical.features.hopper.HopperDestination";
    private static final String SIDED_ENDPOINT_CLASS =
        "miyucomics.hexical.features.hopper.targets.SidedInventoryEndpoint";
    private static final String WRISTPOCKET_ENDPOINT_CLASS =
        "miyucomics.hexical.features.hopper.targets.WristpocketEndpoint";

    private static final Set<String> CONDUIT_IOTA_CLASSES = Set.of(
        "dev.kineticcat.hexportation.fabric.api.casting.iota.ConduitIota",
        "dev.kineticcat.hexportation.api.casting.iota.ConduitIota",
        "dev.kineticcat.hexportation.neoforge.api.casting.iota.ConduitIota"
    );

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static volatile Object registeredResolver;

    private HexicalHopperCompat() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> registryClass = Class.forName(REGISTRY_CLASS, true, loader);
            Class<?> resolverClass = Class.forName(RESOLVER_CLASS, true, loader);
            Object registry = registryClass.getField("INSTANCE").get(null);

            Object resolver = Proxy.newProxyInstance(
                resolverClass.getClassLoader(),
                new Class<?>[]{resolverClass},
                HexicalHopperCompat::invokeResolver
            );
            Method register = Arrays.stream(registryClass.getMethods())
                .filter(method -> method.getName().equals("register"))
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0].isAssignableFrom(resolverClass))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(
                    REGISTRY_CLASS + ".register(" + RESOLVER_CLASS + ")"
                ));

            register.invoke(registry, resolver);
            registeredResolver = resolver;
            LOGGER.info("Registered Hexic Hexical hopper resolver");
        } catch (Throwable error) {
            REGISTERED.set(false);
            registeredResolver = null;
            throw new IllegalStateException("Failed to register Hexical hopper compatibility", error);
        }
    }

    /**
     * Probe seam which runs the same resolver that is registered with Hexical.
     */
    public static Object resolveForProbe(Iota iota, CastingEnvironment env, Integer slot) {
        try {
            return resolve(iota, env, slot);
        } catch (RuntimeException error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Hexical hopper resolver probe failed", error);
        }
    }

    /**
     * Probe seam which traverses Hexical's public registry, proving that the
     * resolver was not only constructed but was actually installed.
     */
    public static Object resolveThroughRegistryForProbe(
        Iota iota,
        CastingEnvironment env,
        Integer slot
    ) {
        try {
            Class<?> registryClass = Class.forName(
                REGISTRY_CLASS,
                true,
                Thread.currentThread().getContextClassLoader()
            );
            Object registry = registryClass.getField("INSTANCE").get(null);
            Method resolve = Arrays.stream(registryClass.getMethods())
                .filter(method -> method.getName().equals("resolve"))
                .filter(method -> method.getParameterCount() == 3)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(
                    REGISTRY_CLASS + ".resolve(Iota, CastingEnvironment, Integer)"
                ));
            return resolve.invoke(registry, iota, env, slot);
        } catch (RuntimeException error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Hexical hopper registry probe failed", error);
        }
    }

    public static boolean isRegistered() {
        return registeredResolver != null;
    }

    private static Object invokeResolver(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        if (!method.getName().equals("resolve") || args == null || args.length != 3) {
            throw new UnsupportedOperationException("Unexpected Hexical resolver method: " + method);
        }
        return resolve((Iota) args[0], (CastingEnvironment) args[1], (Integer) args[2]);
    }

    private static Object resolve(Iota iota, CastingEnvironment env, Integer slot) throws Throwable {
        Object wristpocket = resolveWristpocket(iota, env, slot);
        if (wristpocket != null) {
            return wristpocket;
        }
        if (iota != null && CONDUIT_IOTA_CLASSES.contains(iota.getClass().getName())) {
            return resolveConduit(iota, env);
        }
        return null;
    }

    private static Object resolveWristpocket(Iota iota, CastingEnvironment env, Integer slot)
        throws Throwable {
        if (!(iota instanceof EntityIota entityIota) || slot == null || slot != -1) {
            return null;
        }

        Entity entity = entityIota.getEntity(env.getWorld());
        if (!(entity instanceof ServerPlayer player)) {
            return null;
        }

        Class<?> endpointClass = Class.forName(
            WRISTPOCKET_ENDPOINT_CLASS,
            true,
            Thread.currentThread().getContextClassLoader()
        );
        return construct(endpointClass, player);
    }

    private static Object resolveConduit(Iota conduitIota, CastingEnvironment env) throws Throwable {
        Object conduit = conduitIota.getClass().getMethod("getConduit").invoke(conduitIota);
        BlockPos sourcePos = (BlockPos) conduit.getClass().getMethod("source").invoke(conduit);
        Direction sourceDirection =
            (Direction) conduit.getClass().getMethod("sourceDir").invoke(conduit);
        BlockPos sinkPos = (BlockPos) conduit.getClass().getMethod("sink").invoke(conduit);

        Object source = null;
        Object destination = null;

        Object sourceBlockEntity = env.getWorld().getBlockEntity(sourcePos);
        if (sourceBlockEntity instanceof WorldlyContainer inventory) {
            source = newSidedEndpoint(inventory, sourceDirection);
        }

        Object sinkBlockEntity = env.getWorld().getBlockEntity(sinkPos);
        if (sinkBlockEntity instanceof WorldlyContainer inventory) {
            // Preserve Hexic 2.1.0 behaviour exactly: the sink endpoint also
            // uses sourceDir rather than sinkDir.
            destination = newSidedEndpoint(inventory, sourceDirection);
        }

        if (source == null && destination == null) {
            return null;
        }
        return combineEndpoints(source, destination);
    }

    private static Object newSidedEndpoint(WorldlyContainer inventory, Direction direction)
        throws Throwable {
        Class<?> endpointClass = Class.forName(
            SIDED_ENDPOINT_CLASS,
            true,
            Thread.currentThread().getContextClassLoader()
        );
        return construct(endpointClass, inventory, direction);
    }

    private static Object combineEndpoints(Object source, Object destination) throws Throwable {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> sourceClass = Class.forName(SOURCE_CLASS, true, loader);
        Class<?> destinationClass = Class.forName(DESTINATION_CLASS, true, loader);

        Class<?>[] interfaces;
        if (source != null && destination != null) {
            interfaces = new Class<?>[]{sourceClass, destinationClass};
        } else if (source != null) {
            interfaces = new Class<?>[]{sourceClass};
        } else {
            interfaces = new Class<?>[]{destinationClass};
        }

        Object finalSource = source;
        Object finalDestination = destination;
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }

            Object target = sourceClass.isAssignableFrom(method.getDeclaringClass())
                ? finalSource
                : finalDestination;
            if (target == null) {
                throw new UnsupportedOperationException(
                    "Endpoint does not implement " + method.getDeclaringClass().getName()
                );
            }
            return method.invoke(target, args);
        };
        return Proxy.newProxyInstance(sourceClass.getClassLoader(), interfaces, handler);
    }

    private static Object construct(Class<?> type, Object... args) throws Throwable {
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != args.length) {
                continue;
            }

            boolean matches = true;
            for (int i = 0; i < parameters.length; i++) {
                if (args[i] != null && !parameters[i].isAssignableFrom(args[i].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return constructor.newInstance(args);
            }
        }
        throw new NoSuchMethodException(
            type.getName() + " constructor for " +
                Arrays.stream(args).map(value -> value == null ? "null" : value.getClass().getName()).toList()
        );
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "HexicHexicalHopperResolver";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }
}
