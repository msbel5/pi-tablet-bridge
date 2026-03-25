package com.pitabletbridge;

public final class GeometryHelper {
    private GeometryHelper() {
    }

    public static NormalizedPoint mapTouchToFrame(float touchX, float touchY, int viewWidth, int viewHeight, int frameWidth, int frameHeight) {
        if (viewWidth <= 0 || viewHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) {
            return null;
        }

        float viewAspect = (float) viewWidth / (float) viewHeight;
        float frameAspect = (float) frameWidth / (float) frameHeight;
        float drawnWidth;
        float drawnHeight;
        if (frameAspect > viewAspect) {
            drawnWidth = viewWidth;
            drawnHeight = drawnWidth / frameAspect;
        } else {
            drawnHeight = viewHeight;
            drawnWidth = drawnHeight * frameAspect;
        }

        float left = (viewWidth - drawnWidth) / 2.0f;
        float top = (viewHeight - drawnHeight) / 2.0f;
        float right = left + drawnWidth;
        float bottom = top + drawnHeight;
        if (touchX < left || touchX > right || touchY < top || touchY > bottom) {
            return null;
        }

        float normalizedX = (touchX - left) / drawnWidth;
        float normalizedY = (touchY - top) / drawnHeight;
        normalizedX = clamp(normalizedX, 0.0f, 1.0f);
        normalizedY = clamp(normalizedY, 0.0f, 1.0f);
        return new NormalizedPoint(normalizedX, normalizedY);
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public static final class NormalizedPoint {
        public final float x;
        public final float y;

        public NormalizedPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}

