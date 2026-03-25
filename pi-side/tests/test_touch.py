import unittest

from touch import NoopBackend, TouchDispatcher, normalized_to_screen


class TouchTest(unittest.TestCase):
    def test_normalized_to_screen(self):
        x, y = normalized_to_screen(0.5, 0.25, 1000, 800)
        self.assertEqual(500, x)
        self.assertEqual(200, y)

    def test_touch_dispatcher_drag_sequence(self):
        backend = NoopBackend()
        dispatcher = TouchDispatcher(backend=backend)
        dispatcher.update_screen_size(100, 100)
        dispatcher.handle_touch_event({"action": "drag_start", "x": 0.1, "y": 0.2})
        dispatcher.handle_touch_event({"action": "drag_move", "x": 0.3, "y": 0.4})
        dispatcher.handle_touch_event({"action": "drag_end", "x": 0.3, "y": 0.4})
        self.assertEqual("move_absolute", backend.commands[0][0])
        self.assertEqual(("button_down", "left"), backend.commands[1])
        self.assertEqual(("button_up", "left"), backend.commands[-1])

    def test_keyboard_text_and_special(self):
        backend = NoopBackend()
        dispatcher = TouchDispatcher(backend=backend)
        dispatcher.handle_key_event({"kind": "text", "text": "abc"})
        dispatcher.handle_key_event({"kind": "backspace", "count": 2})
        dispatcher.handle_key_event({"kind": "special", "key": "enter"})
        self.assertIn(("type_text", "abc"), backend.commands)
        self.assertIn(("press_special_key", "backspace"), backend.commands)
        self.assertIn(("press_special_key", "enter"), backend.commands)


if __name__ == "__main__":
    unittest.main()
