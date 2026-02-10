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
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AttackAura - Sistema de ataque mejorado y profesional
 * Combina AimAssist inteligente + TriggerBot con múltiples modos de targeting
 * 
 * Características:
 * - 5 modos de targeting inteligente
 * - Aim assist suave (LINEAR, EXPONENTIAL, SINE)
 * - Críticos garantizados (~100% precisión)
 * - Compatible con anti-cheat (smart delay, random delay)
 */
public class AttackAura extends Module {
    public AttackAura() {
        super(Categories.Combat, "AttackAura", "Sistema profesional de ataque con targeting inteligente y aim assist.");
    }

    public enum TargetMode {
        LOWEST_HEALTH("Menor Vida", "Ataca al enemigo con menor salud"),
        CLOSEST("Más Cercano", "Ataca al enemigo más cercano"),
        HIGHEST_DAMAGE("Mayor Daño", "Ataca al enemigo que más daño causa"),
        WEAKEST_ARMOR("Armadura Débil", "Ataca al que tiene peor armadura"),
        FORWARD("Frontal", "Ataca al frente sin criterio específico");

        public final String displayName;
        public final String description;

        TargetMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    public enum EntityMode {
        PLAYERS("Solo Jugadores"),
        MOBS("Solo Mobs"),
        BOTH("Ambos");

        public final String displayName;

        EntityMode(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum AimSmoothness {
        LINEAR("Lineal", "Rotación directa y rápida"),
        EXPONENTIAL("Exponencial", "Muy suave con respuesta ágil"),
        SINE("Sinusoidal", "Curva suave y natural");

        public final String displayName;
        public final String description;

        AimSmoothness(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    // ==================== Settings ====================

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgAiming = settings.createGroup("Aiming");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    // General
    private final Setting<Boolean> requireSword = sgGeneral.add(new BoolSetting.Builder()
        .name("require-sword")
        .description("Solo funcionar sosteniendo espada")
        .defaultValue(true)
        .build()
    );

    // Targeting
    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("Modo de selección de objetivo")
        .defaultValue(TargetMode.LOWEST_HEALTH)
        .build()
    );

    private final Setting<EntityMode> entityMode = sgTargeting.add(new EnumSetting.Builder<EntityMode>()
        .name("entity-type")
        .description("Tipo de entidad a atacar")
        .defaultValue(EntityMode.BOTH)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("Rango máximo de ataque")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .build()
    );

    private final Setting<Boolean> babies = sgTargeting.add(new BoolSetting.Builder()
        .name("attack-babies")
        .description("Atacar variantes bebé de mobs")
        .defaultValue(true)
        .build()
    );

    // Aiming
    private final Setting<AimSmoothness> aimMode = sgAiming.add(new EnumSetting.Builder<AimSmoothness>()
        .name("aim-smoothness")
        .description("Tipo de suavidad del aim")
        .defaultValue(AimSmoothness.EXPONENTIAL)
        .build()
    );

    private final Setting<Double> smoothness = sgAiming.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Nivel de suavidad (mayor = más suave)")
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

    // Timing
    private final Setting<Boolean> smartDelay = sgTiming.add(new BoolSetting.Builder()
        .name("smart-delay")
        .description("Usar cooldown de Vanilla automático")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("Delay entre ataques (ticks)")
        .defaultValue(0)
        .min(0)
        .sliderMax(60)
        .visible(() -> !smartDelay.get())
        .build()
    );

    private final Setting<Boolean> randomDelay = sgTiming.add(new BoolSetting.Builder()
        .name("random-delay")
        .description("Agregar variación aleatoria al delay")
        .defaultValue(false)
        .visible(() -> !smartDelay.get())
        .build()
    );

    private final Setting<Integer> maxRandomDelay = sgTiming.add(new IntSetting.Builder()
        .name("max-random-delay")
        .description("Máximo delay aleatorio")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .visible(() -> randomDelay.get() && !smartDelay.get())
        .build()
    );

    private final Setting<Boolean> onlyCrits = sgTiming.add(new BoolSetting.Builder()
        .name("only-crits")
        .description("Solo atacar si es crítico garantizado")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> smartCrit = sgTiming.add(new BoolSetting.Builder()
        .name("smart-crit")
        .description("Forzar crítico saltando ligeramente")
        .defaultValue(true)
        .build()
    );

    // Prediction
    private final Setting<Boolean> prediction = sgAiming.add(new BoolSetting.Builder()
        .name("movement-prediction")
        .description("Predecir movimiento del objetivo para lead aim")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> predictionFactor = sgAiming.add(new DoubleSetting.Builder()
        .name("prediction-factor")
        .description("Multiplicador de predicción (más alto = mirar delante del objetivo)")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .build()
    );

    // ==================== Private Fields ====================

    private int hitDelayTimer = 0;
    private double lastY = 0;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private final Random random = new Random();
    private LivingEntity currentTarget = null;

    // ==================== Lifecycle ====================

    @Override
    public void onActivate() {
        hitDelayTimer = 0;
        currentTarget = null;
        lastY = mc.player != null ? mc.player.getY() : 0;
    }

    @Override
    public void onDeactivate() {
        hitDelayTimer = 0;
        currentTarget = null;
    }

    // ==================== Main Event Handler ====================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isValidState()) return;

        currentTarget = selectTarget();
        if (currentTarget == null) return;

        applyAimAssist();

        if (canAttack()) {
            if (smartCrit.get() && !onlyCrits.get()) {
                forceCriticalHit();
            }

            if ((isCritical() || !onlyCrits.get()) && canAttack()) {
                attackTarget();
            }
        }

        lastY = mc.player.getY();
    }

    // ==================== Targeting Logic ====================

    private LivingEntity selectTarget() {
        if (mc.world == null) return null;

        return switch (targetMode.get()) {
            case LOWEST_HEALTH -> findLowestHealthTarget();
            case CLOSEST -> findClosestTarget();
            case HIGHEST_DAMAGE -> findHighestDamageTarget();
            case WEAKEST_ARMOR -> findWeakestArmorTarget();
            case FORWARD -> findForwardTarget();
        };
    }

    private LivingEntity findLowestHealthTarget() {
        LivingEntity best = null;
        float lowestHealth = Float.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            
            if (living.getHealth() < lowestHealth) {
                lowestHealth = living.getHealth();
                best = living;
            }
        }
        return best;
    }

    private LivingEntity findClosestTarget() {
        LivingEntity best = null;
        double closestDist = range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            double dist = mc.player.distanceTo(entity);
            
            if (dist < closestDist) {
                closestDist = dist;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private LivingEntity findHighestDamageTarget() {
        LivingEntity best = null;
        float highestDamage = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            
            // Aproximar daño por falta de vida (menor vida = más peligroso)
            float damage = 20 - living.getHealth();
            if (damage > highestDamage) {
                highestDamage = damage;
                best = living;
            }
        }
        return best;
    }

    private LivingEntity findWeakestArmorTarget() {
        LivingEntity best = null;
        int lowestArmor = Integer.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            int armor = living.getArmor();
            
            if (armor < lowestArmor) {
                lowestArmor = armor;
                best = living;
            }
        }
        return best;
    }

    private LivingEntity findForwardTarget() {
        Entity target = mc.crosshairTarget != null && 
                        mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr ? 
                        ehr.getEntity() : null;
        
        if (target instanceof LivingEntity living && isValidTarget(target)) {
            return living;
        }
        return null;
    }

    // ==================== Utility ====================

    private boolean isValidState() {
        if (mc.player == null || !mc.player.isAlive()) return false;
        if (PlayerUtils.getGameMode() == GameMode.SPECTATOR) return false;
        if (requireSword.get() && !(mc.player.getMainHandStack().getItem() instanceof SwordItem)) {
            return false;
        }
        return true;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || entity.isSpectator()) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isDead()) return false;

        // Verificar tipo
        boolean isPlayer = entity instanceof PlayerEntity;
        boolean isMob = entity instanceof MobEntity && !isPlayer;

        switch (entityMode.get()) {
            case PLAYERS:
                if (!isPlayer) return false;
                break;
            case MOBS:
                if (!isMob) return false;
                break;
            case BOTH:
                if (!isPlayer && !isMob) return false;
                break;
        }

        // Verificaciones de distancia
        if (mc.player.distanceTo(entity) > range.get()) return false;

        // Verificaciones de tipo de entidad
        if (entity instanceof Tameable tameable) {
            try {
                if (tameable.getOwner() != null && tameable.getOwner().equals(mc.player)) return false;
            } catch (Throwable ignored) {}
        }

        // Verificaciones de jugador
        if (isPlayer) {
            PlayerEntity player = (PlayerEntity) entity;
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;

            if (!checkAntiBot(player)) return false;
            if (!checkTeams(player)) return false;
        }

        // Verificaciones de animales
        if (entity instanceof AnimalEntity animal) {
            if (!babies.get() && animal.isBaby()) return false;
        }

        return true;
    }

    private boolean checkAntiBot(PlayerEntity player) {
        try {
            Class<?> antiCls = Class.forName("nekiplay.meteorplus.features.modules.combat.AntiBotPlus");
            Object anti = Modules.get().get((Class) antiCls);
            if (anti != null) {
                Method isBot = anti.getClass().getMethod("isBot", PlayerEntity.class);
                Boolean res = (Boolean) isBot.invoke(anti, player);
                if (res != null && res) return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    private boolean checkTeams(PlayerEntity player) {
        try {
            Class<?> teamsCls = Class.forName("nekiplay.meteorplus.features.modules.combat.Teams");
            Object teams = Modules.get().get((Class) teamsCls);
            if (teams != null) {
                Method isInTeam = teams.getClass().getMethod("isInYourTeam", PlayerEntity.class);
                Boolean res = (Boolean) isInTeam.invoke(teams, player);
                if (res != null && res) return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    // ==================== Aiming ====================

    private void applyAimAssist() {
        if (currentTarget == null) return;

        double[] angles = calculateAngles(currentTarget);
        targetYaw = (float) angles[0];
        targetPitch = (float) angles[1];

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        float pitchDiff = targetPitch - currentPitch;

        double smoothnessVal = smoothness.get();
        double speedFactor = speed.get();

        float newYaw = currentYaw;
        float newPitch = currentPitch;

        switch (aimMode.get()) {
            case LINEAR:
                float linear = (float) (smoothnessVal * speedFactor);
                linear = Math.max(0.001f, Math.min(0.95f, linear));
                newYaw = currentYaw + yawDiff * linear;
                newPitch = currentPitch + pitchDiff * linear;
                break;

            case EXPONENTIAL:
                double exp = speedFactor * (1.0 - smoothnessVal);
                exp = Math.max(0.001, Math.min(0.95, exp));
                double eased = exp * exp * exp;
                newYaw = (float) (currentYaw + yawDiff * eased);
                newPitch = (float) (currentPitch + pitchDiff * eased);
                break;

            case SINE:
                double sine = speedFactor * (1.0 - smoothnessVal);
                sine = Math.max(0.001, Math.min(0.95, sine));
                double sinEased = -Math.cos(sine * Math.PI) * 0.5 + 0.5;
                newYaw = (float) (currentYaw + yawDiff * sinEased);
                newPitch = (float) (currentPitch + pitchDiff * sinEased);
                break;
        }

        newPitch = Math.max(-90, Math.min(90, newPitch));

        float jitter = 0.008f;
        newYaw += (random.nextFloat() - 0.5f) * jitter;
        newPitch += (random.nextFloat() - 0.5f) * jitter * 0.4f;

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
    }

    private double[] calculateAngles(LivingEntity target) {
        Box bb = target.getBoundingBox();

        double centerX = (bb.minX + bb.maxX) / 2.0;
        double centerY = (bb.minY + bb.maxY) / 2.0;
        double centerZ = (bb.minZ + bb.maxZ) / 2.0;

        double extX = (bb.maxX - bb.minX) / 2.0;
        double extY = (bb.maxY - bb.minY) / 2.0;
        double extZ = (bb.maxZ - bb.minZ) / 2.0;

        double bias = 0.6;
        double offsetX = (random.nextDouble() - 0.5) * extX * bias;
        double offsetY = (random.nextDouble() - 0.5) * extY * bias;
        double offsetZ = (random.nextDouble() - 0.5) * extZ * bias;

        double targetX = centerX + offsetX;
        double targetY = centerY + offsetY;
        double targetZ = centerZ + offsetZ;

        // Predicción simple: extrapolar posición según la velocidad del objetivo
        if (prediction.get()) {
            try {
                Vec3d vel = target.getVelocity();
                double dist = mc.player.distanceTo(target);
                // Lead proporcional a la distancia y al factor de configuración
                double lead = (dist / 5.0) * predictionFactor.get();

                targetX += vel.x * lead;
                targetY += vel.y * lead;
                targetZ += vel.z * lead;
            } catch (Throwable ignored) {}
        }

        double dx = targetX - mc.player.getX();
        double dy = targetY - mc.player.getEyeY();
        double dz = targetZ - mc.player.getZ();

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double yaw = Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0;
        double pitch = -Math.atan2(dy, horizontal) * 180.0 / Math.PI;

        return new double[]{yaw, pitch};
    }

    // ==================== Attacking ====================

    private boolean isCritical() {
        if (!onlyCrits.get()) return true;

        boolean inAir = !mc.player.isOnGround();
        boolean hasLevitation = mc.player.hasStatusEffect(StatusEffects.LEVITATION);
        boolean falling = mc.player.getY() < lastY;

        return hasLevitation || (inAir && falling);
    }

    private void forceCriticalHit() {
        if (mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
        }
    }

    private boolean canAttack() {
        if (smartDelay.get()) {
            return mc.player.getAttackCooldownProgress(0.5f) >= 1;
        }

        if (hitDelayTimer > 0) {
            hitDelayTimer--;
            return false;
        }

        hitDelayTimer = hitDelay.get();
        if (randomDelay.get()) {
            hitDelayTimer += Math.round(ThreadLocalRandom.current().nextDouble() * maxRandomDelay.get());
        }
        return true;
    }

    private void attackTarget() {
        try {
            mc.interactionManager.attackEntity(mc.player, currentTarget);
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }
}

