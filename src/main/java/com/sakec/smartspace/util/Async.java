package com.sakec.smartspace.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;

/** Runs database work off the JavaFX thread and delivers results back on it. */
public final class Async {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "smartspace-worker");
        t.setDaemon(true);
        return t;
    });

    private Async() { }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (!(ex instanceof AppException)) Log.error("Background task failed", ex);
            onFailure.accept(ex);
        });
        POOL.execute(task);
    }

    public static void fx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run(); else Platform.runLater(r);
    }

    public static void shutdown() { POOL.shutdownNow(); }
}
