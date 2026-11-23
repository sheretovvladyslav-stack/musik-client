package com.vtxn;

/** A colour theme. Everything is derived from a background colour and an accent colour. */
public final class Theme {
    public final String name;
    public final String desc;
    public final boolean glass;

    public final int window, sidebar, panel;
    public final int button, buttonHover, selected, border;
    public final int text, dim, accentFull, barBg;
    public final int cardFill, cardBorder;
    public final int[] swatches;

    public Theme(String name, String desc, boolean glass, int bg, int accent) {
        this.name = name;
        this.desc = desc;
        this.glass = glass;

        int dark = mix(bg, 0x000000, 0.35f);
        window = argb(glass ? 0xC8 : 0xFF, bg);
        sidebar = argb(glass ? 0xD8 : 0xFF, dark);
        panel = argb(glass ? 0xB0 : 0xFF, mix(bg, accent, 0.07f));
        button = argb(0xFF, mix(bg, accent, 0.14f));
        buttonHover = argb(0xFF, mix(bg, accent, 0.28f));
        selected = argb(0xFF, mix(bg, accent, 0.38f));
        border = argb(0xFF, mix(bg, accent, 0.45f));
        int txt = mix(0xF2F2F7, accent, 0.10f);
        text = argb(0xFF, txt);
        dim = argb(0xFF, mix(txt, bg, 0.55f));
        accentFull = argb(0xFF, accent);
        barBg = argb(0xFF, mix(bg, accent, 0.18f));
        cardFill = argb(0xFF, mix(bg, accent, 0.08f));
        cardBorder = argb(0xFF, mix(bg, accent, 0.22f));
        swatches = new int[] {
                argb(0xFF, dark),
                argb(0xFF, bg),
                argb(0xFF, accent),
                argb(0xFF, mix(accent, bg, 0.5f)),
                argb(0xFF, mix(bg, accent, 0.14f))
        };
    }

    private static int argb(int a, int rgb) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
