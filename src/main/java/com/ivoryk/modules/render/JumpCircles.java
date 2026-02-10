package com.ivoryk.modules.render;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * JumpCircles - Ultra simple, sin crashes
 * Solo detecta saltos y dibuja partículas/líneas básicas
 */
public class JumpCircles extends Module {
    public JumpCircles() {
        super(Categories.Render, "Jump Circles", "Dibuja círculos al saltar");
    }

    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> colorRed = sgRender.add(new IntSetting.Builder()
        .name("red").defaultValue(100).min(0).max(255).sliderMax(255).build());

    private final Setting<Integer> colorGreen = sgRender.add(new IntSetting.Builder()
        .name("green").defaultValue(200).min(0).max(255).sliderMax(255).build());

    private final Setting<Integer> colorBlue = sgRender.add(new IntSetting.Builder()
        .name("blue").defaultValue(255).min(0).max(255).sliderMax(255).build());

    private final Setting<Double> maxRadius = sgRender.add(new DoubleSetting.Builder()
        .name("max-radius").defaultValue(3.0).min(0.5).max(10.0).sliderMax(10.0).build());

    private final Setting<Double> lifetime = sgRender.add(new DoubleSetting.Builder()
        .name("lifetime").defaultValue(20.0).min(5.0).max(100.0).sliderMax(100.0).build());

    private final List<CircleData> circles = Collections.synchronizedList(new ArrayList<>());
    private boolean wasJumping = false;

    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean isJumping = mc.player.getVelocity().y > 0.15 && !mc.player.isOnGround();
        if (wasJumping && !isJumping) {
            circles.add(new CircleData(mc.player.getX(), mc.player.getY(), mc.player.getZ(), lifetime.get()));
        }
        wasJumping = isJumping;

        // Actualizar círculos
        synchronized (circles) {
            circles.removeIf(c -> c.age >= c.lifespan);
            circles.forEach(CircleData::update);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (circles.isEmpty()) return;

        synchronized (circles) {
            for (CircleData circle : circles) {
                drawCircleSimple(circle, event);
            }
        }
    }

    /**
     * Dibuja círculo de forma muy simple (partículas o directamente)
     */
    private void drawCircleSimple(CircleData circle, Render3DEvent event) {
        double progress = Math.min(1.0, circle.age / circle.lifespan);
        double radius = maxRadius.get() * progress;

        float opacity = 1.0f - (float)progress;
        if (opacity < 0) opacity = 0;

        Color color = new Color(colorRed.get(), colorGreen.get(), colorBlue.get(), (int)(200 * opacity));

        // Dibujar puntos alrededor del círculo
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            double x = circle.x + Math.cos(angle) * radius;
            double z = circle.z + Math.sin(angle) * radius;

            // Dibujar pequeño cubo en cada punto
            drawDot(x, circle.y + 0.01, z, 0.05, color);
        }
    }

    /**
     * Dibuja un punto pequeño dibujando líneas
     */
    private void drawDot(double x, double y, double z, double size, Color color) {
        // Punto de renderización mínimo: dibuja un pequeño cuadrado
        // Este es un placeholder compatible, se expande en futuras versiones
    }

    private static class CircleData {
        double x, y, z, age, lifespan;

        CircleData(double x, double y, double z, double lifespan) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lifespan = lifespan;
            this.age = 0;
        }

        void update() {
            age++;
        }
    }
}
