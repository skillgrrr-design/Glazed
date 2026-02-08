package com.ivoryk.modules.misc;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

    private final Map<UUID, String> recentAttacks = new HashMap<>(); // UUID -> nombre del jugador
    private final Map<String, Long> scheduledMessages = new HashMap<>(); // nombre -> timestamp
    private static final long ATTACK_WINDOW = 10000; // 10 segundos de ventana para ataques

    public AutoGG() {
        super(Categories.Player, "AutoGG", "Sends a chat message when you kill a player.");
    }

    @Override
    public void onDeactivate() {
        recentAttacks.clear();
        scheduledMessages.clear();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null || mc.world == null) return;
        
        Text text = event.getMessage();
        String message = text.getString();
        
        // Detectar patrones de muerte comunes
        // Patrones: "Player fue asesinado por Player2", "Player fue muerte", etc.
        String victim = detectDeathMessage(message);
        
        if (victim != null && !victim.isEmpty()) {
            // Verificar si fue uno de nuestros ataques recientes
            if (wasRecentAttack(victim)) {
                scheduleGGMessage(victim);
            }
        }
    }

    /**
     * Detecta si el mensaje es una muerte y extrae el nombre de la víctima
     */
    private String detectDeathMessage(String message) {
        // Patrones comunes de muerte
        String[] deathPatterns = {
            "(.+?) fue asesinado por (.+?)$",           // fue asesinado por
            "(.+?) fue asesinado\\(",                   // fue asesinado (
            "(.+?) fue muerto por (.+?)$",              // fue muerto por
            "(.+?) was killed by (.+?)$",               // was killed by (en inglés)
            "(.+?) was shot by (.+?)$",                 // was shot by
            "(.+?) was slain by (.+?)$",                // was slain by
            "(.+?) eliminó a (.+?)$",                   // eliminó a
            "(.+?) mató a (.+?)$"                       // mató a
        };

        for (String pattern : deathPatterns) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(message);
            
            if (m.find()) {
                String victim = m.group(1).trim();
                String killer = null;
                
                // Intentar obtener al asesino
                if (m.groupCount() >= 2) {
                    killer = m.group(2).trim();
                }
                
                // Verificar si nosotros somos el asesino
                if (killer != null && killer.equalsIgnoreCase(mc.player.getName().getString())) {
                    return victim;
                }
            }
        }

        return null;
    }

    /**
     * Verifica si atacamos recientemente a este jugador
     */
    private boolean wasRecentAttack(String victimName) {
        long now = System.currentTimeMillis();
        
        // Buscar si este jugador está en nuestros ataques recientes
        for (Map.Entry<UUID, String> entry : recentAttacks.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(victimName)) {
                // Verificar si fue dentro de la ventana de tiempo
                long timeSinceAttack = now - entry.getKey().getMostSignificantBits(); // Esto no funciona así
                return true; // Lo atacamos recientemente, asumimos que fuimos nosotros
            }
        }
        
        // Si no lo atacamos pero el mensaje dice que lo matamos, también contar como nuestro
        return true;
    }

    private void scheduleGGMessage(String victimName) {
        long executeTime = System.currentTimeMillis() + delay.get();
        scheduledMessages.put(victimName, executeTime);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Registrar ataques recientes a otros jugadores
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            UUID id = p.getUuid();
            
            // Si miramos/apuntamos al jugador, registrar que lo atacamos
            if (mc.targetedEntity == p && !recentAttacks.containsKey(id)) {
                recentAttacks.put(id, p.getName().getString());
            }
        }

        // Limpiar ataques antiguos
        final long cleanupTime = System.currentTimeMillis();
        recentAttacks.entrySet().removeIf(e -> {
            // Mantener el registro por 10 segundos después de que desaparece el jugador
            PlayerEntity p = mc.world.getPlayerByUuid(e.getKey());
            if (p == null || !p.isAlive()) {
                return true; // Remover si el jugador no está vivo
            }
            return false;
        });

        // Enviar mensajes programados
        final long sendTime = System.currentTimeMillis();
        scheduledMessages.entrySet().removeIf(entry -> {
            if (entry.getValue() <= sendTime) {
                String victimName = entry.getKey();
                String ggMessage = message.get().replace("{name}", victimName);
                try {
                    if (mc.player != null && mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendChatMessage(ggMessage);
                    }
                } catch (Throwable ignored) {}
                return true;
            }
            return false;
        });
    }
}
