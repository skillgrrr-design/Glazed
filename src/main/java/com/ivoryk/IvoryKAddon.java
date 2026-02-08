package com.ivoryk;

import com.ivoryk.modules.combat.*;
import com.ivoryk.modules.movement.*;
import com.ivoryk.modules.player.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;

public class IvoryKAddon extends MeteorAddon {

    public static int MyScreenVERSION = 15;

    @Override
    public void onInitialize() {
        Modules.get().add(new TriggerBot());
        Modules.get().add(new AimAssist());
        Modules.get().add(new com.ivoryk.modules.misc.AutoGG());
        Modules.get().add(new NoSlow());
        Modules.get().add(new ClickAction());
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        MyScreen.checkVersionOnServerJoin();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        MyScreen.resetSessionCheck();
    }

    @Override
    public String getPackage() {
        return "com.ivoryk";
    }

}
