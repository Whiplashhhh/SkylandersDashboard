#!/usr/bin/env python3
"""Read-only file inventory and authenticated outgoing portal command channel."""
import argparse
import hashlib
import json
import logging
from pathlib import Path
import time
import urllib.request
import urllib.parse
import uuid
from portal_client import request, state

log = logging.getLogger('portal-connector')


def file_id(relative):
    return hashlib.sha256(relative.encode('utf-8')).hexdigest()


def inventory_digest(files):
    """Fingerprint of the published inventory: sorted ids, one per line.

    The server computes the same from what it actually received, so a mismatch
    always means it must ask for the list again.
    """
    return hashlib.sha256(''.join(f'{identity}\n' for identity in sorted(files)).encode()).hexdigest()


def inventory(root):
    files = {}
    for candidate in root.rglob('*.sky'):
        try:
            resolved = candidate.resolve(strict=True)
            if not resolved.is_relative_to(root) or not resolved.is_file() or resolved.stat().st_size != 1024:
                log.warning('Rejected figure: %s', candidate)
                continue
            relative = candidate.relative_to(root).as_posix()
            files[file_id(relative)] = {'id': file_id(relative), 'relativePath': relative}
        except OSError as error:
            log.warning('Cannot inspect %s: %s', candidate, error)
    return files


def resolve_file(root, files, identity):
    if identity not in files:
        raise ValueError('FILE_UNAVAILABLE')
    path = (root / files[identity]['relativePath']).resolve(strict=True)
    if not path.is_relative_to(root) or not path.is_file() or path.suffix != '.sky':
        raise ValueError('PATH_FORBIDDEN')
    if path.stat().st_size != 1024:
        raise ValueError('INVALID_DUMP')
    return path


def public_state(observed, root, files):
    # Never send absolute game-PC paths to the server.
    paths = {}
    for identity in files:
        try:
            paths[str(resolve_file(root, files, identity))] = identity
        except (OSError, ValueError):
            pass
    return {**{k: observed[k] for k in ('epoch', 'revision', 'enabled', 'capacity')},
            'slots': [{k: slot[k] for k in ('index', 'toyId', 'variantId')} |
                      {'fileId': paths.get(slot.get('path'))} for slot in observed['slots']]}


def execute(command, socket_path, root, files, session):
    if command['session'] != session:
        raise ValueError('STALE_SESSION')
    if command['expiresAt'] <= int(time.time() * 1000):
        raise ValueError('EXPIRED')
    payload = {k: command[k] for k in ('commandId', 'epoch', 'expectedRevision', 'expiresAt', 'command')}
    payload['version'] = 1
    if command['command'] == 'loadFigure':
        payload['path'] = str(resolve_file(root, files, command['fileId']))
    elif command['command'] == 'removeFigure':
        payload['slot'] = command['slot']
    elif command['command'] != 'clearAll':
        raise ValueError('UNKNOWN_COMMAND')
    return request(socket_path, payload)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('Server redirects are forbidden')


def exchange(url, token, payload):
    req = urllib.request.Request(url + '/api/bridge/exchange',
        data=json.dumps(payload).encode(), headers={'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token}, method='POST')
    # Ten seconds, not three: republishing the inventory is the one large request of
    # this loop, and a remote server across a VPN is not a local socket.
    with urllib.request.build_opener(NoRedirect).open(req, timeout=10) as response:
        return json.loads(response.read(2_000_000))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True, help='JSON file outside the figure root')
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text())
    root = Path(config['skylandersRoot']).expanduser().resolve(strict=True)
    config['socket'] = str(Path(config['socket']).expanduser().absolute())
    if not root.is_dir():
        raise ValueError('Figure root must be a directory')
    if Path(args.config).resolve().is_relative_to(root):
        raise ValueError('Configuration must be outside the figure root')
    url = config['serverUrl'].rstrip('/')
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ('http', 'https') or not parsed.hostname or parsed.username or parsed.query or parsed.fragment:
        raise ValueError('Invalid server URL')
    if not config.get('token'):
        raise ValueError('Missing dedicated connector token')
    session, result = str(uuid.uuid4()), None
    files, scanned, published = {}, 0, None
    while True:
        if time.monotonic() - scanned > 10:
            files, scanned = inventory(root), time.monotonic()
        digest = inventory_digest(files)
        try:
            observed = public_state(state(config['socket']), root, files)
            availability = 'READY' if observed['enabled'] else 'PORTAL_DISABLED'
        except (OSError, ValueError, RuntimeError, KeyError) as error:
            observed, availability = None, 'CEMU_UNAVAILABLE'
            log.debug('Cemu unavailable: %s', error)
        # The inventory is 104 KB for 702 dumps and this loop runs twice a second: send it
        # only when it changed, or when the server says it no longer has it.
        payload = {'version': 1, 'session': session, 'availability': availability,
                   'state': observed, 'filesDigest': digest, 'result': result}
        if digest != published:
            payload['files'] = list(files.values())
        try:
            answer = exchange(url, config['token'], payload)
            published = None if answer.get('needFiles') else digest
            result = None
            command = answer.get('command')
            if command:
                try:
                    reply = execute(command, config['socket'], root, files, session)
                    result = {'commandId': command['commandId'], 'ok': reply['ok'], 'error': reply['error']}
                except (OSError, ValueError, KeyError) as error:
                    code = str(error) if isinstance(error, ValueError) else 'CEMU_UNAVAILABLE'
                    log.warning('Portal command failed: %s', error)
                    result = {'commandId': command['commandId'], 'ok': False, 'error': code}
                continue  # Immediately publish confirmed state and outcome.
        except (OSError, ValueError) as error:
            log.warning('Server unavailable; pending actions discarded: %s', error)
            # A new session starts with an empty server-side inventory: republish it.
            session, result, published = str(uuid.uuid4()), None, None
        time.sleep(0.5)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
    try:
        main()
    except KeyboardInterrupt:
        pass
