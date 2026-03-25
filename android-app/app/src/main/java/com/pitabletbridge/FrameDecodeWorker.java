package com.pitabletbridge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;

public final class FrameDecodeWorker {
    public interface Callbacks {
        void onFrameDecoded(Bitmap bitmap, FramePacket framePacket, long decodeTimeMs);
    }

    private final Object lock = new Object();
    private final Callbacks callbacks;
    private volatile boolean running;
    private Thread workerThread;
    private FramePacket pendingFrame;

    public FrameDecodeWorker(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "frame-decode");
        workerThread.start();
    }

    public void stop() {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        if (workerThread != null) {
            try {
                workerThread.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void submit(FramePacket framePacket) {
        synchronized (lock) {
            pendingFrame = framePacket;
            lock.notifyAll();
        }
    }

    private void loop() {
        while (running) {
            FramePacket framePacket;
            synchronized (lock) {
                while (running && pendingFrame == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running) {
                    return;
                }
                framePacket = pendingFrame;
                pendingFrame = null;
            }

            long started = SystemClock.uptimeMillis();
            Bitmap bitmap = BitmapFactory.decodeByteArray(framePacket.jpegBytes, 0, framePacket.jpegBytes.length);
            long decodeTimeMs = SystemClock.uptimeMillis() - started;
            if (bitmap != null) {
                callbacks.onFrameDecoded(bitmap, framePacket, decodeTimeMs);
            }
        }
    }
}

