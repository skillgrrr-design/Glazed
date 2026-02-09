package com.ivoryk.modules.combat;


import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class TriggerBot extends Module {
    public TriggerBot() {
        super(Categories.Combat, "TriggerBot", "Attacks specified entities around you.");
    }

    @Override
    public void onDeactivate() {
        hitDelayTimer = 0;
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .build()
    );

    private final Setting<Boolean> babies = sgGeneral.add(new BoolSetting.Builder()
        .name("babies")
        .description("Whether or not to attack baby variants of the entity.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smartDelay = sgTiming.add(new BoolSetting.Builder()
        .name("smart-delay")
        .description("Uses the vanilla cooldown to attack entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("How fast you hit the entity in ticks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(60)
        .visible(() -> !smartDelay.get())
        .build()
    );

    private final Setting<Boolean> randomDelayEnabled = sgTiming.add(new BoolSetting.Builder()
        .name("random-delay-enabled")
        .description("Adds a random delay between hits to attempt to bypass anti-cheats.")
        .defaultValue(false)
        .visible(() -> !smartDelay.get())
        .build()
    );

    private final Setting<Integer> randomDelayMax = sgTiming.add(new IntSetting.Builder()
        .name("random-delay-max")
        .description("The maximum value for random delay.")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .visible(() -> randomDelayEnabled.get() && !smartDelay.get())
        .build()
    );

    private final Setting<Boolean> onlyCrits = sgTiming.add(new BoolSetting.Builder()
        .name("only-crits")
        .description("Attack enemy only when guaranteed critical hit (in air or levitating)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreOnlyCritsOnLevitation = sgTiming.add(new BoolSetting.Builder()
        .name("ignore-only-crits-on-levetation")
        .defaultValue(true)
        .visible(() -> onlyCrits.get())
        .build()
    );

    private int hitDelayTimer;
    private int airTicks = 0; // Contador de ticks en el aire

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity && ((LivingEntity) entity).isDead()) || !entity.isAlive()) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (entity instanceof Tameable tameable) {
            try {
                Object owner = tameable.getOwner();
                if (owner != null && owner.equals(mc.player)) return false;
            } catch (Throwable ignored) {}
        }
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;

            // Optional checks for AntiBotPlus and Teams via reflection (if present)
            try {
                Class<?> antiCls = Class.forName("nekiplay.meteorplus.features.modules.combat.AntiBotPlus");
                Object anti = Modules.get().get((Class) antiCls);
                if (anti != null) {
                    Method isBot = anti.getClass().getMethod("isBot", PlayerEntity.class);
                    Boolean res = (Boolean) isBot.invoke(anti, player);
                    if (res != null && res) return false;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable ignored) {}

            try {
                Class<?> teamsCls = Class.forName("nekiplay.meteorplus.features.modules.combat.Teams");
                Object teams = Modules.get().get((Class) teamsCls);
                if (teams != null) {
                    Method isInYourTeam = teams.getClass().getMethod("isInYourTeam", PlayerEntity.class);
                    Boolean res = (Boolean) isInYourTeam.invoke(teams, player);
                    if (res != null && res) return false;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable ignored) {}
        }

        return !(entity instanceof AnimalEntity) || babies.get() || !((AnimalEntity) entity).isBaby();
    }

    private boolean delayCheck() {
        // Only-crits: Attack only when guaranteed critical (in air or levitating for more than 1 tick)
        if (onlyCrits.get()) {
            boolean isInAir = !mc.player.isOnGround();
            boolean hasLevitation = mc.player.hasStatusEffect(StatusEffects.LEVITATION);
            
            if (hasLevitation) {
                airTicks = 3; // Reset air ticks para mantener críticos con levitación
            } else if (isInAir) {
                airTicks++;
            } else {
                airTicks = 0;
            }
            
            // Solo atacar si tiene al menos 2 ticks en el aire (significa que saltó)
            if (airTicks < 2 && !hasLevitation) {
                return false;
            }
        }

        if (smartDelay.get()) return mc.player.getAttackCooldownProgress(0.5f) >= 1;

        if (hitDelayTimer > 0) {
            hitDelayTimer--;
            return false;
        } else {
            hitDelayTimer = hitDelay.get();
            if (randomDelayEnabled.get()) hitDelayTimer += Math.round(ThreadLocalRandom.current().nextDouble() * randomDelayMax.get());
            return true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;
        // Only attack when the crosshair raycast hit an entity (hitbox intersection)
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != net.minecraft.util.hit.HitResult.Type.ENTITY) return;

        if (!(mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult)) return;

        net.minecraft.util.hit.EntityHitResult ehr = (net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget;
        Entity hit = ehr.getEntity();

        if (hit == null) return;

        if (delayCheck() && entityCheck(hit)) {
            hitEntity(hit);
        }
    }

    private boolean needCrit(Entity e) {
        try {
            Class<?> critCls = Class.forName("nekiplay.meteorplus.features.modules.combat.criticals.CriticalsPlus");
            Method nc = critCls.getDeclaredMethod("needCrit", Entity.class);
            return (Boolean) nc.invoke(null, e);
        } catch (Throwable ignored) {}
        return false;
    }

    private void hitEntity(Entity target) {
        try {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }
}
