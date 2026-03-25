package com.pitabletbridge;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Protocol {
    public static final int MSG_HELLO = 1;
    public static final int MSG_CONFIG = 2;
    public static final int MSG_FRAME = 3;
    public static final int MSG_TOUCH = 4;
    public static final int MSG_KEY = 5;
    public static final int MSG_PING = 6;
    public static final int MSG_PONG = 7;
    public static final int PROTOCOL_VERSION = 1;

    private Protocol() {
    }

    public static void writeMessage(OutputStream outputStream, int messageType, byte[] payload) throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        dataOutputStream.writeInt(messageType);
        dataOutputStream.writeInt(payload.length);
        dataOutputStream.write(payload);
        dataOutputStream.flush();
    }

    public static void writeJson(OutputStream outputStream, int messageType, JSONObject jsonObject) throws IOException {
        writeMessage(outputStream, messageType, jsonObject.toString().getBytes("UTF-8"));
    }

    public static Header readHeader(InputStream inputStream) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        int type = dataInputStream.readInt();
        int length = dataInputStream.readInt();
        return new Header(type, length);
    }

    public static byte[] readFully(InputStream inputStream, int length) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        byte[] payload = new byte[length];
        dataInputStream.readFully(payload);
        return payload;
    }

    public static final class Header {
        public final int type;
        public final int length;

        public Header(int type, int length) {
            this.type = type;
            this.length = length;
        }
    }
}

