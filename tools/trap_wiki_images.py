"""Match individual Trap gallery artwork to catalogue names, never prototypes."""
from html.parser import HTMLParser
import re
from urllib.parse import urlparse

WIKI = 'https://skylanders.fandom.com/wiki/Trap#Gallery'
# Gallery captions differ from the names in the article's element lists.
ALIASES = {
    "Biter's Bane": 'Biters Bane',
    'Weed Whacker': 'Weed Wacker',
    'Rock Hawk': 'Rocky Hawk',
    'Grabbing Gadget': 'Grabby Gadget',
    'Seed Serpent': 'Seed Snake',
    'Flood Flask': 'Flood Mask',
    'Legendary Flood Flask': 'Legendary Flood Mask',
    'Tempest Timer': 'Air Hourglass',
    'Spinning Sandstorm': 'Earth Handstand',
    'Spark Spear': 'Captains Hat',
    'Cyclone Saber': 'Air Sword',
}


class TrapGallery(HTMLParser):
    def __init__(self):
        super().__init__()
        self.active = False
        self.images = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'h2':
            self.active = False
        if attrs.get('id') == 'Gallery':
            self.active = True
        if not self.active or tag != 'img' or not attrs.get('data-caption'):
            return
        caption = attrs.get('alt', '').strip()
        url = attrs.get('data-src') or attrs.get('src', '')
        parsed = urlparse(url)
        if parsed.scheme != 'https' or parsed.hostname != 'static.wikia.nocookie.net':
            return
        # Use original artwork, not the 185px gallery thumbnail.
        url = re.sub(r'/revision/latest/scale-to-width-down/\d+', '/revision/latest', url)
        self.images.setdefault(caption.casefold(), url)


def match_gallery(entries, html):
    parser = TrapGallery()
    parser.feed(html)
    pages, resolved, missing = {}, {}, []
    for toy in entries:
        if toy['category'] != 'TRAP':
            continue
        key = f"{toy['toyId']}_{toy['variantId']}"
        name = toy['nameEn']
        caption = ALIASES.get(name, name)
        url = parser.images.get(caption.casefold())
        if not url:
            missing.append({'identity': key, 'name': name, 'category': 'TRAP'})
            continue
        title = 'Trap gallery: ' + name
        pages[title] = [key]
        resolved[title] = {'page': 'Trap', 'wiki': WIKI, 'caption': caption,
                           'source': url, 'download': url,
                           'status': 'resolved', 'identities': [key]}
    return pages, resolved, missing
