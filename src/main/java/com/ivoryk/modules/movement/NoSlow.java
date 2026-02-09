package com.ivoryk.modules.movement;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;

public class NoSlow extends Module {
    // Public modes exposed to users
    public enum Mode {
        NCP,
        StrictNCP,
        Matrix,
        Grim,
        MusteryGrief,
        GrimNew,
        Matrix2,
        LFCraft
    }

    // --- Configuration / settings -------------------------------------------------
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final meteordevelopment.meteorclient.settings.Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("NoSlow mode")
        .defaultValue(Mode.NCP)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> mainHand = sgGeneral.add(new BoolSetting.Builder()
        .name("main-hand")
        .description("Apply effect to main hand")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> food = sgGeneral.add(new BoolSetting.Builder()
        .name("food")
        .description("No slow for food")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> projectiles = sgGeneral.add(new BoolSetting.Builder()
        .name("projectiles")
        .description("No slow for projectiles")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> shield = sgGeneral.add(new BoolSetting.Builder()
        .name("shield")
        .description("No slow for shield")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak")
        .description("No slow while sneaking")
        .defaultValue(false)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> crawl = sgGeneral.add(new BoolSetting.Builder()
        .name("crawl")
        .description("No slow while crawling")
        .defaultValue(false)
        .build()
    );

    // toggle to restore the original aggressive bypass behavior
    private final meteordevelopment.meteorclient.settings.Setting<Boolean> aggressiveBypass = sgGeneral.add(new BoolSetting.Builder()
        .name("aggressive-bypass")
        .description("Enable original aggressive bypass behaviors (packets/velocity). WARNING: more detectable")
        .defaultValue(true)
        .build()
    );

    private boolean returnSneak;

    // --- Constants ----------------------------------------------------------------
    private static final int SEQUENCED_PACKET_COUNT = 3;

    public NoSlow() {
        super(Categories.Movement, "NoSlow", "No slowdown when using items");
    }

    // --- Main event handler ------------------------------------------------------
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (returnSneak) {
            // restore sneak state previously set
            mc.options.sneakKey.setPressed(false);
            mc.player.setSprinting(true);
            returnSneak = false;
        }

        if (!canNoSlow()) return; // central validation gate

        // delegate to per-mode handlers for clarity
        switch (mode.get()) {
            case NCP:
                handleNCP();
                break;
            case StrictNCP:
                handleStrictNCP();
                break;
            case MusteryGrief:
                handleMusteryGrief();
                break;
            case Grim:
                handleGrim();
                break;
            case Matrix:
                handleMatrix();
                break;
            case GrimNew:
                handleGrimNew();
                break;
            case Matrix2:
                handleMatrix2();
                break;
            case LFCraft:
                handleLFCraft();
                break;
            default:
                break;
        }
    }

    // --- Mode handlers ----------------------------------------------------------
    private void handleNCP() {
        // NCP mode: aggressive packet-based approach
        if (aggressiveBypass.get()) {
            // Send slot change packets every tick
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            
            // Additional: interact with off-hand to further confuse detection
            if (mc.player.age % 2 == 0) {
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
            }
        } else {
            // Conservative approach: only send every 4 ticks
            if (mc.player.age % 4 == 0) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            }
        }
    }

    private void handleStrictNCP() {
        if (aggressiveBypass.get()) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
        else if (mc.player.age % 4 == 0) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
    }

    private void handleMusteryGrief() {
        if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
            mc.options.sneakKey.setPressed(true);
            returnSneak = true;
        }
    }

    private void handleGrim() {
        if (aggressiveBypass.get()) {
            if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            } else if (mainHand.get()) {
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
            }
        } else {
            if (mainHand.get()) sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        }
    }

    private void handleMatrix() {
        if (aggressiveBypass.get()) {
            if (mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                mc.player.setVelocity(mc.player.getVelocity().x * 0.3, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.3);
            } else if (mc.player.fallDistance > 0.2f) {
                mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
            }
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
        }
    }

    private void handleGrimNew() {
        if (aggressiveBypass.get()) {
            if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            } else if (mainHand.get() && (mc.player.getItemUseTime() <= 3 || mc.player.age % 2 == 0)) {
                sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
            }
        } else {
            if (mainHand.get() && mc.player.getItemUseTime() <= 3) sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        }
    }

    private void handleMatrix2() {
        if (aggressiveBypass.get()) {
            if (mc.player.isOnGround()) {
                if (mc.player.age % 2 == 0) {
                    mc.player.setVelocity(mc.player.getVelocity().x * 0.5f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.5f);
                } else {
                    mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
                }
            }
        } else {
            mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
        }
    }

    private void handleLFCraft() {
        if (mc.player.getItemUseTime() <= 3) {
            if (aggressiveBypass.get()) sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, mc.player.getBlockPos().up(), Direction.NORTH, id));
        }
    }

    // --- Utilities ---------------------------------------------------------------
    private void sendSequencedPacket(java.util.function.Function<Integer, net.minecraft.network.packet.Packet<?>> packetFactory) {
        if (mc.getNetworkHandler() == null) return;
        if (aggressiveBypass.get()) {
            for (int i = 0; i < SEQUENCED_PACKET_COUNT; i++) mc.getNetworkHandler().sendPacket(packetFactory.apply(i));
        } else {
            mc.getNetworkHandler().sendPacket(packetFactory.apply(0));
        }
    }

    /**
     * Central validation for whether NoSlow should run this tick.
     * Keeps the checks in one place so future refactors (e.g. mixins) can call this.
     */
    public boolean canNoSlow() {
        if (mc.player == null || mc.world == null) return false;

        // basic use checks
        if (!mc.player.isUsingItem() || mc.player.isRiding()) return false;

        if (mc.player.getActiveItem().isEmpty()) return false;

        if (!food.get() && mc.player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD))
            return false;

        if (!shield.get() && mc.player.getActiveItem().getItem() == Items.SHIELD)
            return false;

        if (!projectiles.get()
                && (mc.player.getActiveItem().getItem() == Items.CROSSBOW
                || mc.player.getActiveItem().getItem() == Items.BOW
                || mc.player.getActiveItem().getItem() == Items.TRIDENT))
            return false;

        if (mode.get() == Mode.MusteryGrief && mc.player.isOnGround() && !mc.options.jumpKey.isPressed())
            return false;

        if (!mainHand.get() && mc.player.getActiveHand() == Hand.MAIN_HAND)
            return false;

        if ((mc.player.getOffHandStack().getComponents().contains(DataComponentTypes.FOOD)
                || mc.player.getOffHandStack().getItem() == Items.SHIELD)
                && (mode.get() == Mode.GrimNew || mode.get() == Mode.Grim)
                && mc.player.getActiveHand() == Hand.MAIN_HAND)
            return false;

        return true;
    }

    /*
     * Important note (technical):
     * This module manipulates client-side state (packets, local velocity, keys).
     * On server-authoritative setups the server still controls final movement.
     * A reliable NoSlow requires intervening in the movement calculation (e.g. mixin into
     * ClientPlayerEntity#travel / LivingEntity#travel or inject into input processing),
     * which is beyond this module-only approach. See evaluation and next-steps.
     */
}
