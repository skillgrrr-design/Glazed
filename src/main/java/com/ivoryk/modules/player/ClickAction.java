package com.ivoryk.modules.player;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class ClickAction extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enderpearlEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enderpearl-enabled")
        .description("Enable automatic enderpearl throw when bound key is pressed. No default keyset.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> enderKey = sgGeneral.add(new IntSetting.Builder()
        .name("ender-keycode")
        .description("Keycode for enderpearl action (-1 = none)")
        .defaultValue(-1)
        .min(-1)
        .max(350)
        .visible(enderpearlEnabled::get)
        .build()
    );

    private final Setting<Boolean> expEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("exp-enabled")
        .description("Enable automatic exp bottle throw when bound key is pressed. No default keyset.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> expKey = sgGeneral.add(new IntSetting.Builder()
        .name("exp-keycode")
        .description("Keycode for exp action (-1 = none)")
        .defaultValue(-1)
        .min(-1)
        .max(350)
        .visible(expEnabled::get)
        .build()
    );

    private final Setting<Integer> expClicksPerSecond = sgGeneral.add(new IntSetting.Builder()
        .name("exp-cps")
        .description("Clicks per second for exp bottles (14-18)")
        .defaultValue(15)
        .min(10)
        .max(20)
        .visible(expEnabled::get)
        .build()
    );

    private long lastExpThrow = 0;

    public ClickAction() {
        super(Categories.Player, "ClickAction", "Click-based actions: Enderpearl (X) and Exp bottles (Z)");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Handle Enderpearl (bound key)
        if (enderpearlEnabled.get() && enderKey.get() != -1 && isKeyPressed(enderKey.get())) {
            throwEnderpearl();
        }

        // Handle Exp (bound key) with CPS control
        if (expEnabled.get() && expKey.get() != -1 && isKeyPressed(expKey.get())) {
            long now = System.currentTimeMillis();
            long delayMs = 1000 / expClicksPerSecond.get();
            if (now - lastExpThrow >= delayMs) {
                throwExpBottle();
                lastExpThrow = now;
            }
        }
    }

    private boolean isKeyPressed(int key) {
        try {
            return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void throwEnderpearl() {
        if (mc.player == null || mc.interactionManager == null) return;

        // Check if player has enderpearl
        boolean hasEnderpearl = mc.player.getInventory().main.stream()
            .anyMatch(stack -> stack.getItem() == Items.ENDER_PEARL);

        if (!hasEnderpearl) return;

        try {
            // Find enderpearl slot and switch
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                    mc.player.getInventory().selectedSlot = i;
                    // Throw it
                    mc.player.swingHand(Hand.MAIN_HAND);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private void throwExpBottle() {
        if (mc.player == null || mc.interactionManager == null) return;

        // Check if player has exp bottle
        boolean hasExpBottle = mc.player.getInventory().main.stream()
            .anyMatch(stack -> stack.getItem() == Items.EXPERIENCE_BOTTLE);

        if (!hasExpBottle) return;

        try {
            // Find exp bottle slot and switch
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == Items.EXPERIENCE_BOTTLE) {
                    mc.player.getInventory().selectedSlot = i;
                    // Throw it
                    mc.player.swingHand(Hand.MAIN_HAND);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }
}
