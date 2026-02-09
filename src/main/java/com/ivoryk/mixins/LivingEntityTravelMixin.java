package com.ivoryk.mixins;

import com.ivoryk.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTravelMixin {

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d modifyTravelVelocity(Vec3d movement) {
        NoSlow noSlow = (NoSlow) Modules.get().get(NoSlow.class);
        if (noSlow == null || !noSlow.isActive()) {
            return movement;
        }

        if (!noSlow.canNoSlow()) {
            return movement;
        }

        // Apply NoSlow effect by completely bypassing slowdown
        // For Grim/strict servers, apply more aggressive velocity restoration
        return new Vec3d(movement.x * 1.8d, movement.y, movement.z * 1.8d);
    }
}
