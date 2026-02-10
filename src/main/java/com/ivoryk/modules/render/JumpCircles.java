package com.ivoryk.modules.render;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * JumpCircles Module - Renderiza círculos visibles al saltar
 * Versión arreglada: Usa BufferBuilder directo para máxima compatibilidad
 */
public class JumpCircles extends Module {
    private static final Logger LOG = LoggerFactory.getLogger(JumpCircles.class);

    public JumpCircles() {
        super(Categories.Render, "Jump Circles", "Círculos visibles coloridos al saltar - Compatible todos los renders");
    }

    private final SettingGroup sgColor = settings.createGroup("Color");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> colorRed = sgColor.add(new IntSetting.Builder()
        .name("red").defaultValue(100).min(0).max(255).sliderMax(255).build());

    private final Setting<Integer> colorGreen = sgColor.add(new IntSetting.Builder()
        .name("green").defaultValue(200).min(0).max(255).sliderMax(255).build());

    private final Setting<Integer> colorBlue = sgColor.add(new IntSetting.Builder()
        .name("blue").defaultValue(255).min(0).max(255).sliderMax(255).build());

    private final Setting<Double> maxRadius = sgRender.add(new DoubleSetting.Builder()
        .name("max-radius").defaultValue(3.0).min(0.5).max(10.0).sliderMax(10.0).build());

    private final Setting<Double> thickness = sgRender.add(new DoubleSetting.Builder()
        .name("thickness").defaultValue(0.15).min(0.05).max(0.5).sliderMax(0.5).build());

    private final Setting<Integer> segments = sgRender.add(new IntSetting.Builder()
        .name("segments").defaultValue(32).min(8).sliderMax(64).build());

    private final Setting<Double> lifetime = sgRender.add(new DoubleSetting.Builder()
        .name("lifetime").defaultValue(20.0).min(5.0).max(100.0).sliderMax(100.0).build());

    private final Setting<Boolean> fadeOut = sgRender.add(new BoolSetting.Builder()
        .name("fade-out").defaultValue(true).build());

    private final List<CircleInstance> circles = Collections.synchronizedList(new ArrayList<>());
    private boolean wasJumping = false;

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (circles.isEmpty()) return;

        synchronized (circles) {
            circles.removeIf(CircleInstance::isExpired);
            for (CircleInstance circle : circles) {
                circle.update();
                renderSimpleCircle(circle, event.matrices);
            }
        }
    }

    /**
     * Renderiza un círculo simple y visible
     */
    private void renderSimpleCircle(CircleInstance circle, MatrixStack matrices) {
        try {
            Vec3d pos = circle.getPosition();
            double progress = circle.getProgress();
            double radius = maxRadius.get() * progress;

            float opacity = 1.0f;
            if (fadeOut.get()) {
                opacity = Math.max(0, 1.0f - (float)(circle.getAge() / lifetime.get()));
            }

            int alpha = (int)(255 * opacity);
            Color color = new Color(colorRed.get(), colorGreen.get(), colorBlue.get(), alpha);

            matrices.push();
            matrices.translate(pos.x, pos.y + 0.01, pos.z);

            // Dibujar círculo en XZ
            drawCircleOutline(radius, segments.get(), color);

            matrices.pop();

        } catch (Exception e) {
            LOG.error("Error rendering circle", e);
        }
    }

    /**
     * Dibuja outline de círculo usando líneas básicas en plano XZ
     */
    private void drawCircleOutline(double radius, int segments, Color color) {
        var vertexConsumer = mc.getBufferBuilders().getEntityVertexConsumers()
            .getBuffer(net.minecraft.client.render.RenderLayer.getLines());

        MatrixStack matrices = new MatrixStack();
        matrices.push();

        float r = color.r / 255f;
        float g = color.g / 255f;
        float b = color.b / 255f;
        float a = color.a / 255f;

        for (int i = 0; i < segments; i++) {
            double angle1 = (2.0 * Math.PI * i) / segments;
            double angle2 = (2.0 * Math.PI * (i + 1)) / segments;

            double x1 = Math.cos(angle1) * radius;
            double z1 = Math.sin(angle1) * radius;
            double x2 = Math.cos(angle2) * radius;
            double z2 = Math.sin(angle2) * radius;

            // Línea 1
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)x1, 0, (float)z1)
                .color(r, g, b, a);

            // Línea 2
            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)x2, 0, (float)z2)
                .color(r, g, b, a);
        }

        matrices.pop();
    }

    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean isJumping = mc.player.getVelocity().y > 0.15 && !mc.player.isOnGround();
        if (wasJumping && !isJumping) {
            addCircle(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
        wasJumping = isJumping;
    }

    public void addCircle(double x, double y, double z) {
        circles.add(new CircleInstance(x, y, z, lifetime.get()));
    }

    private class CircleInstance {
        private final double x, y, z, lifespan;
        private double age;

        public CircleInstance(double x, double y, double z, double lifespan) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lifespan = lifespan;
            this.age = 0;
        }

        public void update() {
            age++;
        }

        public double getProgress() {
            return Math.min(1.0, age / lifespan);
        }

        public double getAge() {
            return age;
        }

        public boolean isExpired() {
            return age >= lifespan;
        }

        public Vec3d getPosition() {
            return new Vec3d(x, y, z);
        }
    }
}
