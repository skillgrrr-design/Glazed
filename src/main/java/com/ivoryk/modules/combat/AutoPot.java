package com.ivoryk.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AutoPot mejorado para GrimNew:
 * - Timing adaptativo y realista
 * - Rotación suave para evitar detección
 * - Búsqueda inteligente de pociones
 * - Compatible con servidores anti-cheat
 */
public class AutoPot extends Module {
    public AutoPot() {
        super(Categories.Combat, "AutoPot", "Usa pociones automáticamente con rotación suave compatible con anti-cheat.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Integer> healthThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("health-threshold")
        .description("Umbral de salud para usar pociones.")
        .defaultValue(10)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> hotbarOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-only")
        .description("Solo usar pociones de la hotbar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Velocidad de rotación (más alto = más rápido).")
        .defaultValue(6.0)
        .min(0.1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> minDelay = sgTiming.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Delay mínimo entre usos de pociones (ticks).")
        .defaultValue(3)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> maxDelay = sgTiming.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Delay máximo entre usos de pociones (ticks).")
        .defaultValue(8)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> randomizeRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("randomize-rotation")
        .description("Variación aleatoria en la rotación para parecer más humano.")
        .defaultValue(true)
        .build()
    );

    private int useDelay = 0;
    private int targetSlot = -1;
    private float targetPitch = 90f;
    private float targetYaw = 0f;
    private float lastYaw = 0f;
    private float lastPitch = 0f;

    @Override
    public void onActivate() {
        useDelay = 0;
        targetSlot = -1;
    }

    @Override
    public void onDeactivate() {
        useDelay = 0;
        targetSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        // Si la salud está por debajo del umbral normal, o si una predicción
        // indica que el próximo golpe podría matarnos (o dejarnos a <= 3 corazones), potear.
        float predicted = predictNextHitDamage();
        boolean willBeLow = (health - predicted) <= 6.0f; // 3 corazones = 6 health
        if (health > healthThreshold.get() && !willBeLow) return;

        if (useDelay > 0) {
            useDelay--;
            return;
        }

        // Buscar poción en hotbar primero
        int potionSlot = findPotionInHotbar();
        
        // Si no está en hotbar, buscar en inventario
        if (potionSlot == -1 && !hotbarOnly.get()) {
            potionSlot = findPotionInInventory();
        }

        if (potionSlot == -1) return;

        // Si la poción no está en hotbar, moverla
        if (potionSlot > 8) {
            movePotionToHotbar(potionSlot);
            return;
        }

        // Aplicar rotación suave
        applySmoothedRotation();

        // Usar poción cuando la rotación esté lista
        if (isRotationReady()) {
            usePotionAtSlot(potionSlot);
            int delayRange = maxDelay.get() - minDelay.get();
            useDelay = minDelay.get() + (delayRange > 0 ? ThreadLocalRandom.current().nextInt(delayRange) : 0);
        }
    }

    private int findPotionInHotbar() {
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty() && isPotionItem(stack.getItem())) {
                return i;
            }
        }
        return -1;
    }

    private int findPotionInInventory() {
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty() && isPotionItem(stack.getItem())) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPotionItem(net.minecraft.item.Item item) {
        return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    // ==================== Predicción de daño ====================
    // Estima el daño que una entidad cercana podría hacer en el siguiente golpe.
    private float predictNextHitDamage() {
        if (mc.world == null || mc.player == null) return 0f;

        float maxPred = 0f;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity)) continue;
            LivingEntity le = (LivingEntity) e;

            double dist = mc.player.distanceTo(le);
            // Solo considerar entidades en rango cuerpo a cuerpo razonable
            if (dist > 5.0) continue;

            // Si la entidad claramente no nos puede atacar, ignórala
            if (le.isDead() || le.isRemoved()) continue;

            float est = estimateEntityDamage(le);

            // Si la entidad nos está mirando y cerca, aumentar prioridad
            double dx = mc.player.getX() - le.getX();
            double dz = mc.player.getZ() - le.getZ();
            double lookDot = Math.cos(Math.toRadians(Math.abs(le.getYaw() - mc.player.getYaw())));
            if (lookDot > 0.5 && dist < 4.0) est *= 1.1f;

            if (est > maxPred) maxPred = est;
        }

        return maxPred;
    }

    // Estimación simple por tipo de entidad / jugador
    private float estimateEntityDamage(LivingEntity e) {
        // Jugadores suelen hacer más daño (armadura no considerada)
        if (e instanceof net.minecraft.entity.player.PlayerEntity) return 8.0f;
        if (e instanceof net.minecraft.entity.mob.SkeletonEntity) return 5.0f;
        if (e instanceof net.minecraft.entity.mob.ZombieEntity) return 6.0f;
        if (e instanceof net.minecraft.entity.mob.EndermanEntity) return 9.0f;
        if (e instanceof net.minecraft.entity.mob.SpiderEntity) return 4.0f;
        if (e instanceof net.minecraft.entity.mob.CreeperEntity) return 12.0f;

        // Fallback conservador
        return 5.0f;
    }

    private void movePotionToHotbar(int potionSlot) {
        try {
            int syncId = mc.player.playerScreenHandler.syncId;
            
            // Encontrar slot vacío en hotbar
            int emptySlot = -1;
            for (int i = 0; i <= 8; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    emptySlot = i;
                    break;
                }
            }
            
            if (emptySlot == -1) return;
            
            mc.interactionManager.clickSlot(syncId, potionSlot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, emptySlot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
        } catch (Throwable ignored) {
        }
    }

    private void applySmoothedRotation() {
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        
        // Calcular target con variación aleatoria si está habilitado
        if (randomizeRotation.get()) {
            float randomYaw = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 30f;
            targetYaw = randomYaw;
        } else {
            targetYaw = 0f;
        }
        targetPitch = 90f;
        
        // Aplicar interpolación suave
        double speed = rotationSpeed.get() / 10.0;
        float newYaw = currentYaw + (targetYaw - currentYaw) * (float) speed;
        float newPitch = Math.min(currentPitch + (targetPitch - currentPitch) * (float) speed, 90f);
        
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        
        lastYaw = newYaw;
        lastPitch = newPitch;
    }

    private boolean isRotationReady() {
        float pitchDiff = Math.abs(mc.player.getPitch() - 90f);
        return pitchDiff < 5f;
    }

    private void usePotionAtSlot(int slot) {
        try {
            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }
}
