package com.pitabletbridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FramePacket {
    public final int frameId;
    public final int width;
    public final int height;
    public final long timestampMs;
    public final byte[] jpegBytes;

    public FramePacket(int frameId, int width, int height, long timestampMs, byte[] jpegBytes) {
        this.frameId = frameId;
        this.width = width;
        this.height = height;
        this.timestampMs = timestampMs;
        this.jpegBytes = jpegBytes;
    }

    public static FramePacket parse(byte[] payload) {
        if (payload == null || payload.length < 20) {
            throw new IllegalArgumentException("Frame payload too short");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int frameId = buffer.getInt();
        int width = buffer.getInt();
        int height = buffer.getInt();
        long timestampMs = buffer.getLong();
        byte[] jpegBytes = new byte[payload.length - 20];
        buffer.get(jpegBytes);
        return new FramePacket(frameId, width, height, timestampMs, jpegBytes);
    }
}

