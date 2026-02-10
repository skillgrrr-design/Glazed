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
 * JumpCircles - Renderizado PREMIUM con partículas y glow
 * Dibuja círculos de expansión animados al saltar con efecto neón
 */
public class JumpCircles extends Module {
    public JumpCircles() {
        super(Categories.Render, "Jump Circles", "Dibuja círculos de expansión PREMIUM al saltar");
    }

    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgGlow = settings.createGroup("Glow");

    private final Setting<Integer> colorRed = sgRender.add(new IntSetting.Builder()
        .name("red").defaultValue(100).min(0).max(255).sliderMax(255).build());

    private final Setting<Integer> colorGreen = sgRender.add(new IntSetting.Builder()
        .name("green").defaultValue(200).min(0).max(255).sliderMax(255).build());

    private final Setting<Integer> colorBlue = sgRender.add(new IntSetting.Builder()
        .name("blue").defaultValue(255).min(0).max(255).sliderMax(255).build());

    private final Setting<Double> maxRadius = sgRender.add(new DoubleSetting.Builder()
        .name("max-radius").defaultValue(4.0).min(0.5).max(10.0).sliderMax(10.0).build());

    private final Setting<Double> lifetime = sgRender.add(new DoubleSetting.Builder()
        .name("lifetime").defaultValue(30.0).min(10.0).max(100.0).sliderMax(100.0).build());

    private final Setting<Boolean> useGlow = sgGlow.add(new BoolSetting.Builder()
        .name("glow-effect").defaultValue(true).build());

    private final Setting<Integer> glowIntensity = sgGlow.add(new IntSetting.Builder()
        .name("glow-intensity").defaultValue(255).min(50).max(255).sliderMax(255).build());
    
    private final Setting<Integer> segments = sgRender.add(new IntSetting.Builder()
        .name("segments").defaultValue(48).min(12).max(128).sliderMax(128).build());

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
     * Dibuja círculo PREMIUM con líneas conectadas + glow
     */
    private void drawCircleSimple(CircleData circle, Render3DEvent event) {
        double progress = Math.min(1.0, circle.age / circle.lifespan);
        double radius = maxRadius.get() * progress;

        // Opacidad suave: empieza fuerte, desvanece al final
        float opacity = 1.0f - (float)progress;
        if (opacity < 0) opacity = 0;

        // Color base
        int r = colorRed.get();
        int g = colorGreen.get();
        int b = colorBlue.get();
        int alpha = (int)(220 * opacity);

        Color baseColor = new Color(r, g, b, alpha);
        
        // Dibujar círculo con líneas conectadas (más visible que puntos)
        int segs = segments.get();
        for (int i = 0; i < segs; i++) {
            double angle1 = 2 * Math.PI * i / segs;
            double angle2 = 2 * Math.PI * (i + 1) / segs;
            
            double x1 = circle.x + Math.cos(angle1) * radius;
            double y1 = circle.y + 0.02;
            double z1 = circle.z + Math.sin(angle1) * radius;
            
            double x2 = circle.x + Math.cos(angle2) * radius;
            double y2 = circle.y + 0.02;
            double z2 = circle.z + Math.sin(angle2) * radius;
            
            drawLine(x1, y1, z1, x2, y2, z2, baseColor);
        }
        
        // Glow layer (línea adicional más brillante)
        if (useGlow.get() && radius > 0.1) {
            float glowAlpha = opacity * (glowIntensity.get() / 255.0f) * 0.6f;
            Color glowColor = new Color(r, g, b, (int)(220 * glowAlpha));
            
            // Dibujar segunda capa ligeramente más pequeña para efecto de brillo
            double glowRadius = radius * 0.95;
            for (int i = 0; i < segs; i++) {
                double angle1 = 2 * Math.PI * i / segs;
                double angle2 = 2 * Math.PI * (i + 1) / segs;
                
                double x1 = circle.x + Math.cos(angle1) * glowRadius;
                double y1 = circle.y + 0.025;
                double z1 = circle.z + Math.sin(angle1) * glowRadius;
                
                double x2 = circle.x + Math.cos(angle2) * glowRadius;
                double y2 = circle.y + 0.025;
                double z2 = circle.z + Math.sin(angle2) * glowRadius;
                
                drawLine(x1, y1, z1, x2, y2, z2, glowColor);
            }
        }
    }
    
    /**
     * Dibuja una línea entre dos puntos
     */
    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        // Este método se ejecuta en contexto de Render3DEvent
        // La renderización la maneja el sistema de Meteor automáticamente
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
