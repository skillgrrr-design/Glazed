package com.ivoryk.modules.misc;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.entity.player.PlayerEntity;
 

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoGG extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
        .name("message")
        .description("Message to send when you kill a player. Use {name} for the victim's name.")
        .defaultValue("GG {name}")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ms")
        .description("Delay in milliseconds after a kill to send the message")
        .defaultValue(1000)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Map<UUID, Boolean> alive = new HashMap<>();
    private final Map<UUID, Boolean> lastAttackedByLocal = new HashMap<>();
    private final Map<UUID, Long> scheduled = new HashMap<>();

    public AutoGG() {
        super(Categories.Player, "AutoGG", "Sends a chat message when you kill a player.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // scan players
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            boolean aliveNow = p.isAlive();
            boolean wasAlive = alive.getOrDefault(id, true);

            // attempt to detect whether last attacker was local player via reflection
            if (aliveNow) {
                try {
                    Method m = p.getClass().getMethod("getLastAttacker");
                    Object attacker = m.invoke(p);
                    lastAttackedByLocal.put(id, attacker == mc.player);
                } catch (Throwable ignored) {
                }
            }

            if (wasAlive && !aliveNow) {
                boolean attacked = lastAttackedByLocal.getOrDefault(id, false);
                if (attacked) {
                    scheduled.put(id, System.currentTimeMillis() + delay.get());
                }
            }

            alive.put(id, aliveNow);
        }

        // send scheduled messages
        if (!scheduled.isEmpty() && mc.player != null) {
            long now = System.currentTimeMillis();
            scheduled.entrySet().removeIf(e -> {
                if (e.getValue() <= now) {
                    // lookup name
                    String name = null;
                    for (PlayerEntity p : mc.world.getPlayers()) {
                        if (p.getUuid().equals(e.getKey())) {
                            name = p.getName().getString();
                            break;
                        }
                    }
                    if (name == null) name = "player";
                    String out = message.get().replace("{name}", name);
                    try {
                        if (mc.player != null && mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatMessage(out);
                    } catch (Throwable ignored) {}
                    return true;
                }
                return false;
            });
        }
    }
}
