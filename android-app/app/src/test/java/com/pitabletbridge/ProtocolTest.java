package com.pitabletbridge;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ProtocolTest {
    @Test
    public void writesAndReadsMessageHeader() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] payloadBytes = "{\"hello\":\"world\"}".getBytes("UTF-8");
        Protocol.writeMessage(outputStream, Protocol.MSG_HELLO, payloadBytes);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        Protocol.Header header = Protocol.readHeader(inputStream);
        Assert.assertEquals(Protocol.MSG_HELLO, header.type);
        byte[] payload = Protocol.readFully(inputStream, header.length);
        Assert.assertEquals("{\"hello\":\"world\"}", new String(payload, "UTF-8"));
    }
}
