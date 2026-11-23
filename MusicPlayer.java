package com.vtxn;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Decodes mp3 files with JLayer and plays them through javax.sound. */
public final class MusicPlayer {
    public static final MusicPlayer INSTANCE = new MusicPlayer();

    private final List<Path> queue = new ArrayList<>();
    private final AtomicInteger generation = new AtomicInteger();

    private volatile Path current;
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean loop;
    private volatile float volume = 0.5f;
    private volatile long positionMs;
    private volatile long durationMs;

    private MusicPlayer() {}

    // ---------- public API ----------

    public synchronized void play(Path path) {
        int gen = generation.incrementAndGet();
        current = path;
        playing = true;
        paused = false;
        positionMs = 0;
        durationMs = 0;
        Thread t = new Thread(() -> run(gen, path), "VTxn-Player");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        generation.incrementAndGet();
        playing = false;
        paused = false;
        positionMs = 0;
    }

    public void togglePause() {
        if (playing) paused = !paused;
    }

    /** Skip to the next queued song, or stop if the queue is empty. */
    public void next() {
        if (!playNextFromQueue()) stop();
    }

    /** Adds to the queue; starts playing right away if nothing is playing. */
    public void enqueue(Path path) {
        synchronized (queue) {
            queue.add(path);
        }
        if (!playing) playNextFromQueue();
    }

    /**
     * Adds every given song to the queue in a fresh random order (songs already
     * queued are skipped). Starts playing right away if nothing is playing.
     * Returns how many songs were added.
     */
    public int enqueueAllShuffled(List<Path> songs) {
        List<Path> shuffled = new ArrayList<>(songs);
        Collections.shuffle(shuffled);
        int added = 0;
        synchronized (queue) {
            for (Path p : shuffled) {
                if (!queue.contains(p)) {
                    queue.add(p);
                    added++;
                }
            }
        }
        if (!playing) playNextFromQueue();
        return added;
    }

    public List<Path> queueSnapshot() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    public int queueSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public void removeFromQueue(int index) {
        synchronized (queue) {
            if (index >= 0 && index < queue.size()) queue.remove(index);
        }
    }

    public void clearQueue() {
        synchronized (queue) {
            queue.clear();
        }
    }

    public Path current() { return current; }
    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isLoop() { return loop; }
    public void setLoop(boolean loop) { this.loop = loop; }
    public float volume() { return volume; }
    public void setVolume(float v) { this.volume = Math.max(0f, Math.min(1f, v)); }
    public long positionMs() { return positionMs; }
    public long durationMs() { return durationMs; }

    // ---------- internals ----------

    private boolean playNextFromQueue() {
        Path next;
        synchronized (queue) {
            next = queue.isEmpty() ? null : queue.remove(0);
        }
        if (next == null) return false;
        play(next);
        return true;
    }

    private void run(int gen, Path start) {
        Path path = start;
        try {
            while (gen == generation.get()) {
                boolean failed = false;
                try {
                    decodeAndPlay(gen, path);
                } catch (Exception e) {
                    failed = true;
                    System.err.println("[V_Txn] Failed to play " + path + ": " + e);
                }
                if (gen != generation.get()) return;
                if (loop && !failed && positionMs > 0) {
                    positionMs = 0;
                    continue;
                }
                Path next;
                synchronized (queue) {
                    next = queue.isEmpty() ? null : queue.remove(0);
                }
                if (next == null) break;
                path = next;
                current = next;
                positionMs = 0;
                durationMs = 0;
            }
        } finally {
            synchronized (this) {
                if (gen == generation.get()) {
                    playing = false;
                    paused = false;
                    positionMs = 0;
                }
            }
        }
    }

    private void decodeAndPlay(int gen, Path path) throws Exception {
        long size = Files.size(path);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path), 1 << 16)) {
            Bitstream bs = new Bitstream(in);
            Decoder decoder = new Decoder();
            SourceDataLine line = null;
            boolean lineStopped = false;
            double pos = 0;
            try {
                Header h = bs.readFrame();
                if (h != null) {
                    durationMs = (long) h.total_ms((int) Math.min(size, Integer.MAX_VALUE));
                }
                while (h != null && gen == generation.get()) {
                    SampleBuffer buf = (SampleBuffer) decoder.decodeFrame(h, bs);

                    if (line == null) {
                        AudioFormat fmt = new AudioFormat(decoder.getOutputFrequency(), 16,
                                decoder.getOutputChannels(), true, false);
                        line = AudioSystem.getSourceDataLine(fmt);
                        line.open(fmt);
                        line.start();
                    }

                    while (paused && gen == generation.get()) {
                        if (!lineStopped) {
                            line.stop();
                            lineStopped = true;
                        }
                        Thread.sleep(40);
                    }
                    if (lineStopped && gen == generation.get()) {
                        line.start();
                        lineStopped = false;
                    }
                    if (gen != generation.get()) break;

                    short[] samples = buf.getBuffer();
                    int n = buf.getBufferLength();
                    float vol = volume;
                    byte[] out = new byte[n * 2];
                    for (int i = 0; i < n; i++) {
                        int v = (int) (samples[i] * vol);
                        out[i * 2] = (byte) v;
                        out[i * 2 + 1] = (byte) (v >> 8);
                    }
                    line.write(out, 0, out.length);

                    pos += h.ms_per_frame();
                    positionMs = (long) pos;

                    bs.closeFrame();
                    h = bs.readFrame();
                }
                if (line != null && gen == generation.get()) line.drain();
            } finally {
                if (line != null) {
                    line.stop();
                    line.close();
                }
                try {
                    bs.close();
                } catch (BitstreamException ignored) {
                }
            }
        }
    }
}
