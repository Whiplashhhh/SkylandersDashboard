"""Temporary loopback UI for user-initiated artwork imports in a normal browser."""
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import secrets
from urllib.parse import urlparse

from import_wiki_images import install_artwork

MAX_IMAGE = 20_000_000


def pending_images(out, manifest, pages, selected):
    jobs = []
    for title in selected:
        info = manifest['pages'].get(title, {})
        parsed = urlparse(info.get('download', ''))
        if parsed.scheme != 'https' or parsed.hostname != 'static.wikia.nocookie.net':
            continue
        if info.get('status') == 'imported' and all(
                (out / (key + info['extension'])).exists() for key in pages[title]):
            continue
        jobs.append({'title': title, 'url': info['download'], 'identities': pages[title]})
    return jobs


def serve(out, manifest, pages, selected, save_manifest):
    token = secrets.token_urlsafe(24)
    prefix = '/' + token + '/'
    jobs = pending_images(out, manifest, pages, selected)
    if not jobs:
        print('No resolved images awaiting import.')
        return
    completed = set()
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    html = Path(__file__).with_name('wiki_image_browser.html').read_bytes()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Do not log the local capability URL.

        def send(self, status, data, content_type='application/json'):
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Referrer-Policy', 'no-referrer')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Content-Security-Policy',
                "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
                "connect-src 'self' https://static.wikia.nocookie.net; "
                "img-src blob:; base-uri 'none'; frame-ancestors 'none'")
            self.end_headers()
            self.wfile.write(data)

        def authorized(self):
            return self.headers.get('Host') == host and self.path.startswith(prefix)

        def do_GET(self):
            if not self.authorized():
                self.send(403, b'{}')
            elif self.path == prefix:
                self.send(200, html, 'text/html; charset=utf-8')
            elif self.path == prefix + 'state':
                data = {'jobs': jobs, 'completed': sorted(completed)}
                self.send(200, json.dumps(data).encode())
            else:
                self.send(404, b'{}')

        def do_POST(self):
            if not self.authorized() or self.headers.get('Origin') != origin:
                self.send(403, b'{}')
                return
            try:
                index_text = self.path.removeprefix(prefix + 'image/')
                if not self.path.startswith(prefix + 'image/') or not index_text.isdigit():
                    raise ValueError('Invalid image index')
                index = int(index_text)
                if index >= len(jobs):
                    raise ValueError('Unknown image')
                size = int(self.headers.get('Content-Length', '0'))
                if not 0 < size <= MAX_IMAGE:
                    raise ValueError('Invalid image size')
                data = self.rfile.read(size)
                if len(data) != size:
                    raise ValueError('Incomplete image')
                if index not in completed:
                    job = jobs[index]
                    info = manifest['pages'][job['title']]
                    install_artwork(out, job['identities'], info, data, stamp)
                    save_manifest()
                    completed.add(index)
                    print(f'[{len(completed)}/{len(jobs)}] Imported {job["title"]}', flush=True)
                self.send(200, b'{"ok":true}')
            except (ValueError, OSError) as exc:
                self.send(400, json.dumps({'error': str(exc)}).encode())

    with HTTPServer(('127.0.0.1', 0), Handler) as server:
        host = f'127.0.0.1:{server.server_port}'
        origin = 'http://' + host
        print(f'Open in your normal browser: {origin}{prefix}', flush=True)
        print(f'{len(jobs)} images awaiting download. Keep this terminal open. Ctrl+C stops the local server.', flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print('\nStopped. Completed imports are saved; --browser resumes them.')
