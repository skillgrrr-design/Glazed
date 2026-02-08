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
        .defaultValue(500)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final Map<UUID, String> alivePlayerNames = new HashMap<>();
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();
    private final Map<UUID, Long> scheduled = new HashMap<>();
    private static final long ATTACK_TIMEOUT = 5000; // 5 segundos

    public AutoGG() {
        super(Categories.Player, "AutoGG", "Sends a chat message when you kill a player.");
    }

    @Override
    public void onDeactivate() {
        alivePlayerNames.clear();
        lastAttackTime.clear();
        scheduled.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Rastrear jugadores actualmente vivos
        Map<UUID, String> currentLivePlayers = new HashMap<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.isAlive()) {
                currentLivePlayers.put(p.getUuid(), p.getName().getString());
            }
        }

        // Detectar muertes: jugadores que estaban vivos pero ya no están
        for (UUID uuid : alivePlayerNames.keySet()) {
            if (!currentLivePlayers.containsKey(uuid)) {
                // El jugador murió
                Long lastAttack = lastAttackTime.get(uuid);
                
                // Si lo atacamos en los últimos 5 segundos, es probable que nosotros lo matamos
                if (lastAttack != null && (System.currentTimeMillis() - lastAttack) < ATTACK_TIMEOUT) {
                    String victimName = alivePlayerNames.get(uuid);
                    scheduled.put(uuid, System.currentTimeMillis() + delay.get());
                }
                
                lastAttackTime.remove(uuid);
            }
        }

        // Actualizar lista de vivos
        alivePlayerNames.clear();
        alivePlayerNames.putAll(currentLivePlayers);

        // Registrar cuando atacamos a otros jugadores
        // Nota: Esta es una detección simple; se podría mejorar con eventos de daño
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            
            // Verificar si el objetivo de la cámara es este jugador (significa que miramos a este jugador)
            if (mc.targetedEntity == p) {
                lastAttackTime.put(id, System.currentTimeMillis());
            }
        }

        // Enviar mensajes programados
        if (!scheduled.isEmpty()) {
            long now = System.currentTimeMillis();
            scheduled.entrySet().removeIf(e -> {
                if (e.getValue() <= now) {
                    String victimName = alivePlayerNames.getOrDefault(e.getKey(), "player");
                    // Buscar nombre del jugador muerto
                    for (UUID uuid : alivePlayerNames.keySet()) {
                        if (uuid.equals(e.getKey())) {
                            victimName = alivePlayerNames.get(uuid);
                            break;
                        }
                    }
                    
                    String out = message.get().replace("{name}", victimName);
                    try {
                        if (mc.player != null && mc.getNetworkHandler() != null) {
                            mc.getNetworkHandler().sendChatMessage(out);
                        }
                    } catch (Throwable ignored) {}
                    return true;
                }
                return false;
            });
        }
    }
}
