package com.vtxn;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VtxnClient implements ClientModInitializer {
    public static final String MOD_ID = "vtxn";

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        // Creates <instance>/vtxn/music on first launch
        MusicLibrary.ensureFolder();

        Config.load();
        Themes.loadFromConfig();
        MusicPlayer.INSTANCE.setVolume(parseFloat(Config.get("volume", "0.5"), 0.5f));
        MusicPlayer.INSTANCE.setLoop(Boolean.parseBoolean(Config.get("loop", "false")));

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vtxn.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MusicScreen());
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MusicPlayer.INSTANCE.stop());
    }

    private static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
