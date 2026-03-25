package com.pitabletbridge;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public final class TransportManager {
    public interface Callbacks {
        void onStatusChanged(String status, String transport);

        void onFrame(FramePacket framePacket);

        void onConfig(JSONObject config);
    }

    private static final int PORT = 19876;
    private static final int DISCOVERY_PORT = 19877;

    private final Object stateLock = new Object();
    private final Callbacks callbacks;

    private volatile boolean running;
    private ConnectionWorker activeConnection;
    private long lastLanAttemptMs;
    private Thread usbThread;
    private Thread lanThread;
    private Thread heartbeatThread;
    private ServerSocket usbServerSocket;
    private DatagramSocket discoverySocket;

    public TransportManager(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void start() {
        synchronized (stateLock) {
            if (running) {
                return;
            }
            running = true;
        }
        callbacks.onStatusChanged("Searching for Pi", null);
        usbThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runUsbServer();
            }
        }, "usb-server");
        lanThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runLanDiscovery();
            }
        }, "lan-discovery");
        heartbeatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runHeartbeat();
            }
        }, "heartbeat");
        usbThread.start();
        lanThread.start();
        heartbeatThread.start();
    }

    public void stop() {
        synchronized (stateLock) {
            running = false;
        }
        closeServerSocket();
        closeDiscoverySocket();
        ConnectionWorker connectionWorker = getActiveConnection();
        if (connectionWorker != null) {
            connectionWorker.close();
        }
        joinThread(usbThread);
        joinThread(lanThread);
        joinThread(heartbeatThread);
    }

    public void sendTouch(String action, float normalizedX, float normalizedY, int pointerCount) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("action", action);
            jsonObject.put("x", normalizedX);
            jsonObject.put("y", normalizedY);
            jsonObject.put("pointers", pointerCount);
        } catch (JSONException ignored) {
            return;
        }
        sendJson(Protocol.MSG_TOUCH, jsonObject);
    }

    public void sendKey(KeyCommand keyCommand) {
        try {
            sendJson(Protocol.MSG_KEY, keyCommand.toJson());
        } catch (JSONException ignored) {
        }
    }

    private void sendJson(int messageType, JSONObject jsonObject) {
        ConnectionWorker connectionWorker = getActiveConnection();
        if (connectionWorker != null) {
            connectionWorker.sendJson(messageType, jsonObject);
        }
    }

    private ConnectionWorker getActiveConnection() {
        synchronized (stateLock) {
            return activeConnection;
        }
    }

    private void runUsbServer() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(PORT));
            serverSocket.setSoTimeout(1000);
            usbServerSocket = serverSocket;
            while (isRunning()) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    promoteConnection(new ConnectionWorker(socket, "usb", socket.getInetAddress().getHostAddress()));
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException e) {
            callbacks.onStatusChanged("USB server error: " + e.getMessage(), null);
        } finally {
            closeServerSocket();
        }
    }

    private void runLanDiscovery() {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            socket.setBroadcast(true);
            socket.setSoTimeout(1000);
            discoverySocket = socket;
            long lastDiscoverMs = 0L;
            byte[] buffer = new byte[1024];
            while (isRunning()) {
                long now = System.currentTimeMillis();
                if (now - lastDiscoverMs >= 2000L) {
                    sendDiscover(socket);
                    lastDiscoverMs = now;
                }

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException ignored) {
                    continue;
                }
                String payload = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                if (!payload.startsWith("{")) {
                    continue;
                }
                JSONObject jsonObject = new JSONObject(payload);
                if ("BEACON".equals(jsonObject.optString("kind"))) {
                    maybeConnectLan(jsonObject.optString("ip"), jsonObject.optInt("port", PORT));
                }
            }
        } catch (Exception e) {
            callbacks.onStatusChanged("LAN discovery error: " + e.getMessage(), null);
        } finally {
            closeDiscoverySocket();
        }
    }

    private void sendDiscover(DatagramSocket socket) throws IOException, JSONException {
        JSONObject object = new JSONObject();
        object.put("kind", "DISCOVER");
        object.put("ts", System.currentTimeMillis());
        byte[] payload = object.toString().getBytes("UTF-8");
        DatagramPacket packet = new DatagramPacket(payload, payload.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
        socket.send(packet);
    }

    private void maybeConnectLan(String host, int port) {
        if (host == null || host.length() == 0) {
            return;
        }
        ConnectionWorker current = getActiveConnection();
        if (current != null && "usb".equals(current.transport)) {
            return;
        }
        if (current != null && "lan".equals(current.transport) && current.isOpen()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLanAttemptMs < 1500L) {
            return;
        }
        lastLanAttemptMs = now;
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setTcpNoDelay(true);
            promoteConnection(new ConnectionWorker(socket, "lan", host + ":" + port));
        } catch (IOException ignored) {
        }
    }

    private void promoteConnection(ConnectionWorker candidate) {
        ConnectionWorker replaced = null;
        synchronized (stateLock) {
            if (!running) {
                candidate.close();
                return;
            }
            if (activeConnection != null && activeConnection.isOpen()) {
                if (activeConnection.priority > candidate.priority) {
                    candidate.close();
                    return;
                }
                if (activeConnection.priority == candidate.priority) {
                    candidate.close();
                    return;
                }
                replaced = activeConnection;
            }
            activeConnection = candidate;
        }
        if (replaced != null) {
            replaced.close();
        }
        candidate.start();
        callbacks.onStatusChanged("Connected via " + candidate.transport.toUpperCase(), candidate.transport);
    }

    private void handleMessage(ConnectionWorker worker, int messageType, byte[] payload) throws IOException, JSONException {
        if (messageType == Protocol.MSG_PING) {
            JSONObject object = new JSONObject();
            object.put("ts", System.currentTimeMillis());
            worker.sendJson(Protocol.MSG_PONG, object);
            return;
        }
        if (messageType == Protocol.MSG_FRAME) {
            callbacks.onFrame(FramePacket.parse(payload));
            return;
        }
        if (messageType == Protocol.MSG_CONFIG) {
            callbacks.onConfig(new JSONObject(new String(payload, "UTF-8")));
        }
    }

    private void handleClosed(ConnectionWorker worker) {
        synchronized (stateLock) {
            if (activeConnection == worker) {
                activeConnection = null;
                if (running) {
                    callbacks.onStatusChanged("Disconnected", null);
                }
            }
        }
    }

    private void runHeartbeat() {
        while (isRunning()) {
            ConnectionWorker worker = getActiveConnection();
            if (worker != null && worker.isOpen()) {
                JSONObject object = new JSONObject();
                try {
                    object.put("ts", System.currentTimeMillis());
                } catch (JSONException ignored) {
                }
                worker.sendJson(Protocol.MSG_PING, object);
            }
            sleepQuietly(1000L);
        }
    }

    private boolean isRunning() {
        synchronized (stateLock) {
            return running;
        }
    }

    private void closeServerSocket() {
        ServerSocket serverSocket = usbServerSocket;
        usbServerSocket = null;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeDiscoverySocket() {
        DatagramSocket socket = discoverySocket;
        discoverySocket = null;
        if (socket != null) {
            socket.close();
        }
    }

    private void joinThread(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(1000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private final class ConnectionWorker {
        private final Socket socket;
        private final String transport;
        private final String peerName;
        private final int priority;
        private final Object sendLock = new Object();
        private volatile boolean open = true;
        private Thread thread;

        private ConnectionWorker(Socket socket, String transport, String peerName) {
            this.socket = socket;
            this.transport = transport;
            this.peerName = peerName;
            this.priority = "usb".equals(transport) ? 2 : 1;
        }

        private void start() {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    runLoop();
                }
            }, "connection-" + transport);
            thread.start();
        }

        private boolean isOpen() {
            return open && !socket.isClosed();
        }

        private void sendJson(int messageType, JSONObject jsonObject) {
            synchronized (sendLock) {
                if (!isOpen()) {
                    return;
                }
                try {
                    Protocol.writeJson(socket.getOutputStream(), messageType, jsonObject);
                } catch (IOException e) {
                    close();
                }
            }
        }

        private void runLoop() {
            try {
                sendHello();
                while (isOpen()) {
                    Protocol.Header header = Protocol.readHeader(socket.getInputStream());
                    byte[] payload = Protocol.readFully(socket.getInputStream(), header.length);
                    handleMessage(this, header.type, payload);
                }
            } catch (SocketException ignored) {
            } catch (Exception ignored) {
            } finally {
                close();
            }
        }

        private void sendHello() throws JSONException, IOException {
            JSONObject object = new JSONObject();
            object.put("protocol_version", Protocol.PROTOCOL_VERSION);
            object.put("role", "tablet");
            object.put("transport", transport);
            object.put("app_version", "0.1.0");
            object.put("capabilities", "display,touch,keyboard");
            Protocol.writeJson(socket.getOutputStream(), Protocol.MSG_HELLO, object);
        }

        private void close() {
            if (!open) {
                return;
            }
            open = false;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            handleClosed(this);
        }
    }
}

