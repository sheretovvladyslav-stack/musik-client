package com.vtxn;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny key/value config stored in <instance>/vtxn/config.properties */
public final class Config {
    private static final Properties PROPS = new Properties();

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve("vtxn").resolve("config.properties");
    }

    public static void load() {
        try (InputStream in = Files.newInputStream(file())) {
            PROPS.load(in);
        } catch (IOException ignored) {
            // first launch: no file yet
        }
    }

    public static String get(String key, String def) {
        return PROPS.getProperty(key, def);
    }

    public static void set(String key, String value) {
        PROPS.setProperty(key, value);
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                PROPS.store(out, "V_Txn Client");
            }
        } catch (IOException e) {
            System.err.println("[V_Txn] Could not save config: " + e);
        }
    }
}
