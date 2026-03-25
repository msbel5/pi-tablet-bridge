package com.pitabletbridge;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FramePacketTest {
    @Test
    public void parseFramePacket() {
        ByteBuffer buffer = ByteBuffer.allocate(23).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(7);
        buffer.putInt(640);
        buffer.putInt(360);
        buffer.putLong(1234L);
        buffer.put(new byte[]{1, 2, 3});
        FramePacket packet = FramePacket.parse(buffer.array());
        Assert.assertEquals(7, packet.frameId);
        Assert.assertEquals(640, packet.width);
        Assert.assertEquals(360, packet.height);
        Assert.assertEquals(1234L, packet.timestampMs);
        Assert.assertArrayEquals(new byte[]{1, 2, 3}, packet.jpegBytes);
    }
}

