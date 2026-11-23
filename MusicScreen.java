package com.vtxn;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicScreen extends Screen {
    private enum Tab { LIBRARY, NOW, QUEUE, THEMES }

    /** How a vanilla button gets repainted with the current theme. */
    private enum Kind { BUTTON, TAB, ROW, CARD }

    private record Styled(ButtonWidget w, Kind kind, boolean selected, Theme theme) {}

    private static Tab lastTab = Tab.LIBRARY;

    private static final int W_MAX = 460;
    private static final int H_MAX = 250;

    private final MusicPlayer player = MusicPlayer.INSTANCE;

    private Tab tab = lastTab;
    private List<Path> tracks = new ArrayList<>();
    private Path selected;
    private String filter = "";
    private int scroll;
    private String status = "";
    private boolean dirty;
    private int lastQueueSize = -1;

    private final List<Styled> styled = new ArrayList<>();
    private ButtonWidget loopButton;
    private ButtonWidget pauseButton;

    private int px, py, mx, mw, ww, hh;

    public MusicScreen() {
        super(Text.literal("V_Txn Client"));
        this.tracks = MusicLibrary.scan();
        this.selected = player.current();
    }

    // ---------- layout ----------

    @Override
    protected void init() {
        ww = Math.min(W_MAX, width - 8);
        hh = Math.min(H_MAX, height - 8);
        px = (width - ww) / 2;
        py = (height - hh) / 2;
        mx = px + 108;
        mw = ww - 116;
        styled.clear();
        pauseButton = null;

        // sidebar
        int sy = py + 40;
        tabButton("Library", Tab.LIBRARY, sy);
        tabButton("Now", Tab.NOW, sy + 24);
        tabButton("Queue", Tab.QUEUE, sy + 48);
        tabButton("Themes", Tab.THEMES, sy + 72);
        style(ButtonWidget.builder(Text.literal("Open folder"), b -> openFolder())
                .dimensions(px + 8, sy + 100, 84, 20).build(), Kind.BUTTON, false, null);

        // bottom bar
        int by = py + hh - 28;
        style(ButtonWidget.builder(Text.literal("Play"), b -> onPlay())
                .dimensions(mx, by, 46, 20).build(), Kind.BUTTON, false, null);
        style(ButtonWidget.builder(Text.literal("Stop"), b -> player.stop())
                .dimensions(mx + 50, by, 46, 20).build(), Kind.BUTTON, false, null);
        style(ButtonWidget.builder(Text.literal("Queue"), b -> onEnqueue())
                .dimensions(mx + 100, by, 46, 20).build(), Kind.BUTTON, false, null);
        style(ButtonWidget.builder(Text.literal("Queue all"), b -> onEnqueueAll())
                .dimensions(mx + 150, by, 62, 20).build(), Kind.BUTTON, false, null);
        loopButton = ButtonWidget.builder(loopLabel(), b -> {
            player.setLoop(!player.isLoop());
            b.setMessage(loopLabel());
        }).dimensions(mx + 216, by, 70, 20).build();
        style(loopButton, Kind.BUTTON, false, null);
        style(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(mx + mw - 46, by, 46, 20).build(), Kind.BUTTON, false, null);

        // tab-specific widgets
        if (tab == Tab.LIBRARY) {
            TextFieldWidget search = new TextFieldWidget(textRenderer, mx, py + 22, mw - 60, 18, Text.literal("Search"));
            search.setPlaceholder(Text.literal("Search local tracks"));
            search.setText(filter);
            search.setChangedListener(s -> {
                filter = s;
                scroll = 0;
                dirty = true;
            });
            addDrawableChild(search);
            style(ButtonWidget.builder(Text.literal("Reload"), b -> {
                tracks = MusicLibrary.scan();
                status = "Found " + tracks.size() + " track(s)";
                dirty = true;
            }).dimensions(mx + mw - 56, py + 22, 56, 18).build(), Kind.BUTTON, false, null);
        } else if (tab == Tab.QUEUE) {
            style(ButtonWidget.builder(Text.literal("Clear queue"), b -> {
                player.clearQueue();
                dirty = true;
            }).dimensions(mx, py + 22, 90, 18).build(), Kind.BUTTON, false, null);
        } else if (tab == Tab.NOW) {
            pauseButton = ButtonWidget.builder(Text.literal("Pause"), b -> player.togglePause())
                    .dimensions(mx, py + 84, 80, 20).build();
            style(pauseButton, Kind.BUTTON, false, null);
            style(ButtonWidget.builder(Text.literal("Next"), b -> player.next())
                    .dimensions(mx + 84, py + 84, 80, 20).build(), Kind.BUTTON, false, null);
            addDrawableChild(new VolumeSlider(mx, py + 112, mw, 20));
        }

        rebuildRows();
    }

    private ButtonWidget style(ButtonWidget w, Kind kind, boolean selected, Theme theme) {
        styled.add(new Styled(w, kind, selected, theme));
        return addDrawableChild(w);
    }

    private void tabButton(String label, Tab t, int y) {
        String text = (tab == t ? "> " : "") + label;
        style(ButtonWidget.builder(Text.literal(text), b -> switchTab(t))
                .dimensions(px + 8, y, 84, 20).build(), Kind.TAB, tab == t, null);
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        scroll = 0;
        clearChildren();
        init();
    }

    private Text loopLabel() {
        return Text.literal("Loop: " + (player.isLoop() ? "On" : "Off"));
    }

    private List<Path> filtered() {
        if (filter.isBlank()) return tracks;
        String f = filter.toLowerCase(Locale.ROOT);
        List<Path> out = new ArrayList<>();
        for (Path p : tracks) {
            if (MusicLibrary.displayName(p).toLowerCase(Locale.ROOT).contains(f)) out.add(p);
        }
        return out;
    }

    private void rebuildRows() {
        styled.removeIf(s -> {
            if (s.kind() == Kind.ROW || s.kind() == Kind.CARD) {
                remove(s.w());
                return true;
            }
            return false;
        });
        if (tab == Tab.NOW) return;

        if (tab == Tab.THEMES) {
            buildThemeCards();
            return;
        }

        int top = py + 46;
        int rowH = 18;
        int visible = Math.max(1, (py + hh - 56 - top) / rowH);

        List<Path> src = tab == Tab.LIBRARY ? filtered() : player.queueSnapshot();
        lastQueueSize = player.queueSize();
        int max = Math.max(0, src.size() - visible);
        scroll = Math.max(0, Math.min(scroll, max));

        for (int i = 0; i < visible && scroll + i < src.size(); i++) {
            final int idx = scroll + i;
            final Path p = src.get(idx);
            String name = MusicLibrary.displayName(p);
            boolean sel = tab == Tab.LIBRARY && p.equals(selected);
            String label = (tab == Tab.QUEUE ? (idx + 1) + ". " : "") + name;
            label = textRenderer.trimToWidth(label, mw - 12);
            ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> onRowClick(idx, p))
                    .dimensions(mx, top + i * rowH, mw, 16).build();
            style(b, Kind.ROW, sel, null);
        }
    }

    private void buildThemeCards() {
        final int cols = 3;
        final int gap = 4;
        int cw = (mw - gap * (cols - 1)) / cols;
        int ch = 32;
        int top = py + 24;
        int rowsVisible = Math.max(1, (py + hh - 56 - top + gap) / (ch + gap));
        int totalRows = (Themes.ALL.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows - rowsVisible)));

        for (int r = 0; r < rowsVisible; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (scroll + r) * cols + c;
                if (idx >= Themes.ALL.size()) return;
                final Theme t = Themes.ALL.get(idx);
                ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> {
                    Themes.set(t);
                    status = "Theme: " + t.name;
                }).dimensions(mx + c * (cw + gap), top + r * (ch + gap), cw, ch).build();
                style(b, Kind.CARD, false, t);
            }
        }
    }

    // ---------- actions ----------

    private void onRowClick(int index, Path p) {
        if (tab == Tab.LIBRARY) {
            selected = p;
        } else if (tab == Tab.QUEUE) {
            player.removeFromQueue(index);
            status = "Removed from queue";
        }
        dirty = true;
    }

    private void onPlay() {
        boolean selectedIsCurrent = selected != null && selected.equals(player.current());
        if (tab == Tab.LIBRARY && selected != null && !(selectedIsCurrent && player.isPlaying())) {
            player.play(selected);
        } else if (player.isPaused()) {
            player.togglePause();
        } else if (!player.isPlaying() && player.current() != null) {
            player.play(player.current());
        } else if (!player.isPlaying() && selected != null) {
            player.play(selected);
        }
    }

    private void onEnqueue() {
        if (selected == null) {
            status = "Select a song first";
            return;
        }
        player.enqueue(selected);
        status = "Queued: " + MusicLibrary.displayName(selected);
        if (tab == Tab.QUEUE) dirty = true;
    }

    private void onEnqueueAll() {
        if (tracks.isEmpty()) {
            status = "No songs to queue";
            return;
        }
        int added = player.enqueueAllShuffled(tracks);
        status = "Queued " + added + " song(s) shuffled";
        if (tab == Tab.QUEUE) dirty = true;
    }

    private void openFolder() {
        MusicLibrary.ensureFolder();
        Util.getOperatingSystem().open(MusicLibrary.folder());
        status = "Opened music folder";
    }

    @Override
    public void removed() {
        Config.set("volume", Float.toString(player.volume()));
        Config.set("loop", Boolean.toString(player.isLoop()));
        super.removed();
    }

    // ---------- rendering ----------

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // intentionally empty: we draw our own dimmed backdrop in render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (tab == Tab.QUEUE && player.queueSize() != lastQueueSize) dirty = true;
        if (dirty) {
            dirty = false;
            rebuildRows();
        }
        if (pauseButton != null) pauseButton.setMessage(Text.literal(player.isPaused() ? "Resume" : "Pause"));

        drawChrome(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        drawStyled(ctx, mouseX, mouseY);
    }

    private void drawChrome(DrawContext ctx) {
        Theme th = Themes.current();

        ctx.fill(0, 0, width, height, 0x88000000);
        ctx.fill(px, py, px + ww, py + hh, th.window);
        ctx.fill(px, py, px + 100, py + hh, th.sidebar);
        // outline
        ctx.fill(px, py, px + ww, py + 1, th.accentFull);
        ctx.fill(px, py + hh - 1, px + ww, py + hh, th.border);
        ctx.fill(px, py, px + 1, py + hh, th.border);
        ctx.fill(px + ww - 1, py, px + ww, py + hh, th.border);
        ctx.fill(mx - 4, py + 18, mx + mw + 4, py + hh - 56, th.panel);

        ctx.drawTextWithShadow(textRenderer, "V_Txn Client", px + 10, py + 8, th.accentFull);
        ctx.drawTextWithShadow(textRenderer, th.name + " theme", px + 10, py + 20, th.dim);

        String header = switch (tab) {
            case LIBRARY -> "Library";
            case NOW -> "Now playing";
            case QUEUE -> "Queue (click a song to remove it)";
            case THEMES -> "Themes (" + Themes.ALL.size() + ")";
        };
        ctx.drawTextWithShadow(textRenderer, header, mx, py + 8, th.text);
        if (!status.isEmpty()) {
            int sw = textRenderer.getWidth(status);
            ctx.drawTextWithShadow(textRenderer, status, mx + mw - sw, py + 8, th.dim);
        }

        // bottom: current track + state
        Path cur = player.current();
        String name = cur == null ? "Nothing playing" : MusicLibrary.displayName(cur);
        String state = !player.isPlaying() ? "Stopped" : player.isPaused() ? "Paused" : "Playing";
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, mw), mx, py + hh - 52, th.text);
        ctx.drawTextWithShadow(textRenderer, state, mx, py + hh - 42, th.dim);

        if (tab == Tab.LIBRARY && filtered().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "No mp3 files found.", mx + 6, py + 52, th.dim);
            ctx.drawTextWithShadow(textRenderer, "Click 'Open folder', drop mp3s in, then 'Reload'.", mx + 6, py + 64, th.dim);
        } else if (tab == Tab.QUEUE && player.queueSize() == 0) {
            ctx.drawTextWithShadow(textRenderer, "Queue is empty.", mx + 6, py + 52, th.dim);
        }

        if (tab == Tab.NOW) {
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, mw), mx, py + 28, th.accentFull);
            long pos = player.positionMs();
            long dur = player.durationMs();
            ctx.drawTextWithShadow(textRenderer, fmt(pos) + " / " + (dur > 0 ? fmt(dur) : "--:--"), mx, py + 42, th.text);
            int barY = py + 58;
            ctx.fill(mx, barY, mx + mw, barY + 6, th.barBg);
            if (dur > 0) {
                int filled = (int) (mw * Math.min(1.0, (double) pos / dur));
                ctx.fill(mx, barY, mx + filled, barY + 6, th.accentFull);
            }
            ctx.drawTextWithShadow(textRenderer, "Loop: " + (player.isLoop() ? "On" : "Off")
                    + "   Queue: " + player.queueSize() + " song(s)", mx, py + 70, th.dim);
        }
    }

    /** Repaints every styled vanilla button on top of itself using the current theme. */
    private void drawStyled(DrawContext ctx, int mouseX, int mouseY) {
        Theme th = Themes.current();
        for (Styled s : styled) {
            ButtonWidget b = s.w();
            int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            String label = b.getMessage().getString();

            switch (s.kind()) {
                case BUTTON -> {
                    boolean on = b == loopButton && player.isLoop();
                    box(ctx, x, y, w, h, on ? th.accentFull : th.border,
                            on ? th.selected : hover ? th.buttonHover : th.button);
                    int tw = textRenderer.getWidth(label);
                    ctx.drawTextWithShadow(textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, th.text);
                }
                case TAB -> {
                    ctx.fill(x, y, x + w, y + h, s.selected() ? th.selected : hover ? th.buttonHover : th.button);
                    if (s.selected()) ctx.fill(x, y, x + 2, y + h, th.accentFull);
                    ctx.drawTextWithShadow(textRenderer, label, x + 8, y + (h - 8) / 2,
                            s.selected() ? th.accentFull : th.text);
                }
                case ROW -> {
                    ctx.fill(x, y, x + w, y + h, s.selected() ? th.selected : hover ? th.buttonHover : th.button);
                    if (s.selected()) ctx.fill(x, y, x + 2, y + h, th.accentFull);
                    ctx.drawTextWithShadow(textRenderer, label, x + 8, y + (h - 8) / 2,
                            s.selected() ? th.accentFull : th.text);
                }
                case CARD -> drawCard(ctx, s.theme(), x, y, w, h, hover);
            }
        }
    }

    private void drawCard(DrawContext ctx, Theme t, int x, int y, int w, int h, boolean hover) {
        boolean current = t == Themes.current();
        box(ctx, x, y, w, h, current ? t.accentFull : hover ? t.border : t.cardBorder, t.cardFill);

        // palette strip
        int sw = (w - 6) / t.swatches.length;
        for (int i = 0; i < t.swatches.length; i++) {
            ctx.fill(x + 3 + i * sw, y + 3, x + 3 + (i + 1) * sw, y + 10, t.swatches[i]);
        }

        ctx.drawTextWithShadow(textRenderer, t.name, x + 4, y + 13, current ? t.accentFull : t.text);
        if (t.glass) {
            int gw = textRenderer.getWidth("GLASS");
            ctx.drawTextWithShadow(textRenderer, "GLASS", x + w - gw - 4, y + 13, t.accentFull);
        }
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(t.desc, w - 8), x + 4, y + 23, t.dim);
    }

    private static void box(DrawContext ctx, int x, int y, int w, int h, int border, int fill) {
        ctx.fill(x, y, x + w, y + h, border);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab != Tab.NOW) {
            scroll -= (int) Math.signum(verticalAmount);
            dirty = true;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ---------- volume slider ----------

    private static class VolumeSlider extends SliderWidget {
        VolumeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), MusicPlayer.INSTANCE.volume());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Volume: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            MusicPlayer.INSTANCE.setVolume((float) value);
        }
    }
}
