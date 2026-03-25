package com.pitabletbridge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

public final class TouchForwarder implements View.OnTouchListener {
    public interface Callbacks {
        void onGesture(String action, float normalizedX, float normalizedY, int pointerCount);
    }

    private static final long LONG_PRESS_MS = 450L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dragThresholdPx;
    private final Callbacks callbacks;

    private int frameWidth;
    private int frameHeight;
    private GeometryHelper.NormalizedPoint startPoint;
    private GeometryHelper.NormalizedPoint lastPoint;
    private boolean active;
    private boolean dragStarted;
    private boolean longPressTriggered;
    private int pointerCount;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (active && !dragStarted && !longPressTriggered && lastPoint != null) {
                callbacks.onGesture("secondary_tap", lastPoint.x, lastPoint.y, pointerCount);
                longPressTriggered = true;
                active = false;
            }
        }
    };

    public TouchForwarder(Context context, Callbacks callbacks) {
        this.callbacks = callbacks;
        this.dragThresholdPx = context.getResources().getDisplayMetrics().density * 12.0f;
    }

    public void setFrameSize(int frameWidth, int frameHeight) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getActionMasked();
        pointerCount = motionEvent.getPointerCount();
        if (pointerCount > 1) {
            cancelGesture();
            return true;
        }

        GeometryHelper.NormalizedPoint point = GeometryHelper.mapTouchToFrame(
                motionEvent.getX(),
                motionEvent.getY(),
                view.getWidth(),
                view.getHeight(),
                frameWidth,
                frameHeight
        );

        if (action == MotionEvent.ACTION_DOWN) {
            if (point == null) {
                return false;
            }
            active = true;
            dragStarted = false;
            longPressTriggered = false;
            startPoint = point;
            lastPoint = point;
            handler.removeCallbacks(longPressRunnable);
            handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
            return true;
        }

        if (!active) {
            return false;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (point == null) {
                return true;
            }
            lastPoint = point;
            if (longPressTriggered) {
                return true;
            }
            if (distanceInViewSpace(startPoint, point, view.getWidth(), view.getHeight()) >= dragThresholdPx) {
                handler.removeCallbacks(longPressRunnable);
                if (!dragStarted) {
                    dragStarted = true;
                    callbacks.onGesture("drag_start", startPoint.x, startPoint.y, pointerCount);
                }
                callbacks.onGesture("drag_move", point.x, point.y, pointerCount);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            handler.removeCallbacks(longPressRunnable);
            if (point != null) {
                lastPoint = point;
            }
            if (dragStarted && lastPoint != null) {
                callbacks.onGesture("drag_end", lastPoint.x, lastPoint.y, pointerCount);
            } else if (!longPressTriggered && lastPoint != null) {
                callbacks.onGesture("tap", lastPoint.x, lastPoint.y, pointerCount);
            }
            resetState();
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            cancelGesture();
            return true;
        }

        return false;
    }

    private float distanceInViewSpace(GeometryHelper.NormalizedPoint first, GeometryHelper.NormalizedPoint second, int width, int height) {
        if (first == null || second == null) {
            return 0.0f;
        }
        float dx = (second.x - first.x) * width;
        float dy = (second.y - first.y) * height;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void cancelGesture() {
        handler.removeCallbacks(longPressRunnable);
        if (dragStarted && lastPoint != null) {
            callbacks.onGesture("drag_end", lastPoint.x, lastPoint.y, pointerCount);
        }
        resetState();
    }

    private void resetState() {
        active = false;
        dragStarted = false;
        longPressTriggered = false;
        startPoint = null;
        lastPoint = null;
        pointerCount = 0;
    }
}

