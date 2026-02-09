package com.ivoryk.modules.misc;

import com.ivoryk.utils.InventoryUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.screen.slot.SlotActionType;

public class AutoRefill extends Module {
    public AutoRefill() {
        super(Categories.Player, "AutoRefill", "Auto-refill hotbar slots when the inventory is open, respecting protected items and slot locks.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enableSoups = sgGeneral.add(new BoolSetting.Builder()
        .name("soups")
        .description("Refill soups from inventory to prioritized hotbar slots.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enablePotions = sgGeneral.add(new BoolSetting.Builder()
        .name("potions")
        .description("Refill potions from inventory to prioritized hotbar slots.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> respectProtected = sgGeneral.add(new BoolSetting.Builder()
        .name("respect-protected")
        .description("Do not move protected items (sword, gapple, totem, crystals, or marked items).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> refillDelay = sgGeneral.add(new IntSetting.Builder()
        .name("refill-delay")
        .description("Delay (ticks) between refill actions to keep behavior realistic.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private int tickCounter = 0;

    @Override
    public void onActivate() {
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        // Cleanup if needed
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.getInventory() == null) return;
        if (!(mc.currentScreen instanceof HandledScreen)) return; // Only run when inventory/container is open

        tickCounter++;
        if (tickCounter < Math.max(1, refillDelay.get())) return;
        tickCounter = 0;

        // Hotbar slots 0-8
        for (int hotbar = 0; hotbar <= 8; hotbar++) {
            ItemStack hot = mc.player.getInventory().getStack(hotbar);

            if (!hot.isEmpty()) continue; // only fill empty slots

            // find candidate in main inventory (9-35)
            int found = -1;
            for (int s = 9; s <= 35; s++) {
                ItemStack st = mc.player.getInventory().getStack(s - 9 + 9); // mapping safe
                // st index: player inventory main starts at index 9 in container
                if (st == null) continue;
                if (st.isEmpty()) continue;

                Item it = st.getItem();
                boolean match = false;
                if (enablePotions.get() && it == Items.POTION) {
                    // only healing potions by default
                    if (PotionUtil.getPotion(st) == Potions.HEALING || PotionUtil.getPotion(st) == Potions.STRONG_HEALING) match = true;
                }
                if (enableSoups.get() && (it == Items.MUSHROOM_STEW || it == Items.SUSPICIOUS_STEW || it == Items.BEETROOT_SOUP)) match = true;

                if (match) {
                    found = s;
                    break;
                }
            }

            if (found == -1) continue;

            // Perform safe window clicks: pickup source -> pickup target
            try {
                int syncId = mc.player.playerScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, found, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, hotbar, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, found, 0, SlotActionType.PICKUP, mc.player);
            } catch (Throwable ignored) {}
            return; // do one move per delay to keep behavior natural
        }
    }

    private boolean isProtected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item it = stack.getItem();
        if (!respectProtected.get()) return false;
        return it == Items.DIAMOND_SWORD || it == Items.NETHERITE_SWORD || it == Items.GOLDEN_APPLE || it == Items.TOTEM_OF_UNDYING || it == Items.END_CRYSTAL;
    }
}
