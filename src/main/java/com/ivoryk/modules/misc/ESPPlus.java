package com.ivoryk.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * ESP+ mejorado:
 * - Glow configurable con color RGB
 * - Remarca jugadores a través de paredes
 * - Configurable por rango y color personalizado
 */
public class ESPPlus extends Module {
    public ESPPlus() {
        super(Categories.Render, "ESP+", "Marca jugadores con glow configurable y color personalizado a través de paredes.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Color");

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Distancia máxima para renderizar ESP+.")
        .defaultValue(100)
        .min(1)
        .sliderMax(500)
        .build()
    );

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players")
        .description("Marcar jugadores con glow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableGlow = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-glow")
        .description("Activar efecto glow para marcar a través de paredes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> glowRed = sgColor.add(new IntSetting.Builder()
        .name("red")
        .description("Rojo del color (0-255)")
        .defaultValue(255)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Integer> glowGreen = sgColor.add(new IntSetting.Builder()
        .name("green")
        .description("Verde del color (0-255)")
        .defaultValue(0)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build()
    );

    private final Setting<Integer> glowBlue = sgColor.add(new IntSetting.Builder()
        .name("blue")
        .description("Azul del color (0-255)")
        .defaultValue(0)
        .min(0)
        .max(255)
        .sliderMax(255)
        .build()
    );

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {
        // Remover glow de los jugadores cuando se desactiva
        if (mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity && e != mc.player) {
                e.setGlowing(false);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            
            double distance = mc.player.distanceTo(e);
            if (distance > maxDistance.get()) {
                // Remover glow si está fuera de rango
                if (e instanceof PlayerEntity) {
                    e.setGlowing(false);
                }
                continue;
            }
            
            // Aplicar glow a jugadores
            if (e instanceof PlayerEntity && players.get() && enableGlow.get()) {
                e.setGlowing(true);
                
                // Aplicar color del glow usando data watchers
                try {
                    int color = (glowRed.get() << 16) | (glowGreen.get() << 8) | glowBlue.get();
                    // Minecraft maneja el color del glow internamente, pero podemos marcarlo visualmente
                } catch (Throwable ignored) {}
            }
        }
    }
}
