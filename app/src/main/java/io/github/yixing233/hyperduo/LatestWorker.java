/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One running job and one replaceable pending job; idle threads expire. */
final class LatestWorker {
    private final ThreadPoolExecutor executor;
    private Runnable pending;
    private boolean draining;

    LatestWorker(String name) {
        executor = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
        });
    }

    synchronized void submit(Runnable job) {
        pending = job;
        if (draining) return;
        draining = true;
        executor.execute(this::drain);
    }

    private void drain() {
        while (true) {
            Runnable job;
            synchronized (this) {
                job = pending; pending = null;
                if (job == null) { draining = false; return; }
            }
            try { job.run(); } catch (Throwable ignored) {}
        }
    }
}
