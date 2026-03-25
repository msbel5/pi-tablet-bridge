package com.pitabletbridge;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

public class MainActivity extends Activity implements TransportManager.Callbacks, FrameDecodeWorker.Callbacks, TouchForwarder.Callbacks {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ImageView frameView;
    private TextView statusText;
    private TextView metricsText;
    private Button keyboardButton;

    private TransportManager transportManager;
    private FrameDecodeWorker frameDecodeWorker;
    private KeyboardDialogController keyboardDialogController;
    private TouchForwarder touchForwarder;

    private Bitmap currentBitmap;
    private int currentFrameWidth = 640;
    private int currentFrameHeight = 360;
    private long metricsWindowStartedMs = SystemClock.uptimeMillis();
    private int metricsFrames;
    private long lastLatencyMs;
    private long lastDecodeMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        frameView = (ImageView) findViewById(R.id.frameView);
        statusText = (TextView) findViewById(R.id.statusText);
        metricsText = (TextView) findViewById(R.id.metricsText);
        keyboardButton = (Button) findViewById(R.id.keyboardButton);

        transportManager = new TransportManager(this);
        frameDecodeWorker = new FrameDecodeWorker(this);
        keyboardDialogController = new KeyboardDialogController(this, new KeyboardDialogController.Listener() {
            @Override
            public void onCommand(KeyCommand command) {
                transportManager.sendKey(command);
            }
        });
        touchForwarder = new TouchForwarder(this, this);
        touchForwarder.setFrameSize(currentFrameWidth, currentFrameHeight);
        frameView.setOnTouchListener(touchForwarder);

        keyboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                keyboardDialogController.show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        frameDecodeWorker.start();
        transportManager.start();
    }

    @Override
    protected void onStop() {
        transportManager.stop();
        frameDecodeWorker.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        super.onDestroy();
    }

    @Override
    public void onStatusChanged(final String status, String transport) {
        final String metrics = transport == null ? "Waiting for frames" : "Transport: " + transport.toUpperCase();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                statusText.setText(status);
                metricsText.setText(metrics);
            }
        });
    }

    @Override
    public void onFrame(final FramePacket framePacket) {
        currentFrameWidth = framePacket.width;
        currentFrameHeight = framePacket.height;
        touchForwarder.setFrameSize(currentFrameWidth, currentFrameHeight);
        frameDecodeWorker.submit(framePacket);
    }

    @Override
    public void onConfig(final JSONObject config) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                int width = config.optInt("screen_width", 0);
                int height = config.optInt("screen_height", 0);
                if (width > 0 && height > 0) {
                    statusText.setText("Connected " + width + "x" + height);
                }
            }
        });
    }

    @Override
    public void onFrameDecoded(final Bitmap bitmap, final FramePacket framePacket, final long decodeTimeMs) {
        lastLatencyMs = Math.max(0L, System.currentTimeMillis() - framePacket.timestampMs);
        lastDecodeMs = decodeTimeMs;
        metricsFrames++;
        long now = SystemClock.uptimeMillis();
        long windowMs = now - metricsWindowStartedMs;
        final int fps = windowMs > 0 ? (int) Math.round((metricsFrames * 1000.0d) / (double) windowMs) : 0;
        if (windowMs >= 1000L) {
            metricsWindowStartedMs = now;
            metricsFrames = 0;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (currentBitmap != null && !currentBitmap.isRecycled()) {
                    currentBitmap.recycle();
                }
                currentBitmap = bitmap;
                frameView.setImageBitmap(bitmap);
                metricsText.setText("FPS " + fps + " | latency " + lastLatencyMs + " ms | decode " + lastDecodeMs + " ms");
            }
        });
    }

    @Override
    public void onGesture(String action, float normalizedX, float normalizedY, int pointerCount) {
        transportManager.sendTouch(action, normalizedX, normalizedY, pointerCount);
    }
}

