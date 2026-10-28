package com.sakec.smartspace.service;

import com.sakec.smartspace.util.Log;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.media.AudioClip;

/** Plays queued room-request sound sequences without blocking the JavaFX thread. */
public final class NotificationSound {
    private static final Set<Integer> PLAYED_REQUEST_IDS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "smartspace-notification-sound");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile AudioClip clip;
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean();
    private static boolean soundLoadAttempted;

    private NotificationSound() { }

    public static void playForRequest(int requestId) {
        if (SHUTDOWN.get() || !PLAYED_REQUEST_IDS.add(requestId)) return;
        try {
            PLAYER.execute(NotificationSound::playSequence);
        } catch (RejectedExecutionException e) {
            if (!SHUTDOWN.get()) Log.error("Could not queue room-request notification sound", e);
        }
    }

    private static void playSequence() {
        try {
            AudioClip sound = sound();
            if (sound == null) return;
            for (int playback = 0; playback < 3; playback++) {
                sound.play();
                while (sound.isPlaying()) Thread.sleep(25);
                if (playback < 2) Thread.sleep(700);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            Log.error("Could not play room-request notification sound", e);
        }
    }

    private static AudioClip sound() {
        AudioClip loaded = clip;
        if (loaded != null || soundLoadAttempted) return loaded;
        soundLoadAttempted = true;
        URL resource = NotificationSound.class.getResource("/sounds/room-request.wav");
        if (resource == null) {
            Log.warn("Room-request notification sound is missing: /sounds/room-request.wav");
            return null;
        }
        try {
            loaded = new AudioClip(resource.toExternalForm());
            clip = loaded;
            return loaded;
        } catch (RuntimeException e) {
            Log.error("Could not load room-request notification sound", e);
            return null;
        }
    }

    public static void shutdown() {
        if (!SHUTDOWN.compareAndSet(false, true)) return;
        PLAYER.shutdownNow();
        AudioClip loaded = clip;
        if (loaded != null) {
            loaded.stop();
            clip = null;
        }
        PLAYED_REQUEST_IDS.clear();
    }
}
