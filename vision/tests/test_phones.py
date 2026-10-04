import unittest

from focusquest_companion.phones import Found, closer_looks, confidence, distinct, held, moved, overlaps

HAND = (100, 100, 80, 120)


class OverlapsTest(unittest.TestCase):

    def test_boxes_that_share_an_area_overlap(self):
        self.assertTrue(overlaps((150, 150, 50, 50), HAND))

    def test_boxes_that_only_touch_or_are_apart_do_not(self):
        self.assertFalse(overlaps((180, 100, 40, 40), HAND))   # starts where the hand ends
        self.assertFalse(overlaps((400, 400, 40, 40), HAND))


class HeldTest(unittest.TestCase):

    def test_a_phone_in_a_hand_is_held(self):
        phone = Found(0.5, (150, 150, 60, 100))
        self.assertEqual(held([phone], [HAND]), phone)

    def test_a_phone_lying_away_from_any_hand_is_not(self):
        self.assertIsNone(held([Found(0.9, (400, 400, 60, 100))], [HAND]))
        self.assertIsNone(held([Found(0.9, (150, 150, 60, 100))], []))

    def test_a_doubtful_phone_does_not_count_even_in_a_hand(self):
        self.assertIsNone(held([Found(0.2, (150, 150, 60, 100))], [HAND]))

    def test_the_surest_phone_in_a_hand_is_taken(self):
        lying = Found(0.9, (400, 400, 60, 100))
        doubtful = Found(0.3, (110, 110, 30, 30))
        surer = Found(0.5, (150, 150, 60, 100))
        self.assertEqual(held([lying, doubtful, surer], [HAND]), surer)


class CloserLooksTest(unittest.TestCase):

    def test_the_lower_left_and_lower_right_overlapping_in_the_middle(self):
        self.assertEqual(closer_looks(1280, 720), [(0, 288, 768, 432), (512, 288, 768, 432)])

    def test_a_box_found_in_a_closer_look_is_placed_in_the_whole_frame(self):
        self.assertEqual(moved((10, 20, 30, 40), (512, 288, 768, 432)), (522, 308, 30, 40))

    def test_a_phone_found_again_in_a_closer_look_is_kept_once_at_its_surest(self):
        whole = Found(0.3, (100, 100, 40, 80))
        closer = Found(0.6, (102, 98, 40, 84))
        elsewhere = Found(0.4, (400, 100, 40, 80))
        self.assertEqual(distinct([whole, elsewhere, closer]), [closer, elsewhere])


class ConfidenceTest(unittest.TestCase):

    def test_rescaled_from_the_backends_minimum_up_to_certain(self):
        self.assertEqual(confidence(Found(0.25, HAND)), 0.7)
        self.assertEqual(confidence(Found(0.425, HAND)), 0.85)
        self.assertEqual(confidence(Found(0.6, HAND)), 1.0)
        self.assertEqual(confidence(Found(0.9, HAND)), 1.0)


if __name__ == "__main__":
    unittest.main()
