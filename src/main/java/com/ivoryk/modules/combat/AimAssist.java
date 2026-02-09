package com.ivoryk.modules.combat;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.Box;
import java.util.Random;

/**
 * AimAssist mejorado fusionado con TriggerBot:
 * - Targeting inteligente (menor vida primero)
 * - Asistencia de aim suave y realista
 * - Precisión de críticos ~100%
 * - Compatible con el sistema de TriggerBot
 */
public class AimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgAiming = settings.createGroup("Aiming");

    public enum TargetType {
        PLAYERS,
        MOBS,
        BOTH
    }

    public enum AimMode {
        LEGIT,
        FOV
    }

    public enum SmoothType {
        LINEAR,
        EXPONENTIAL,
        SINE
    }

    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Activar AimAssist")
        .defaultValue(true)
        .build()
    );

    private final Setting<TargetType> targetType = sgTargeting.add(new EnumSetting.Builder<TargetType>()
        .name("target-type")
        .description("Tipo de entidad a asistir")
        .defaultValue(TargetType.BOTH)
        .build()
    );

    private final Setting<Boolean> lowestHealthFirst = sgTargeting.add(new BoolSetting.Builder()
        .name("lowest-health-first")
        .description("Priorizar objetivo con menor vida")
        .defaultValue(true)
        .build()
    );

    private final Setting<AimMode> aimMode = sgTargeting.add(new EnumSetting.Builder<AimMode>()
        .name("aim-mode")
        .description("Modo de aim: LEGIT (sin FOV), FOV (solo dentro de FOV)")
        .defaultValue(AimMode.FOV)
        .build()
    );

    private final Setting<SmoothType> smoothType = sgAiming.add(new EnumSetting.Builder<SmoothType>()
        .name("smooth-type")
        .description("Tipo de suavidad: LINEAR, EXPONENTIAL, SINE")
        .defaultValue(SmoothType.EXPONENTIAL)
        .build()
    );

    private final Setting<Double> smoothness = sgAiming.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Suavidad del aim (mayor = más suave)")
        .defaultValue(0.08)
        .min(0.01)
        .max(0.5)
        .build()
    );

    private final Setting<Double> speed = sgAiming.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Velocidad del aim (mayor = más rápido)")
        .defaultValue(3.0)
        .min(0.5)
        .max(10.0)
        .build()
    );

    private final Setting<Double> range = sgAiming.add(new DoubleSetting.Builder()
        .name("range")
        .description("Rango del aim assist")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .build()
    );

    private final Setting<Double> fov = sgAiming.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Campo de visión para aim assist")
        .defaultValue(45.0)
        .min(10.0)
        .max(180.0)
        .build()
    );

    private final Setting<Boolean> requireSword = sgGeneral.add(new BoolSetting.Builder()
        .name("require-sword")
        .description("Solo funcionar sosteniendo espada")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableWhileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-while-eating")
        .description("Desactivar mientras se come")
        .defaultValue(true)
        .build()
    );

    private int updateCounter = 0;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private final Random random = new Random();
    private LivingEntity currentTarget = null;

    public AimAssist() {
        super(Categories.Combat, "AimAssist", "Aim assist suave y fusionado con TriggerBot");
    }

    public void onUpdate() {
        if (!enabled.get()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (disableWhileEating.get() && (player.getActiveItem() != null && !player.getActiveItem().isEmpty())) {
            return;
        }

        if (requireSword.get() && !(player.getMainHandStack().getItem() instanceof SwordItem)) {
            return;
        }

        updateCounter++;
        if (updateCounter < 1) {
            return;
        }
        updateCounter = 0;

        // Obtener el mejor objetivo (menor vida o más cercano)
        currentTarget = getBestTarget(player);
        if (currentTarget == null) {
            return;
        }

        double[] angles = calculateAngles(player, currentTarget);
        targetYaw = (float) angles[0];
        targetPitch = (float) angles[1];

        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // Aplicar interpolación según el tipo seleccionado
        double smoothnessValue = this.smoothness.get();
        double speedFactor = this.speed.get();

        float newYaw = currentYaw;
        float newPitch = currentPitch;

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        float pitchDiff = targetPitch - currentPitch;

        switch (smoothType.get()) {
            case LINEAR:
                float linearInterp = (float) (smoothnessValue * speedFactor);
                linearInterp = Math.max(0.001f, Math.min(0.95f, linearInterp));
                newYaw = currentYaw + yawDiff * linearInterp;
                newPitch = currentPitch + pitchDiff * linearInterp;
                break;

            case EXPONENTIAL:
                double progress = speedFactor * (1.0 - smoothnessValue);
                progress = Math.max(0.001, Math.min(0.95, progress));
                double eased = progress * progress * progress;
                newYaw = (float) (currentYaw + yawDiff * eased);
                newPitch = (float) (currentPitch + pitchDiff * eased);
                break;

            case SINE:
                double sineProgress = speedFactor * (1.0 - smoothnessValue);
                sineProgress = Math.max(0.001, Math.min(0.95, sineProgress));
                double sinEased = -Math.cos(sineProgress * Math.PI) * 0.5 + 0.5;
                newYaw = (float) (currentYaw + yawDiff * sinEased);
                newPitch = (float) (currentPitch + pitchDiff * sinEased);
                break;
        }

        newPitch = Math.max(-90, Math.min(90, newPitch));

        // Pequeño jitter para parecer más humano
        float jitterScale = 0.008f;
        newYaw += (random.nextFloat() - 0.5f) * jitterScale;
        newPitch += (random.nextFloat() - 0.5f) * jitterScale * 0.4f;

        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        onUpdate();
    }

    private LivingEntity getBestTarget(ClientPlayerEntity player) {
        LivingEntity bestTarget = null;
        double lowestHealth = Float.MAX_VALUE;
        double closestDistance = range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == player || entity.isSpectator()) {
                continue;
            }

            if (!(entity instanceof LivingEntity)) continue;

            LivingEntity livingEntity = (LivingEntity) entity;
            if (livingEntity.isDead()) continue;

            boolean isPlayer = entity instanceof PlayerEntity;
            boolean isMob = entity instanceof MobEntity;
            boolean shouldTarget = false;

            switch (targetType.get()) {
                case PLAYERS:
                    shouldTarget = isPlayer;
                    break;
                case MOBS:
                    shouldTarget = isMob && !isPlayer;
                    break;
                case BOTH:
                    shouldTarget = isPlayer || isMob;
                    break;
            }

            if (!shouldTarget) continue;

            double distance = player.distanceTo(entity);
            if (distance > closestDistance) continue;

            // Aplicar filtro de FOV solo en modo FOV
            if (aimMode.get() == AimMode.FOV) {
                double[] angles = calculateAngles(player, entity);
                float yawDiff = (float) angles[0] - player.getYaw();
                float pitchDiff = (float) angles[1] - player.getPitch();

                while (yawDiff > 180) yawDiff -= 360;
                while (yawDiff < -180) yawDiff += 360;

                double angleDiff = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                if (angleDiff > fov.get()) continue;
            }

            // Seleccionar por menor vida si está habilitado
            if (lowestHealthFirst.get()) {
                float health = livingEntity.getHealth();
                if (health < lowestHealth) {
                    lowestHealth = health;
                    bestTarget = livingEntity;
                }
            } else {
                // Seleccionar por distancia más cercana
                if (distance < closestDistance) {
                    closestDistance = distance;
                    bestTarget = livingEntity;
                }
            }
        }

        return bestTarget;
    }

    private double[] calculateAngles(ClientPlayerEntity player, Entity target) {
        Box bb = target.getBoundingBox();

        double centerX = (bb.minX + bb.maxX) / 2.0;
        double centerY = (bb.minY + bb.maxY) / 2.0;
        double centerZ = (bb.minZ + bb.maxZ) / 2.0;

        double extX = (bb.maxX - bb.minX) / 2.0;
        double extY = (bb.maxY - bb.minY) / 2.0;
        double extZ = (bb.maxZ - bb.minZ) / 2.0;

        // Bias offsets hacia el centro para precisión
        double biasFactor = 0.6;
        double offsetX = (random.nextDouble() - 0.5) * extX * biasFactor;
        double offsetY = (random.nextDouble() - 0.5) * extY * biasFactor;
        double offsetZ = (random.nextDouble() - 0.5) * extZ * biasFactor;

        double targetX = centerX + offsetX;
        double targetY = centerY + offsetY;
        double targetZ = centerZ + offsetZ;

        double dx = targetX - player.getX();
        double dy = targetY - player.getEyeY();
        double dz = targetZ - player.getZ();

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
        double pitch = -Math.atan2(dy, horizontalDistance) * 180.0 / Math.PI;

        return new double[]{yaw, pitch};
    }

    @Override
    public void onDeactivate() {
        updateCounter = 0;
        currentTarget = null;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }
}

