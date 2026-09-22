#!/usr/bin/env python3
"""Import the first infobox image from the catalogue's verified Fandom pages.

Explicit offline import; never accesses .sky files. Existing artwork is backed up.
Images and the resumable provenance manifest stay in the git-ignored images folder.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from html.parser import HTMLParser
import json
from pathlib import Path
import shutil
import subprocess
import urllib.parse

API = 'https://skylanders.fandom.com/api.php'
# Verified against the wiki: Hood Sickle alone redirects to a disambiguation page.
PAGE_OVERRIDES = {'Hood_Sickle': 'Hood_Sickle_(character)'}
# These two articles place their main thumbnail before the text, without an infobox.
LEAD_THUMBNAIL_PAGES = {'Volcanic_Vault', 'UFO_Hat'}

EXTENSIONS = ('.webp', '.png', '.jpg', '.jpeg')


class InfoboxImage(HTMLParser):
    def __init__(self, allow_lead_thumbnail=False):
        super().__init__()
        self.allow_lead_thumbnail = allow_lead_thumbnail
        self.before_text = True
        self.in_image = False
        self.url = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in ('p', 'h2', 'h3'):
            self.before_text = False
        classes = attrs.get('class', '').split()
        if tag == 'figure' and ('pi-image' in classes or (
                self.allow_lead_thumbnail and self.before_text and 'thumb' in classes)):
            self.in_image = True
        if self.in_image and tag == 'a' and not self.url:
            url = attrs.get('href', '')
            parsed = urllib.parse.urlparse(url)
            if parsed.scheme == 'https' and parsed.hostname == 'static.wikia.nocookie.net':
                self.url = url

    def handle_endtag(self, tag):
        if tag == 'figure':
            self.in_image = False


class HttpFailure(RuntimeError):
    def __init__(self, url, status, detail):
        super().__init__(f'HTTP {status}: {url}: {detail}')
        self.status = status


def request(url, maximum=20_000_000):
    # curl handles the site's HTTP transport more reliably than urllib here.
    result = subprocess.run([
        'curl', '--fail', '--location', '--silent', '--show-error',
        '--max-time', '30',
        '--max-filesize', str(maximum), '--write-out', '\n%{http_code}', url,
    ], capture_output=True, check=False)
    data, _, code = result.stdout.rpartition(b'\n')
    status = int(code) if code.isdigit() else 0
    if result.returncode or status >= 400:
        raise HttpFailure(url, status, result.stderr.decode(errors='replace').strip())
    if len(data) > maximum:
        raise ValueError('Response too large')
    return data


def extension(data):
    if data.startswith(b'\x89PNG\r\n\x1a\n'):
        return '.png'
    if data.startswith(b'\xff\xd8\xff'):
        return '.jpg'
    if data[:4] == b'RIFF' and data[8:12] == b'WEBP':
        return '.webp'
    raise ValueError('Unsupported image format or non-image response')


def retrieve(title):
    query = urllib.parse.urlencode({'action': 'parse', 'page': PAGE_OVERRIDES.get(title, title),
                                   'prop': 'text', 'redirects': 1, 'format': 'json'})
    page = json.loads(request(API + '?' + query))
    if 'error' in page:
        raise ValueError(page['error'].get('info', str(page['error'])))
    parser = InfoboxImage(allow_lead_thumbnail=title in LEAD_THUMBNAIL_PAGES)
    parser.feed(page['parse']['text']['*'])
    if not parser.url:
        raise ValueError('No main infobox image found')
    return {'page': page['parse']['title'], 'source': parser.url,
            'download': parser.url}


def install_artwork(out, identities, info, data, stamp):
    """Install only catalogue identity filenames, keeping a recoverable backup."""
    import re
    if not identities or any(not re.fullmatch(r'(?:\d+_\d+|villain_[a-z0-9]+)', key) for key in identities):
        raise ValueError('Invalid catalogue identity')
    if len(data) > 20_000_000:
        raise ValueError('Image too large')
    ext = extension(data)
    backup = out / '.wiki-backup' / stamp
    for key in identities:
        destination = out / (key + ext)
        temp = destination.with_suffix(destination.suffix + '.tmp')
        temp.write_bytes(data)
        for old_ext in EXTENSIONS:
            old = out / (key + old_ext)
            if old.exists():
                backup.mkdir(parents=True, exist_ok=True)
                shutil.copy2(old, backup / old.name)
        temp.replace(destination)
        for old_ext in EXTENSIONS:
            old = out / (key + old_ext)
            if old != destination and old.exists():
                old.unlink()
    info.update(status='imported', extension=ext, importedAt=stamp,
                identities=identities)
    info.pop('error', None)


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument('--catalog', type=Path, default=Path('catalog.json'))
    args.add_argument('--out', type=Path, default=Path('images'))
    args.add_argument('--browser', action='store_true', help='Import through your normal browser using a temporary local page')
    args.add_argument('--apply', action='store_true', help='Download and install artwork')
    args.add_argument('--resolve-only', action='store_true', help='Resolve main image URLs without downloading artwork')
    args.add_argument('--refresh', action='store_true', help='Re-fetch previously imported pages')
    args.add_argument('--traps', action='store_true', help='Import individual empty traps from the Trap gallery only')
    args.add_argument('--villains', action='store_true', help='Import main images from villains.json pages only')
    args.add_argument('--limit', type=int, help='Limit distinct wiki pages for a trial')
    options = args.parse_args()
    if options.traps and options.villains:
        args.error('--traps and --villains are mutually exclusive')
    entries = json.loads(options.catalog.read_text())['toys']
    trap_resolved = {}
    pages = {}
    missing = []
    for toy in entries:
        key = f"{toy['toyId']}_{toy['variantId']}"
        if toy.get('wiki'):
            pages.setdefault(toy['wiki'], []).append(key)
        else:
            missing.append({'identity': key, 'name': toy['nameEn'], 'category': toy['category']})
    if options.villains:
        import re
        import unicodedata
        pages, missing = {}, []
        for villain in json.loads(Path('villains.json').read_text())['villains']:
            folded = unicodedata.normalize('NFD', villain['name']).lower()
            key = 'villain_' + re.sub('[^a-z0-9]', '', folded)
            if villain.get('wiki'):
                pages.setdefault(villain['wiki'], []).append(key)
            else:
                missing.append({'identity': key, 'name': villain['name'], 'category': 'VILLAIN'})
    if options.traps:
        from trap_wiki_images import match_gallery
        query = urllib.parse.urlencode({'action': 'parse', 'page': 'Trap', 'prop': 'text', 'format': 'json'})
        page = json.loads(request(API + '?' + query))
        pages, trap_resolved, missing = match_gallery(entries, page['parse']['text']['*'])
        for item in missing:
            print(f'No individual gallery image: {item["name"]}')
    print(f'{len(pages)} pages for {sum(map(len, pages.values()))} identities; '
          f'{len(missing)} identities without a verified page.', flush=True)
    if not options.apply and not options.resolve_only and not options.browser:
        print('Dry run. Use --apply to import. Variants sharing a page share its main artwork.')
        return
    options.out.mkdir(parents=True, exist_ok=True)
    manifest_file = options.out / ('villain-wiki-images.json' if options.villains else 'trap-wiki-images.json' if options.traps else 'wiki-images.json')
    manifest = json.loads(manifest_file.read_text()) if manifest_file.exists() else {'pages': {}}
    manifest['withoutPage'] = missing
    for title, info in trap_resolved.items():
        if options.refresh or not manifest['pages'].get(title, {}).get('source'):
            manifest['pages'][title] = info
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    selected = list(pages)
    if options.limit:
        selected = selected[:options.limit]
    todo = [title for title in selected if options.refresh or not
            manifest['pages'].get(title, {}).get('source')]

    def save_manifest():
        temp = manifest_file.with_suffix('.json.tmp')
        temp.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        temp.replace(manifest_file)

    with ThreadPoolExecutor(max_workers=2) as pool:
        jobs = {pool.submit(retrieve, title): title for title in todo}
        for count, job in enumerate(as_completed(jobs), 1):
            title = jobs[job]
            try:
                info = job.result()
                manifest['pages'][title] = {**info, 'status': 'resolved',
                    'identities': pages[title],
                    'wiki': 'https://skylanders.fandom.com/wiki/' + urllib.parse.quote(info['page'].replace(' ', '_'))}
                print(f'[{count}/{len(todo)}] Resolved {title}', flush=True)
            except Exception as exc:
                manifest['pages'][title] = {'status': 'failed', 'error': str(exc),
                                            'identities': pages[title]}
                print(f'[{count}/{len(todo)}] FAILED {title}: {exc}', flush=True)
            save_manifest()

    if options.browser:
        from wiki_image_browser import serve
        serve(options.out, manifest, pages, selected, save_manifest)
        return

    if options.apply:
        for title in selected:
            info = manifest['pages'].get(title, {})
            if not info.get('source'):
                continue
            if not options.refresh and info.get('status') == 'imported' and all(
                    (options.out / (key + info['extension'])).exists() for key in pages[title]):
                continue
            try:
                data = request(info['download'])
                install_artwork(options.out, pages[title], info, data, stamp)
                info.pop('error', None)
                print(f'Imported {title} ({len(pages[title])} identities)', flush=True)
            except Exception as exc:
                info.update(status='download_failed', error=str(exc))
                print(f'Download failed: {exc}', flush=True)
                if isinstance(exc, HttpFailure) and exc.status in (403, 429):
                    save_manifest()
                    if exc.status == 403:
                        print('The image host denied access (HTTP 403). Repeating this command will not fix a Cloudflare challenge.\n'
                              'Check the image in your normal browser. Existing artwork is preserved.', flush=True)
                    else:
                        print('The image host is rate limiting requests (HTTP 429). Downloads stopped; wait before retrying.', flush=True)
                    break
            save_manifest()
    save_manifest()
    good = [p for p in manifest['pages'].values() if p['status'] == 'imported']
    failed = [title for title, p in manifest['pages'].items() if p['status'] in ('failed', 'download_failed')]
    print(f'Imported: {len(good)} pages, {sum(len(p["identities"]) for p in good)} identities.')
    resolved = [p for p in manifest['pages'].values() if p.get('source')]
    pending = sum(p.get('status') != 'imported' for p in resolved)
    print(f'Resolved: {len(resolved)} pages; awaiting download: {pending}; errors: {len(failed)}; '
          f'without page: {len(missing)}. Report: {manifest_file}')
    if options.apply and any(manifest['pages'].get(t, {}).get('status') != 'imported' for t in selected):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
