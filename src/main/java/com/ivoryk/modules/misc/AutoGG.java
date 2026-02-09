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

import java.util.HashMap;
import java.util.Map;
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

    private final Map<String, Long> scheduledMessages = new HashMap<>(); // nombre -> timestamp

    public AutoGG() {
        super(Categories.Player, "AutoGG", "Sends a chat message when you kill a player.");
    }

    @Override
    public void onDeactivate() {
        scheduledMessages.clear();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;
        
        String message = event.getMessage().getString().toLowerCase();
        String playerName = mc.player.getName().getString().toLowerCase();
        
        // Detectar muerte donde nosotros matamos
        String victim = detectKill(message, playerName);
        if (victim != null && !victim.isEmpty()) {
            scheduleGGMessage(victim);
        }
    }

    private String detectKill(String message, String playerName) {
        // Patrones de muerte comunes
        String[] patterns = {
            "(.+?) fue asesinado por " + playerName,
            "(.+?) was killed by " + playerName,
            "(.+?) was slain by " + playerName,
            "(.+?) was shot by " + playerName,
            "(.+?) fue muerto por " + playerName
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(message);
            
            if (m.find()) {
                return m.group(1).trim();
            }
        }

        return null;
    }

    private void scheduleGGMessage(String victimName) {
        long executeTime = System.currentTimeMillis() + delay.get();
        scheduledMessages.put(victimName, executeTime);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        final long now = System.currentTimeMillis();
        scheduledMessages.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                String victimName = entry.getKey();
                String ggMessage = message.get().replace("{name}", victimName);
                try {
                    mc.getNetworkHandler().sendChatMessage(ggMessage);
                } catch (Throwable ignored) {}
                return true;
            }
            return false;
        });
    }
}
