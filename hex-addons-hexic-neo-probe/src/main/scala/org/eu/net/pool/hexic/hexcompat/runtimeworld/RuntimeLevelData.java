package org.eu.net.pool.hexic.hexcompat.runtimeworld;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * Per-demiplane time/weather state.
 *
 * <p>{@link DerivedLevelData} normally delegates these values to the overworld.
 * A real Hexic demiplane must not mutate or mirror the overworld clock/weather,
 * so the mutable values used by {@code ServerLevel} are kept locally.</p>
 */
public final class RuntimeLevelData extends DerivedLevelData {
    private long gameTime;
    private long dayTime = 6000L;
    private int clearWeatherTime = Integer.MAX_VALUE;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private final BlockPos spawn = new BlockPos(5, 1, 5);

    public RuntimeLevelData(WorldData worldData, ServerLevelData overworldData) {
        super(worldData, overworldData);
        this.gameTime = overworldData.getGameTime();
    }

    @Override
    public BlockPos getSpawnPos() {
        return this.spawn;
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public void setGameTime(long gameTime) {
        this.gameTime = gameTime;
    }

    @Override
    public long getDayTime() {
        return this.dayTime;
    }

    @Override
    public void setDayTime(long dayTime) {
        this.dayTime = dayTime;
    }

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int clearWeatherTime) {
        this.clearWeatherTime = clearWeatherTime;
    }

    @Override
    public boolean isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int rainTime) {
        this.rainTime = rainTime;
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }
}
