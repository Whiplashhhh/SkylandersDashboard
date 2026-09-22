import sys
from pathlib import Path
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import json

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from import_wiki_images import InfoboxImage, extension, retrieve, request, HttpFailure


class MainArtworkTest(unittest.TestCase):
    def test_ignores_logo_and_gallery_and_keeps_first_infobox_image(self):
        parser = InfoboxImage()
        parser.feed('''<img src="logo.png"><a href="https://static.wikia.nocookie.net/logo">logo</a>
            <figure class="pi-item pi-image"><a href="https://static.wikia.nocookie.net/main.png">main</a></figure>
            <figure class="pi-item pi-image"><a href="https://static.wikia.nocookie.net/second.png">second</a></figure>''')
        self.assertEqual(parser.url, 'https://static.wikia.nocookie.net/main.png')

    def test_missing_infobox_has_no_arbitrary_fallback(self):
        parser = InfoboxImage()
        parser.feed('<img src="portrait.png"><figure><a href="https://static.wikia.nocookie.net/gallery.png">gallery</a></figure>')
        self.assertIsNone(parser.url)

    def test_untrusted_link_is_not_downloaded(self):
        parser = InfoboxImage()
        parser.feed('<figure class="pi-image"><a href="http://localhost/private">link</a></figure>')
        self.assertIsNone(parser.url)

    def test_known_legacy_layout_uses_leading_thumbnail(self):
        parser = InfoboxImage(allow_lead_thumbnail=True)
        parser.feed('<figure class="thumb"><a href="https://static.wikia.nocookie.net/main.png">main</a></figure>'
                    '<p>Article</p><figure class="thumb"><a href="https://static.wikia.nocookie.net/gallery.png">gallery</a></figure>')
        self.assertEqual(parser.url, 'https://static.wikia.nocookie.net/main.png')

    def test_legacy_layout_does_not_use_later_gallery(self):
        parser = InfoboxImage(allow_lead_thumbnail=True)
        parser.feed('<p>Article</p><figure class="thumb"><a href="https://static.wikia.nocookie.net/gallery.png">gallery</a></figure>')
        self.assertIsNone(parser.url)

    @patch('import_wiki_images.request')
    def test_hood_sickle_resolves_character_not_disambiguation(self, fetch):
        fetch.return_value = json.dumps({'parse': {'title': 'Hood Sickle (character)', 'text': {'*':
            '<figure class="pi-image"><a href="https://static.wikia.nocookie.net/main.png">main</a></figure>'}}}).encode()
        self.assertEqual(retrieve('Hood_Sickle')['page'], 'Hood Sickle (character)')
        self.assertIn('page=Hood_Sickle_%28character%29', fetch.call_args.args[0])

    @patch('import_wiki_images.subprocess.run')
    def test_http_status_is_not_inferred_from_url_text(self, run):
        run.return_value = SimpleNamespace(returncode=0, stdout=b'image403\n200', stderr=b'')
        self.assertEqual(request('https://example.test/403.png'), b'image403')
        run.return_value = SimpleNamespace(returncode=22, stdout=b'\n403', stderr=b'Forbidden')
        with self.assertRaises(HttpFailure) as error:
            request('https://example.test/art.png')
        self.assertEqual(error.exception.status, 403)

    def test_cloudflare_html_is_not_saved_as_image(self):
        with self.assertRaises(ValueError):
            extension(b'<!DOCTYPE html><title>Just a moment...</title>')
        self.assertEqual(extension(b'\x89PNG\r\n\x1a\n'), '.png')
        self.assertEqual(extension(b'\xff\xd8\xff'), '.jpg')
        self.assertEqual(extension(b'RIFF0000WEBP'), '.webp')


if __name__ == '__main__':
    unittest.main()
