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
    public enum Mode {
        NCP, StrictNCP, Matrix, Grim, MusteryGrief, GrimNew, Matrix2, LFCraft, Matrix3
    }

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

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> soulSand = sgGeneral.add(new BoolSetting.Builder()
        .name("soul-sand")
        .description("No slow on soul sand")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> honey = sgGeneral.add(new BoolSetting.Builder()
        .name("honey")
        .description("No slow on honey")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> slime = sgGeneral.add(new BoolSetting.Builder()
        .name("slime")
        .description("No slow on slime")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> ice = sgGeneral.add(new BoolSetting.Builder()
        .name("ice")
        .description("No slow on ice")
        .defaultValue(true)
        .build()
    );

    private final meteordevelopment.meteorclient.settings.Setting<Boolean> sweetBerryBush = sgGeneral.add(new BoolSetting.Builder()
        .name("sweet-berry-bush")
        .description("No slow on sweet berry bush")
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

    private boolean returnSneak;

    public NoSlow() {
        super(Categories.Movement, "NoSlow", "No slowdown when using items");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (returnSneak) {
            mc.options.sneakKey.setPressed(false);
            mc.player.setSprinting(true);
            returnSneak = false;
        }

        if (mc.player.isUsingItem() && !mc.player.isRiding()) {
            switch (mode.get()) {
                case StrictNCP:
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                    break;
                case MusteryGrief:
                    if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
                        mc.options.sneakKey.setPressed(true);
                        returnSneak = true;
                    }
                    break;
                case Grim:
                    if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                    } else if (mainHand.get()) {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    }
                    break;
                case Matrix:
                    if (mc.player.isOnGround() && !mc.options.jumpKey.isPressed()) {
                        mc.player.setVelocity(mc.player.getVelocity().x * 0.3, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.3);
                    } else if (mc.player.fallDistance > 0.2f) {
                        mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
                    }
                    break;
                case GrimNew:
                    if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                    } else if (mainHand.get() && (mc.player.getItemUseTime() <= 3 || mc.player.age % 2 == 0)) {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                    }
                    break;
                case Matrix2:
                    if (mc.player.isOnGround()) {
                        if (mc.player.age % 2 == 0) {
                            mc.player.setVelocity(mc.player.getVelocity().x * 0.5f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.5f);
                        } else {
                            mc.player.setVelocity(mc.player.getVelocity().x * 0.95f, mc.player.getVelocity().y, mc.player.getVelocity().z * 0.95f);
                        }
                    }
                    break;
                case LFCraft:
                    if (mc.player.getItemUseTime() <= 3) {
                        sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, mc.player.getBlockPos().up(), Direction.NORTH, id));
                    }
                    break;
                case NCP:
                default:
                    break;
            }
        }
    }

    private void sendSequencedPacket(java.util.function.Function<Integer, net.minecraft.network.packet.Packet<?>> packetFactory) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(packetFactory.apply(0));
        }
    }

    public boolean canNoSlow() {
        if (mode.get() == Mode.Matrix3)
            return false;

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
}
