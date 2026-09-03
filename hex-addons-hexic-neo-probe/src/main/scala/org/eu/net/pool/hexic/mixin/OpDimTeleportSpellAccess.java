package org.eu.net.pool.hexic.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Runtime accessor used by the outer Oneironaut cost hook and regression probes. */
@Pseudo
@Mixin(targets = "net.beholderface.oneironaut.casting.patterns.spells.great.OpDimTeleport$Spell")
public interface OpDimTeleportSpellAccess {
    @Accessor("target")
    Entity hexic$getTarget();

    @Accessor("origin")
    ServerLevel hexic$getOrigin();

    @Accessor("destination")
    ServerLevel hexic$getDestination();

    @Accessor("coords")
    Vec3 hexic$getCoords();
}
