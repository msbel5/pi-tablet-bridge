import json
import socket
import threading
import time

from protocol import (
    MSG_HELLO,
    MSG_PING,
    MSG_PONG,
    decode_header,
    decode_json,
    encode_json_message,
    encode_message,
    recv_exact,
)


class BridgeSession(object):
    def __init__(self, sock, transport_name, peer_name, on_message, on_close):
        self.sock = sock
        self.transport_name = transport_name
        self.peer_name = peer_name
        self.priority = 2 if transport_name == "usb" else 1
        self.on_message = on_message
        self.on_close = on_close
        self._send_lock = threading.Lock()
        self._closed = threading.Event()
        self._thread = threading.Thread(target=self._read_loop, name="session-%s" % transport_name)
        self._thread.daemon = True

    def start(self):
        self._thread.start()

    def is_closed(self):
        return self._closed.is_set()

    def send_payload(self, message_type, payload):
        if self.is_closed():
            return False
        try:
            with self._send_lock:
                self.sock.sendall(encode_message(message_type, payload))
            return True
        except Exception:
            self.close()
            return False

    def send_json(self, message_type, obj):
        payload = json.dumps(obj, separators=(",", ":"), sort_keys=True).encode("utf-8")
        return self.send_payload(message_type, payload)

    def close(self):
        if self._closed.is_set():
            return
        self._closed.set()
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except Exception:
            pass
        try:
            self.sock.close()
        except Exception:
            pass
        self.on_close(self)

    def _read_loop(self):
        try:
            while not self._closed.is_set():
                header = recv_exact(self.sock, 8)
                message_type, payload_len = decode_header(header)
                payload = recv_exact(self.sock, payload_len)
                self.on_message(self, message_type, payload)
        except Exception:
            self.close()


class SessionArbiter(object):
    def __init__(self):
        self._lock = threading.Lock()
        self._active_session = None

    def get_active(self):
        with self._lock:
            return self._active_session

    def consider(self, session):
        with self._lock:
            current = self._active_session
            if current and not current.is_closed() and current.priority > session.priority:
                return False, None
            if current and not current.is_closed() and current.priority == session.priority:
                return False, None
            self._active_session = session
            return True, current

    def clear_if_active(self, session):
        with self._lock:
            if self._active_session is session:
                self._active_session = None


def discover_lan_ip():
    probe_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe_socket.connect(("192.0.2.1", 9))
        return probe_socket.getsockname()[0]
    except Exception:
        return None
    finally:
        probe_socket.close()


class TransportManager(object):
    def __init__(self, adb_manager, port, discovery_port, on_message, on_session_ready, on_session_closed):
        self.adb_manager = adb_manager
        self.port = int(port)
        self.discovery_port = int(discovery_port)
        self.on_message = on_message
        self.on_session_ready = on_session_ready
        self.on_session_closed = on_session_closed
        self.arbiter = SessionArbiter()
        self.stop_event = threading.Event()
        self.threads = []
        self.lan_ip = discover_lan_ip()

    def start(self):
        self.threads = [
            threading.Thread(target=self._run_usb_connector, name="usb-connector"),
            threading.Thread(target=self._run_udp_discovery, name="udp-discovery"),
        ]
        if self.lan_ip:
            self.threads.append(threading.Thread(target=self._run_lan_server, name="lan-server"))
        for thread in self.threads:
            thread.daemon = True
            thread.start()

    def stop(self):
        self.stop_event.set()
        active = self.get_active_session()
        if active:
            active.close()
        for thread in self.threads:
            if thread.is_alive():
                thread.join(timeout=2.0)

    def get_active_session(self):
        return self.arbiter.get_active()

    def _create_session(self, sock, transport_name, peer_name):
        try:
            sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        except Exception:
            pass
        session = BridgeSession(
            sock=sock,
            transport_name=transport_name,
            peer_name=peer_name,
            on_message=self._handle_message,
            on_close=self._handle_session_closed,
        )
        accepted, replaced = self.arbiter.consider(session)
        if not accepted:
            session.close()
            return None
        if replaced:
            replaced.close()
        session.start()
        self.on_session_ready(session)
        return session

    def _handle_message(self, session, message_type, payload):
        if message_type == MSG_PING:
            session.send_json(MSG_PONG, {"ts": int(time.time() * 1000)})
            return
        self.on_message(session, message_type, payload)

    def _handle_session_closed(self, session):
        self.arbiter.clear_if_active(session)
        self.on_session_closed(session)

    def _run_usb_connector(self):
        while not self.stop_event.is_set():
            serial = self.adb_manager.wait_for_device(timeout=1.0, poll_interval=0.5)
            if not serial:
                self.stop_event.wait(1.0)
                continue

            active = self.get_active_session()
            if active and not active.is_closed() and active.transport_name == "usb":
                self.stop_event.wait(1.0)
                continue

            device_adb = self.adb_manager.with_serial(serial)
            try:
                device_adb.ensure_forward(self.port, self.port)
                sock = socket.create_connection(("127.0.0.1", self.port), timeout=3.0)
                self._create_session(sock, "usb", "127.0.0.1:%d" % self.port)
                self.stop_event.wait(1.0)
            except Exception:
                self.stop_event.wait(2.0)

    def _run_lan_server(self):
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server.bind((self.lan_ip, self.port))
            server.listen(2)
            server.settimeout(1.0)
            while not self.stop_event.is_set():
                try:
                    client, address = server.accept()
                except socket.timeout:
                    continue
                self._create_session(client, "lan", "%s:%d" % address)
        finally:
            server.close()

    def _run_udp_discovery(self):
        udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            udp_socket.bind(("", self.discovery_port))
            udp_socket.settimeout(1.0)
            last_broadcast = 0.0
            while not self.stop_event.is_set():
                now = time.time()
                if self.lan_ip and now - last_broadcast >= 2.0:
                    beacon = json.dumps(
                        {
                            "kind": "BEACON",
                            "ip": self.lan_ip,
                            "port": self.port,
                            "ts": int(now * 1000),
                        },
                        separators=(",", ":"),
                    ).encode("utf-8")
                    udp_socket.sendto(beacon, ("255.255.255.255", self.discovery_port))
                    last_broadcast = now
                try:
                    payload, address = udp_socket.recvfrom(2048)
                except socket.timeout:
                    continue
                try:
                    message = decode_json(payload)
                except Exception:
                    continue
                if message.get("kind") == "DISCOVER" and self.lan_ip:
                    response = encode_json_message(
                        MSG_HELLO,
                        {
                            "kind": "BEACON",
                            "ip": self.lan_ip,
                            "port": self.port,
                            "ts": int(time.time() * 1000),
                        },
                    )
                    udp_socket.sendto(response[8:], address)
        finally:
            udp_socket.close()

