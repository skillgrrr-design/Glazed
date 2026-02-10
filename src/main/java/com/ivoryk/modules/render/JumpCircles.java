package com.ivoryk.modules.render;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * JumpCircles Module - Renderiza círculos al saltar
 * Estilo Thunderhack con fallback render profesional en OpenGL
 * Compatible con Minecraft 1.21.4 + Fabric + Meteor Client
 */
public class JumpCircles extends Module {
    private static final Logger LOG = LoggerFactory.getLogger(JumpCircles.class);

    public JumpCircles() {
        super(Categories.Render, "Jump Circles", "Renderiza círculos deslumbrantes al saltar con efecto profesional Thunderhack");
    }

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Color");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> colorRed = sgColor.add(new IntSetting.Builder()
        .name("red")
        .description("Rojo del círculo (0-255)")
        .defaultValue(100)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Integer> colorGreen = sgColor.add(new IntSetting.Builder()
        .name("green")
        .description("Verde del círculo (0-255)")
        .defaultValue(200)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Integer> colorBlue = sgColor.add(new IntSetting.Builder()
        .name("blue")
        .description("Azul del círculo (0-255)")
        .defaultValue(255)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Integer> colorAlpha = sgColor.add(new IntSetting.Builder()
        .name("alpha")
        .description("Opacidad del círculo (0-255)")
        .defaultValue(128)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Double> maxRadius = sgRender.add(new DoubleSetting.Builder()
        .name("max-radius")
        .description("Radio máximo del círculo")
        .defaultValue(3.0)
        .min(0.5)
        .max(10.0)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<Double> expandSpeed = sgRender.add(new DoubleSetting.Builder()
        .name("expand-speed")
        .description("Velocidad de expansión del círculo")
        .defaultValue(0.1)
        .min(0.01)
        .max(0.5)
        .sliderMax(0.5)
        .build()
    );

    private final Setting<Double> thickness = sgRender.add(new DoubleSetting.Builder()
        .name("thickness")
        .description("Grosor del anillo")
        .defaultValue(0.15)
        .min(0.05)
        .max(0.5)
        .sliderMax(0.5)
        .build()
    );

    private final Setting<Boolean> fadeOut = sgRender.add(new BoolSetting.Builder()
        .name("fade-out")
        .description("Desvanecimiento suave del círculo")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> lifetime = sgRender.add(new DoubleSetting.Builder()
        .name("lifetime")
        .description("Tiempo de vida del círculo en ticks")
        .defaultValue(20.0)
        .min(5.0)
        .max(100.0)
        .sliderMax(100.0)
        .build()
    );

    // ==================== STATE ====================
    private final List<CircleInstance> circles = Collections.synchronizedList(new ArrayList<>());
    private boolean wasJumping = false;

    // ==================== EVENT HANDLERS ====================
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (circles.isEmpty()) return;

        synchronized (circles) {
            circles.removeIf(CircleInstance::isExpired);

            for (CircleInstance circle : circles) {
                circle.update();
                renderCircle(circle);
            }
        }
    }

    /**
     * Renderiza círculo con anti-aliasing usando OpenGL
     */
    private void renderCircle(CircleInstance circle) {
        try {
            Vec3d pos = circle.getPosition();
            double progress = circle.getProgress();
            double radius = maxRadius.get() * progress;

            // Calcular opacidad con fade out
            float opacity = 1.0f;
            if (fadeOut.get()) {
                double age = circle.getAge();
                double life = lifetime.get();
                opacity = Math.max(0, 1.0f - (float)(age / life));
            }

            Color renderColor = new Color(
                colorRed.get(),
                colorGreen.get(),
                colorBlue.get(),
                (int)(colorAlpha.get() * opacity)
            );

            // Renderizar círculo con SDF effect emulado
            renderCircleWithGlow(pos.x, pos.y + 0.01, pos.z, radius, 32, thickness.get(), renderColor);

        } catch (Exception e) {
            LOG.error("Error rendering jump circle", e);
        }
    }

    /**
     * Emula efecto SDF usando múltiples círculos concéntricos para Glow
     */
    private void renderCircleWithGlow(double x, double y, double z, double radius, int segments, double thickness, Color color) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        // Habilitar blending para el efecto de glow
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // Glow externo (más oscuro, más grande)
        float glowAlpha = color.a / 255f * 0.3f;
        GL11.glColor4f(
            color.r / 255f * 0.8f,
            color.g / 255f * 0.8f,
            color.b / 255f * 0.8f,
            glowAlpha
        );
        renderCircleOutline(radius + thickness, segments, thickness * 2);

        // Círculo principal (más brillante)
        GL11.glColor4f(
            color.r / 255f,
            color.g / 255f,
            color.b / 255f,
            color.a / 255f
        );
        renderCircleOutline(radius, segments, thickness);

        // Núcleo interno (anti-aliasing suave)
        GL11.glColor4f(
            color.r / 255f,
            color.g / 255f,
            color.b / 255f,
            color.a / 255f * 0.5f
        );
        renderCircleOutline(radius - thickness / 2, segments, thickness / 2);

        // Restaurar estado
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glLineWidth(1.0f);
        GL11.glPopMatrix();
    }

    /**
     * Renderiza un outline de círculo usando líneas
     */
    private void renderCircleOutline(double radius, int segments, double lineWidth) {
        GL11.glLineWidth((float) lineWidth);
        GL11.glBegin(GL11.GL_LINE_LOOP);

        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            double px = Math.cos(angle) * radius;
            double pz = Math.sin(angle) * radius;
            GL11.glVertex3d(px, 0, pz);
        }

        GL11.glEnd();
    }

    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) {
        // Detectar saltos por cambio de análisis de velocidad vertical
        if (mc.player != null) {
            boolean isJumping = mc.player.getVelocity().y > 0.15 && !mc.player.isOnGround();
            
            // Detectar transición de saltando a no saltando
            if (wasJumping && !isJumping) {
                addCircle(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            }
            wasJumping = isJumping;
        }
    }

    // ==================== HELPER METHODS ====================
    public void addCircle(double x, double y, double z) {
        circles.add(new CircleInstance(x, y, z, lifetime.get()));
    }

    // ==================== INNER CLASSES ====================

    /**
     * Representa una instancia de círculo en el mundo
     */
    private class CircleInstance {
        private final double x, y, z;
        private final double lifespan;
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
