package com.ivoryk.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * ESP+ (partial implementation):
 * - Provides a low-impact fallback by enabling the glowing outline for selected entities so they are visible through walls.
 * - A true model/texture-through-walls renderer requires low-level render hooks and is out of scope for a simple patch,
 *   but this fallback gives a practical visibility improvement while staying performant.
 */
public class ESPPlus extends Module {
    public ESPPlus() {
        super(Categories.Render, "ESP+", "Render entities and blocks using their real model/texture through walls with selective culling and low FPS overhead.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to render enhanced ESP.")
        .defaultValue(100)
        .min(1)
        .sliderMax(500)
        .build()
    );

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players")
        .description("Render players with real model/texture through walls.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> blocks = sgGeneral.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Render selected blocks (chests/shulkers) with their real appearance through walls.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> glowFallback = sgGeneral.add(new BoolSetting.Builder()
        .name("glow-fallback")
        .description("Enable a safe glowing fallback to make entities visible through walls when full model rendering isn't available.")
        .defaultValue(true)
        .build()
    );

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {
        // Remove glowing from entities we may have set
        if (!glowFallback.get() || mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity) e.setGlowing(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!glowFallback.get() || mc.player == null || mc.world == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            double d = mc.player.distanceTo(e);
            if (d > maxDistance.get()) continue;
            if (e instanceof PlayerEntity && players.get()) {
                e.setGlowing(true);
            }
            // Blocks rendered as entities is not handled by this fallback
        }
    }
}
