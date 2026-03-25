import unittest

from protocol import (
    FRAME_PREFIX_STRUCT,
    HEADER_STRUCT,
    build_frame_payload,
    decode_header,
    decode_json,
    encode_json_message,
    parse_frame_payload,
)


class ProtocolTest(unittest.TestCase):
    def test_json_round_trip(self):
        message = {"a": 1, "b": "two"}
        encoded = encode_json_message(2, message)
        message_type, payload_len = decode_header(encoded[: HEADER_STRUCT.size])
        self.assertEqual(2, message_type)
        self.assertEqual(len(encoded) - HEADER_STRUCT.size, payload_len)
        self.assertEqual(message, decode_json(encoded[HEADER_STRUCT.size :]))

    def test_frame_round_trip(self):
        jpeg_bytes = b"\x01\x02\x03"
        payload = build_frame_payload(7, 640, 360, 123456, jpeg_bytes)
        self.assertEqual(FRAME_PREFIX_STRUCT.size + len(jpeg_bytes), len(payload))
        parsed = parse_frame_payload(payload)
        self.assertEqual(7, parsed["frame_id"])
        self.assertEqual(640, parsed["width"])
        self.assertEqual(360, parsed["height"])
        self.assertEqual(123456, parsed["timestamp_ms"])
        self.assertEqual(jpeg_bytes, parsed["jpeg_bytes"])


if __name__ == "__main__":
    unittest.main()
