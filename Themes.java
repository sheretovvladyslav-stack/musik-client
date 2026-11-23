package com.vtxn;

import java.util.List;

public final class Themes {
    private Themes() {}

    public static final List<Theme> ALL = List.of(
            // glass
            new Theme("Vault", "Glass + violet, Prestige Client theme", true, 0x0E0F20, 0x7C6BFF),
            new Theme("Nova", "Synthwave cyan glow", true, 0x0A111C, 0x22D3EE),
            new Theme("Rose", "Pink couture", true, 0x1B0C15, 0xEC4899),
            new Theme("Ember", "Molten amber glow", true, 0x1B1009, 0xF97316),
            new Theme("Forest", "Emerald canopy", true, 0x0A150F, 0x10B981),
            new Theme("Indigo", "Twilight indigo", true, 0x0D0D22, 0x6366F1),
            new Theme("Magenta", "Neon orchid", true, 0x15091B, 0xD946EF),
            new Theme("Ocean", "Open-water sapphire", true, 0x08111D, 0x3B82F6),
            new Theme("Crimson", "Blood-velvet red", true, 0x1B0B0B, 0xEF4444),
            new Theme("Lime", "Radioactive lime", true, 0x101506, 0x84CC16),
            new Theme("Gold", "Luxury gilded glow", true, 0x18130A, 0xFBBF24),
            new Theme("Aurora", "Northern lights", true, 0x07171A, 0x5EEAD4),
            // solid
            new Theme("Carbon", "Pure neutral dark", false, 0x141416, 0xEEF0F6),
            new Theme("Slate", "Cool gray + electric blue", false, 0x161A24, 0x3B82F6),
            new Theme("Mocha", "Espresso brown + peach", false, 0x1B1511, 0xFDBA8C),
            new Theme("Cobalt", "Uniform deep blue", false, 0x0C1322, 0x4A86E8),
            new Theme("Mauve", "Dusty muted purple", false, 0x1A141D, 0xD0A2E8),
            new Theme("Sage", "Soft muted green", false, 0x111712, 0x8BC595),
            new Theme("Sand", "Beige paper + gold", false, 0x1B1712, 0xDDA95A),
            new Theme("Ink", "Jet black + cyan ink", false, 0x050509, 0x06B6D4),
            new Theme("Wine", "Burgundy + bronze", false, 0x1B1011, 0xDB8B4E),
            new Theme("Pewter", "Brushed steel", false, 0x12171B, 0xB6C8DA),
            new Theme("Olive", "Military olive", false, 0x14150C, 0xB5B54A),
            new Theme("Plum", "Regal deep plum", false, 0x18111D, 0x9050A8),
            new Theme("Brick", "Warm terracotta", false, 0x191210, 0xCB6A52)
    );

    private static volatile Theme current = ALL.get(0);

    public static Theme current() {
        return current;
    }

    public static void set(Theme t) {
        current = t;
        Config.set("theme", t.name);
    }

    public static void loadFromConfig() {
        String saved = Config.get("theme", "Vault");
        for (Theme t : ALL) {
            if (t.name.equalsIgnoreCase(saved)) {
                current = t;
                return;
            }
        }
    }
}
