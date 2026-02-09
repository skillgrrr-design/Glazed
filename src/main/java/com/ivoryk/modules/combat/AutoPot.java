package com.ivoryk.modules.combat;

import com.ivoryk.utils.InventoryUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;

public class AutoPot extends Module {
    public AutoPot() {
        super(Categories.Combat, "AutoPot", "Automatically use healing potions with smooth rotation and configurable thresholds.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> healthThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("health-threshold")
        .description("Health threshold below which the module will attempt to use a healing potion.")
        .defaultValue(10)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> hotbarOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("hotbar-only")
        .description("Only use potions found in the hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Smooth rotation speed when throwing potions (higher is faster).")
        .defaultValue(8)
        .min(0.1)
        .sliderMax(20)
        .build()
    );

    private int useDelay = 0;
    private int targetSlot = -1; // hotbar slot to use
    private float targetPitch = 90f; // look down

    @Override
    public void onActivate() {
        useDelay = 0;
        targetSlot = -1;
    }

    @Override
    public void onDeactivate() {
        useDelay = 0;
        targetSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Only attempt when health below threshold
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (health > healthThreshold.get()) return;

        // Cooldown
        if (useDelay > 0) {
            useDelay--;
            return;
        }

        // find potion in hotbar
        int potionSlot = -1;
        for (int i = 0; i <= 8; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (st != null && !st.isEmpty() && st.getItem() == Items.POTION) {
                potionSlot = i;
                break;
            }
        }

        if (potionSlot == -1 && hotbarOnly.get()) return;

        if (potionSlot == -1 && !hotbarOnly.get()) {
            // try to find in inventory and move to first empty hotbar slot
            int found = -1;
            for (int s = 9; s <= 35; s++) {
                ItemStack st = mc.player.getInventory().getStack(s - 9 + 9);
                if (st != null && !st.isEmpty() && st.getItem() == Items.POTION) {
                    found = s;
                    break;
                }
            }
            if (found == -1) return;
            // move to first empty hotbar
            int empty = -1;
            for (int h = 0; h <= 8; h++) if (mc.player.getInventory().getStack(h).isEmpty()) { empty = h; break; }
            if (empty == -1) return;
            try {
                int syncId = mc.player.playerScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, found, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, empty, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, found, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                potionSlot = empty;
            } catch (Throwable ignored) { return; }
        }

        if (potionSlot == -1) return;

        // Smooth rotation towards looking down
        float currentPitch = mc.player.getPitch();
        float newPitch = currentPitch;
        float diff = targetPitch - currentPitch;
        float step = (float) Math.max(0.5, Math.min(10.0, rotationSpeed.get())) / 10f;
        if (Math.abs(diff) > 1f) {
            newPitch = currentPitch + Math.signum(diff) * step;
            mc.player.setPitch(newPitch);
            return; // wait until rotation finishes
        }

        // select slot and use potion
        try {
            InventoryUtils.setSelectedSlot(mc.player.getInventory(), potionSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
            mc.player.swingHand(Hand.MAIN_HAND);
            useDelay = 20; // small cooldown
        } catch (Throwable ignored) {}
    }

    @Override
    public void onActivate() {
        // Initialize state
    }

    @Override
    public void onDeactivate() {
        // Reset state
    }

    // Implementation note: perform smooth interpolation of player's pitch to simulate natural throw,
    // check incoming damage and simple prediction before using potions. Avoid instant snaps.
}
