package com.ivoryk.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * AutoRefill mejorado:
 * - Funciona correctamente dentro del inventario
 * - Llena slots vacíos de la hotbar con pociones y sopas
 * - Respeta objetos protegidos (espada, manzana dorada, tótem, cristales)
 */
public class AutoRefill extends Module {
    public AutoRefill() {
        super(Categories.Player, "AutoRefill", "Auto-refill hotbar slots cuando el inventario está abierto.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enableSoups = sgGeneral.add(new BoolSetting.Builder()
        .name("soups")
        .description("Rellenar sopas desde el inventario a la hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enablePotions = sgGeneral.add(new BoolSetting.Builder()
        .name("potions")
        .description("Rellenar pociones desde el inventario a la hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> respectProtected = sgGeneral.add(new BoolSetting.Builder()
        .name("respect-protected")
        .description("No mover objetos protegidos (espada, manzana dorada, tótem, cristales).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> refillDelay = sgGeneral.add(new IntSetting.Builder()
        .name("refill-delay")
        .description("Delay (ticks) entre acciones de relleno.")
        .defaultValue(3)
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
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.getInventory() == null) return;
        if (!(mc.currentScreen instanceof HandledScreen)) return;

        tickCounter++;
        if (tickCounter < Math.max(1, refillDelay.get())) return;
        tickCounter = 0;

        // Verificar hotbar (0-8)
        for (int hotbarSlot = 0; hotbarSlot <= 8; hotbarSlot++) {
            ItemStack hotbarStack = mc.player.getInventory().getStack(hotbarSlot);

            // Si está lleno o protegido, saltar
            if (!hotbarStack.isEmpty()) {
                if (isProtected(hotbarStack)) continue;
            }

            // Si está vacío, buscar en el inventario principal (9-35)
            if (hotbarStack.isEmpty()) {
                int sourceSlot = findItemToRefill();
                if (sourceSlot != -1) {
                    performSwap(hotbarSlot, sourceSlot);
                    return; // Una acción por tick
                }
            }
        }
    }

    private int findItemToRefill() {
        // Buscar en el inventario principal (9-35)
        for (int invSlot = 9; invSlot <= 35; invSlot++) {
            ItemStack stack = mc.player.getInventory().getStack(invSlot);
            if (stack == null || stack.isEmpty()) continue;

            Item item = stack.getItem();
            
            if (enablePotions.get() && isPotionItem(item)) {
                return invSlot;
            }
            if (enableSoups.get() && isSoupItem(item)) {
                return invSlot;
            }
        }
        return -1;
    }

    private boolean isPotionItem(Item item) {
        return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private boolean isSoupItem(Item item) {
        return item == Items.MUSHROOM_STEW || item == Items.SUSPICIOUS_STEW || item == Items.BEETROOT_SOUP;
    }

    private void performSwap(int hotbarSlot, int invSlot) {
        try {
            int syncId = mc.player.playerScreenHandler.syncId;
            // Click en el slot del inventario
            mc.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.PICKUP, mc.player);
            // Click en el slot de la hotbar
            mc.interactionManager.clickSlot(syncId, hotbarSlot, 0, SlotActionType.PICKUP, mc.player);
        } catch (Throwable ignored) {
        }
    }

    private boolean isProtected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!respectProtected.get()) return false;
        
        Item item = stack.getItem();
        return item == Items.DIAMOND_SWORD || 
               item == Items.NETHERITE_SWORD ||
               item == Items.IRON_SWORD ||
               item == Items.GOLDEN_APPLE ||
               item == Items.TOTEM_OF_UNDYING ||
               item == Items.END_CRYSTAL;
    }
}
