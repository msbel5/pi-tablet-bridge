import json
import struct


HEADER_STRUCT = struct.Struct(">II")
FRAME_PREFIX_STRUCT = struct.Struct(">IIIQ")

MSG_HELLO = 1
MSG_CONFIG = 2
MSG_FRAME = 3
MSG_TOUCH = 4
MSG_KEY = 5
MSG_PING = 6
MSG_PONG = 7

PROTOCOL_VERSION = 1


def encode_message(message_type, payload):
    return HEADER_STRUCT.pack(int(message_type), len(payload)) + payload


def encode_json_message(message_type, obj):
    payload = json.dumps(obj, separators=(",", ":"), sort_keys=True).encode("utf-8")
    return encode_message(message_type, payload)


def decode_header(header_bytes):
    return HEADER_STRUCT.unpack(header_bytes)


def decode_json(payload):
    if not payload:
        return {}
    return json.loads(payload.decode("utf-8"))


def build_frame_payload(frame_id, width, height, timestamp_ms, jpeg_bytes):
    prefix = FRAME_PREFIX_STRUCT.pack(int(frame_id), int(width), int(height), int(timestamp_ms))
    return prefix + jpeg_bytes


def parse_frame_payload(payload):
    prefix_size = FRAME_PREFIX_STRUCT.size
    if len(payload) < prefix_size:
        raise ValueError("frame payload too small")
    frame_id, width, height, timestamp_ms = FRAME_PREFIX_STRUCT.unpack(payload[:prefix_size])
    return {
        "frame_id": frame_id,
        "width": width,
        "height": height,
        "timestamp_ms": timestamp_ms,
        "jpeg_bytes": payload[prefix_size:],
    }


def recv_exact(sock, size):
    chunks = []
    remaining = size
    while remaining > 0:
        chunk = sock.recv(remaining)
        if not chunk:
            raise IOError("socket closed")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)

