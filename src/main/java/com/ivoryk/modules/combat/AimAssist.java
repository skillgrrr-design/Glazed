package com.ivoryk.modules.combat;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import java.util.Random;
import net.minecraft.util.math.Box;

public class AimAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable aim assist")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<TargetType> targetType = sgGeneral.add(new EnumSetting.Builder<TargetType>()
        .name("target-type")
        .description("Type of entities to assist aim")
        .defaultValue(TargetType.BOTH)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<AimMode> aimMode = sgGeneral.add(new EnumSetting.Builder<AimMode>()
        .name("aim-mode")
        .description("Aim behavior: LEGIT ignores FOV, FOV only targets inside FOV")
        .defaultValue(AimMode.FOV)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<SmoothType> smoothType = sgGeneral.add(new EnumSetting.Builder<SmoothType>()
        .name("smooth-type")
        .description("Type of smoothing: LINEAR (direct), EXPONENTIAL (very smooth), SINE (curved)")
        .defaultValue(SmoothType.EXPONENTIAL)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Double> smoothness = sgGeneral.add(new DoubleSetting.Builder()
        .name("smoothness")
        .description("Aim smoothness (higher = smoother, lower = faster response)")
        .defaultValue(0.08)
        .min(0.01)
        .max(0.5)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the aim moves (higher = faster)")
        .defaultValue(3.0)
        .min(0.5)
        .max(10.0)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Aim assist range")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Field of view for aim assist")
        .defaultValue(45.0)
        .min(10.0)
        .max(180.0)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> requireSword = sgGeneral.add(new BoolSetting.Builder()
        .name("require-sword")
        .description("Only work when holding a sword")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> disableWhileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-while-eating")
        .description("Disable aim assist while eating")
        .defaultValue(true)
        .build()
    );

    private int updateCounter = 0;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private final Random random = new Random();

    public AimAssist() {
        super(Categories.Combat, "AimAssist", "Smooth and fast aim assist");
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

        Entity target = getClosestValidTarget(player);
        if (target == null) {
            return;
        }

        double[] angles = calculateAngles(player, target);
        targetYaw = (float) angles[0];
        targetPitch = (float) angles[1];

        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // Aplicar interpolación según el tipo seleccionado
        double smoothness = this.smoothness.get();
        double speedFactor = this.speed.get();

        float newYaw = currentYaw;
        float newPitch = currentPitch;

        float yawDiff = targetYaw - currentYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        float pitchDiff = targetPitch - currentPitch;

        switch (smoothType.get()) {
            case LINEAR:
                // Interpolación lineal simple y rápida
                float linearInterp = (float) (smoothness * speedFactor);
                linearInterp = Math.max(0.001f, Math.min(0.95f, linearInterp));
                newYaw = currentYaw + yawDiff * linearInterp;
                newPitch = currentPitch + pitchDiff * linearInterp;
                break;

            case EXPONENTIAL:
                // Exponential decay: muy suave pero responde rápido
                // Usa potencia exponencial para suavidad orgánica
                double progress = speedFactor * (1.0 - smoothness);
                progress = Math.max(0.001, Math.min(0.95, progress));
                
                // Aplicar cubo para suavidad extrema: progress^3
                double eased = progress * progress * progress;
                newYaw = (float) (currentYaw + yawDiff * eased);
                newPitch = (float) (currentPitch + pitchDiff * eased);
                break;

            case SINE:
                // Interpolación con función seno (smooth curva)
                double sineProgress = speedFactor * (1.0 - smoothness);
                sineProgress = Math.max(0.001, Math.min(0.95, sineProgress));
                
                // Usar seno para suavidad: -cos(t) * 0.5 + 0.5
                double sinEased = -Math.cos(sineProgress * Math.PI) * 0.5 + 0.5;
                newYaw = (float) (currentYaw + yawDiff * sinEased);
                newPitch = (float) (currentPitch + pitchDiff * sinEased);
                break;
        }

        newPitch = Math.max(-90, Math.min(90, newPitch));

        // Jitter muy pequeño para que se vea más legit
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

    private Entity getClosestValidTarget(ClientPlayerEntity player) {
        Entity closestTarget = null;
        double closestDistance = range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == player || entity.isSpectator()) {
                continue;
            }

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

            double[] angles = calculateAngles(player, entity);
            float yawDiff = (float) angles[0] - player.getYaw();
            float pitchDiff = (float) angles[1] - player.getPitch();

            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;

            double angleDiff = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
            // Apply FOV filtering only when mode is FOV; LEGIT ignores FOV (can target behind)
            if (aimMode.get() == AimMode.FOV && angleDiff > fov.get()) continue;

            closestDistance = distance;
            closestTarget = entity;
        }

        return closestTarget;
    }

    private double[] calculateAngles(ClientPlayerEntity player, Entity target) {
        Box bb = target.getBoundingBox();

        double centerX = (bb.minX + bb.maxX) / 2.0;
        double centerY = (bb.minY + bb.maxY) / 2.0;
        double centerZ = (bb.minZ + bb.maxZ) / 2.0;

        double extX = (bb.maxX - bb.minX) / 2.0;
        double extY = (bb.maxY - bb.minY) / 2.0;
        double extZ = (bb.maxZ - bb.minZ) / 2.0;

        // Bias offsets towards center (smaller offsets so aim favors the middle)
        double biasFactor = 0.6; // lower = more centered
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
    }
}
