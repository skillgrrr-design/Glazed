package com.ivoryk.modules.misc;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Random;

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

    private final Setting<Boolean> randomSuffix = sgGeneral.add(new BoolSetting.Builder()
        .name("random-suffix")
        .description("Add random characters at the end to avoid spam detection")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> randomLength = sgGeneral.add(new IntSetting.Builder()
        .name("random-length")
        .description("Length of random characters to append")
        .defaultValue(4)
        .min(1)
        .sliderMax(10)
        .visible(() -> randomSuffix.get())
        .build()
    );

    private final Map<String, Long> scheduledMessages = new HashMap<>(); // nombre -> timestamp
    private final Random random = new Random();

    public AutoGG() {
        super(Categories.Player, "AutoGG", "Sends a chat message when you kill a player with optional random suffix.");
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
                
                // Agregar sufijo aleatorio si está habilitado
                if (randomSuffix.get()) {
                    ggMessage += " " + generateRandomString(randomLength.get());
                }
                
                try {
                    mc.getNetworkHandler().sendChatMessage(ggMessage);
                } catch (Throwable ignored) {}
                return true;
            }
            return false;
        });
    }

    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
