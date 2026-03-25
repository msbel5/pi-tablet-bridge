import unittest

from transport import SessionArbiter


class FakeSession(object):
    def __init__(self, name, priority):
        self.name = name
        self.priority = priority
        self.closed = False

    def is_closed(self):
        return self.closed


class SessionArbiterTest(unittest.TestCase):
    def test_arbiter_rejects_lower_priority_candidate(self):
        arbiter = SessionArbiter()
        usb = FakeSession("usb", 2)
        accepted, replaced = arbiter.consider(usb)
        self.assertTrue(accepted)
        self.assertIsNone(replaced)

        lan = FakeSession("lan", 1)
        accepted, replaced = arbiter.consider(lan)
        self.assertFalse(accepted)
        self.assertIsNone(replaced)

    def test_arbiter_replaces_lan_with_usb(self):
        arbiter = SessionArbiter()
        lan = FakeSession("lan", 1)
        accepted, replaced = arbiter.consider(lan)
        self.assertTrue(accepted)
        self.assertIsNone(replaced)

        usb = FakeSession("usb", 2)
        accepted, replaced = arbiter.consider(usb)
        self.assertTrue(accepted)
        self.assertIs(lan, replaced)


if __name__ == "__main__":
    unittest.main()
