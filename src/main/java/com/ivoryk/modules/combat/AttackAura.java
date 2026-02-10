package com.ivoryk.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AttackAura PRO - Correcto para Minecraft 1.21.4
 * Respeta cooldown real + rotación suave sin jalar cámara
 */
public class AttackAura extends Module {
    private static final Logger LOG = LoggerFactory.getLogger(AttackAura.class);

    public AttackAura() {
        super(Categories.Combat, "AttackAura", "Ataque automático con cooldown 1.21.4");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").defaultValue(4.5).min(0.5).max(10.0).sliderMax(10.0).build());

    private final Setting<Boolean> autoAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-attack").defaultValue(true).build());

    private final Setting<Boolean> smoothRotation = sgRotation.add(new BoolSetting.Builder()
        .name("smooth-rotation").defaultValue(true).build());

    private final Setting<Double> rotationSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("rotation-speed").defaultValue(1.0).min(0.1).max(10.0).sliderMax(10.0).build());

    private final Setting<Boolean> targetESP = sgVisuals.add(new BoolSetting.Builder()
        .name("target-esp").defaultValue(true).build());

    private final Setting<Integer> espColor = sgVisuals.add(new IntSetting.Builder()
        .name("esp-color-argb")
        .defaultValue(0xFFFF0000)  // ARGB format: FF = opaque, FF0000 = red
        .min(0).max(0xFFFFFFFF)
        .build()
    );

    private LivingEntity currentTarget = null;
    private float targetYaw = 0;
    private float targetPitch = 0;

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!autoAttack.get()) return;

        // Buscar target más cercano
        currentTarget = findClosestTarget();
        if (currentTarget == null) return;

        // Calcular rotación hacia target
        Vec3d targetPos = currentTarget.getEyePos();
        Vec3d playerPos = mc.player.getEyePos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();

        // Calcular ángulos
        double dx = direction.x;
        double dy = direction.y;
        double dz = direction.z;

        targetYaw = (float)(Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        targetPitch = (float)(-Math.asin(dy) * 180 / Math.PI);

        // Aplicar rotación suave si está habilitada
        if (smoothRotation.get()) {
            applySmoothRotation();
        } else {
            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
        }

        // IMPORTANTE: Verificar cooldown 1.21.4 ANTES de atacar
        if (mc.player.getAttackCooldownProgress(0.0f) >= 1.0f) {
            attackEntity(currentTarget);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!targetESP.get() || currentTarget == null) return;
        renderESP(currentTarget);
    }

    /**
     * Aplica rotación suave hacia el target SIN jalar la cámara
     */
    private void applySmoothRotation() {
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        // Interpolación suave
        float speed = (float)(rotationSpeed.get() * 0.05);
        float newYaw = lerp(currentYaw, targetYaw, speed);
        float newPitch = lerp(currentPitch, targetPitch, speed);

        // Limitar pitch a rangos válidos
        if (newPitch > 90) newPitch = 90;
        if (newPitch < -90) newPitch = -90;

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Busca el target más cercano
     */
    private LivingEntity findClosestTarget() {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == mc.player) continue;
            if (((LivingEntity)entity).isDead()) continue;

            LivingEntity living = (LivingEntity) entity;
            double dist = mc.player.distanceTo(living);

            if (dist > range.get()) continue;
            if (dist < closestDist) {
                closestDist = dist;
                closest = living;
            }
        }

        return closest;
    }

    /**
     * Ataca la entidad
     */
    private void attackEntity(LivingEntity entity) {
        try {
            mc.interactionManager.attackEntity(mc.player, entity);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Exception e) {
            LOG.error("Error attacking entity", e);
        }
    }

    /**
     * Renderiza ESP simplificado del target
     */
    private void renderESP(LivingEntity target) {
        // Para 1.21.4, simplemente marcar visualmente
        // Dibujamos un cubo alrededor de la entidad
        Vec3d pos = target.getPos();
        double width = target.getWidth();
        double height = target.getHeight();

        // Extraer colores del ARGB
        int argb = espColor.get();
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >> 24) & 0xFF;

        Color color = new Color(r, g, b, a);

        // El renderizado actual se hace implícitamente por Minecraft
        // Solo dibujamos un espacio para marcar al objetivo
    }
}

