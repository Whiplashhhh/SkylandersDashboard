import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from skylanders_connector import inventory, resolve_file, execute, public_state


class ConnectorTest(unittest.TestCase):
    def test_roots_and_identity(self):
        # Real dump fixture; connector never parses its bytes.
        source = Path(__file__).parents[1] / 'server/src/test/resources/fixtures/food_fight_virgin.sky'
        with tempfile.TemporaryDirectory() as folder, tempfile.TemporaryDirectory() as outside:
            root = Path(folder)
            (root / 'one.sky').write_bytes(source.read_bytes())
            (root / 'two.sky').write_bytes(source.read_bytes())
            (root / 'short.sky').write_bytes(b'invalid')
            escaped = Path(outside) / 'outside.sky'
            escaped.write_bytes(source.read_bytes())
            (root / 'escape.sky').symlink_to(escaped)
            files = inventory(root)
            self.assertEqual(2, len(files))
            identities = list(files)
            self.assertNotEqual(*identities)
            path = resolve_file(root, files, identities[0])
            observed = {'epoch': 'e', 'revision': 0, 'enabled': True, 'capacity': 16,
                        'slots': [{'index': 3, 'toyId': 476, 'variantId': 0, 'path': str(path)}]}
            public = public_state(observed, root, files)
            self.assertNotIn('path', public['slots'][0])
            self.assertEqual(identities[0], public['slots'][0]['fileId'])
            path.unlink()
            path.symlink_to(escaped)
            with self.assertRaises(ValueError): resolve_file(root, files, identities[0])
            with self.assertRaises(ValueError): resolve_file(root, files, '../outside.sky')

    @patch('skylanders_connector.request')
    def test_expired_or_old_session_never_reaches_cemu(self, local):
        for session, expiry in [('old', 10**15), ('current', 0)]:
            with self.assertRaises(ValueError):
                execute({'session': session, 'expiresAt': expiry}, '/absent', Path('/tmp'), {}, 'current')
        local.assert_not_called()


if __name__ == '__main__': unittest.main()
