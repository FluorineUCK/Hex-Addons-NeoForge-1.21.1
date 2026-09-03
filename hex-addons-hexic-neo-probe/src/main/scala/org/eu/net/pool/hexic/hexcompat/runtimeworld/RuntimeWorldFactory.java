package org.eu.net.pool.hexic.hexcompat.runtimeworld;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.eu.net.pool.hexic.mixin.MinecraftServerRuntimeWorldAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal NeoForge 1.21.1 runtime-world implementation used by Hexic.
 *
 * <p>Every demiplane is a distinct {@link ServerLevel} with its own dimension
 * key and save directory. The static {@code hexic:cell} level is used only as
 * the data-driven dimension-type/chunk-generator template.</p>
 */
public final class RuntimeWorldFactory {
    private static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.fromNamespaceAndPath("hexic", "cell");

    private static final ChunkProgressListener NO_PROGRESS = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos chunkPos) {
        }

        @Override
        public void onStatusChange(ChunkPos chunkPos, ChunkStatus chunkStatus) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    private RuntimeWorldFactory() {
    }

    public static ServerLevel open(MinecraftServer server, ResourceLocation id) {
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, id);
        ServerLevel existing = server.getLevel(worldKey);
        if (existing != null) {
            return existing;
        }

        Registry<LevelStem> stems =
                server.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, id);
        LevelStem stem = stems.get(stemKey);
        if (stem == null) {
            ResourceKey<LevelStem> templateKey =
                    ResourceKey.create(Registries.LEVEL_STEM, TEMPLATE_ID);
            LevelStem template = stems.get(templateKey);
            if (template == null) {
                throw new IllegalStateException("Missing Hexic runtime-world template " + TEMPLATE_ID);
            }
            if (!(stems instanceof MappedRegistry<LevelStem> mapped)) {
                throw new IllegalStateException(
                        "Level-stem registry is not mutable: " + stems.getClass().getName());
            }

            RegistrationInfo registrationInfo =
                    stems.registrationInfo(templateKey).orElse(RegistrationInfo.BUILT_IN);
            mapped.unfreeze();
            try {
                stem = new LevelStem(template.type(), template.generator());
                mapped.register(stemKey, stem, registrationInfo);
            } finally {
                mapped.freeze();
            }
        }

        MinecraftServerRuntimeWorldAccess access =
                (MinecraftServerRuntimeWorldAccess) server;
        RuntimeLevelData levelData =
                new RuntimeLevelData(server.getWorldData(), server.getWorldData().overworldData());
        long seed = BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed());

        ServerLevel level = new ServerLevel(
                server,
                access.hexic$getExecutor(),
                access.hexic$getStorageSource(),
                levelData,
                worldKey,
                stem,
                NO_PROGRESS,
                false,
                seed,
                List.of(),
                false,
                null
        );

        server.overworld().getWorldBorder().addListener(
                new BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder()));
        server.forgeGetWorldMap().put(worldKey, level);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));

        // Match vanilla/Fantasy initialization: make the level immediately usable
        // by a spell in the same server tick that created it.
        level.tick(() -> true);
        return level;
    }

    public static void unload(MinecraftServer server, ServerLevel level, boolean deleteFiles) {
        ResourceKey<Level> key = level.dimension();
        if (!server.forgeGetWorldMap().remove(key, level)) {
            return;
        }

        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        try {
            if (!deleteFiles) {
                level.save(null, true, false);
            } else {
                level.noSave = true;
            }
            level.close();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close runtime world " + key.location(), exception);
        }

        if (deleteFiles) {
            MinecraftServerRuntimeWorldAccess access =
                    (MinecraftServerRuntimeWorldAccess) server;
            LevelStorageSource.LevelStorageAccess storage = access.hexic$getStorageSource();
            Path worldRoot = storage.getWorldDir().toAbsolutePath().normalize();
            Path dimensionPath = storage.getDimensionPath(key).toAbsolutePath().normalize();
            if (!dimensionPath.startsWith(worldRoot) || dimensionPath.equals(worldRoot)) {
                throw new IllegalStateException(
                        "Refusing to delete runtime-world path outside the save: " + dimensionPath);
            }
            deleteRecursively(dimensionPath);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (IOException | RuntimeException exception) {
            Throwable cause = exception.getCause() instanceof IOException
                    ? exception.getCause()
                    : exception;
            throw new IllegalStateException("Failed to delete runtime-world directory " + path, cause);
        }
    }
}
