import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from trap_wiki_images import match_gallery


class TrapGalleryTest(unittest.TestCase):
    def test_gallery_scope_alias_original_and_missing(self):
        image = ('<img alt="Weed Wacker" data-caption="Weed Wacker" '
                 'data-src="https://static.wikia.nocookie.net/skylanders/images/a/ab/Trap.png'
                 '/revision/latest/scale-to-width-down/185?cb=123">')
        entries = [dict(toyId=217, variantId=12298, nameEn='Weed Whacker', category='TRAP'),
                   dict(toyId=218, variantId=12288, nameEn='Dark Dagger', category='TRAP')]
        self.assertFalse(match_gallery(entries, image)[0])
        pages, resolved, missing = match_gallery(entries, '<h2><span id="Gallery">Gallery</span></h2>' + image)
        self.assertEqual(list(pages.values()), [['217_12298']])
        self.assertEqual(next(iter(resolved.values()))['download'],
                         'https://static.wikia.nocookie.net/skylanders/images/a/ab/Trap.png/revision/latest?cb=123')
        self.assertEqual([m['name'] for m in missing], ['Dark Dagger'])

    def test_reject_external_image_and_later_section(self):
        entry = [dict(toyId=220, variantId=12318, nameEn='Kaos Trap', category='TRAP')]
        for html in ['<span id="Gallery"></span><img alt="Kaos Trap" data-caption="Kaos Trap" src="https://example.com/x.png">',
                     '<span id="Gallery"></span><h2>Other</h2><img alt="Kaos Trap" data-caption="Kaos Trap" src="https://static.wikia.nocookie.net/x.png">']:
            self.assertFalse(match_gallery(entry, html)[0])
