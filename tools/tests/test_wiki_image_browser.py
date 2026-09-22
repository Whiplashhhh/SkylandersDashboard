import sys
from pathlib import Path
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from import_wiki_images import install_artwork
from wiki_image_browser import pending_images


class BrowserImportTest(unittest.TestCase):
    def test_villain_filename_and_traversal_rejection(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            data = b'\x89PNG\r\n\x1a\nimage'
            install_artwork(out, ['villain_bombshell'], {}, data, 'test')
            self.assertEqual((out / 'villain_bombshell.png').read_bytes(), data)
            for name in ['villain_../secret', 'villain_', 'villain_a/b', '/tmp/villain_a']:
                with self.assertRaises(ValueError):
                    install_artwork(out, [name], {}, data, 'test')

    def test_new_format_replaces_old_but_keeps_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            old = b'RIFF0000WEBPold'
            (out / '1_0.webp').write_bytes(old)
            info = {'status': 'download_failed', 'error': '403'}
            image = b'\x89PNG\r\n\x1a\nnew'
            install_artwork(out, ['1_0', '1_2'], info, image, 'test')
            self.assertEqual((out / '1_0.png').read_bytes(), image)
            self.assertEqual((out / '1_2.png').read_bytes(), image)
            self.assertFalse((out / '1_0.webp').exists())
            self.assertEqual((out / '.wiki-backup/test/1_0.webp').read_bytes(), old)
            self.assertEqual(info['status'], 'imported')
            self.assertNotIn('error', info)

    def test_invalid_image_and_identity_leave_existing_artwork(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            (out / '1_0.webp').write_bytes(b'old')
            with self.assertRaises(ValueError):
                install_artwork(out, ['1_0'], {}, b'<html>Cloudflare</html>', 'test')
            with self.assertRaises(ValueError):
                install_artwork(out, ['../1_0'], {}, b'\xff\xd8\xff', 'test')
            self.assertEqual((out / '1_0.webp').read_bytes(), b'old')
            self.assertEqual(len(list(out.iterdir())), 1)

    def test_resume_checks_files_and_only_allows_fandom_https(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            info = {'status': 'imported', 'extension': '.png',
                    'download': 'https://static.wikia.nocookie.net/image.png'}
            manifest = {'pages': {'One': info, 'Bad': {'download': 'http://localhost/private'}}}
            pages = {'One': ['1_0'], 'Bad': ['2_0']}
            self.assertEqual(len(pending_images(out, manifest, pages, list(pages))), 1)
            (out / '1_0.png').write_bytes(b'present')
            self.assertEqual(pending_images(out, manifest, pages, list(pages)), [])


if __name__ == '__main__':
    unittest.main()
