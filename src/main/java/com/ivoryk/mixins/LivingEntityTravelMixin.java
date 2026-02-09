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
    private Vec3d modifyTravelMovement(Vec3d movement) {
        NoSlow noSlow = (NoSlow) Modules.get().get(NoSlow.class);
        if (noSlow == null || !noSlow.isActive()) {
            return movement;
        }

        if (!noSlow.canNoSlow()) {
            return movement;
        }

        // NoSlow real: elimina penalización de uso de items sin aumentar velocidad base
        // Micro-compensación por anticheat según modo (< 5% para ser invisible)
        double factor = 1.0;
        
        switch (noSlow.getMode()) {
            case Grim:
            case GrimNew:
                factor = 1.0;  // Sin compensación: Grim es muy estricto
                break;
            case Matrix:
            case Matrix2:
                factor = 1.01; // +1% micro-compensación
                break;
            case NCP:
            case StrictNCP:
                factor = 1.02; // +2% micro-compensación
                break;
            default:
                factor = 1.0;
        }

        // Aplicar factor solo si no crea anomalía de movimiento
        return new Vec3d(movement.x * factor, movement.y, movement.z * factor);
    }
}
