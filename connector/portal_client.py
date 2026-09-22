#!/usr/bin/env python3
"""Version 1 local client. Does not read or write figure contents."""
import argparse
import json
import socket
import time
import uuid


def request(socket_path, payload):
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        connection.settimeout(3)
        connection.connect(str(socket_path))
        connection.sendall(json.dumps(payload, ensure_ascii=False).encode() + b'\n')
        data = bytearray()
        while b'\n' not in data:
            chunk = connection.recv(8192)
            if not chunk:
                raise ConnectionError('Cemu closed the connection')
            data.extend(chunk)
            if len(data) > 65536:
                raise ValueError('Cemu response too large')
        return json.loads(data.split(b'\n', 1)[0])


def state(socket_path):
    result = request(socket_path, {'version': 1, 'command': 'getState'})
    if not result.get('ok'):
        raise RuntimeError(result.get('error', 'Invalid Cemu response'))
    return result['state']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--socket', required=True)
    parser.add_argument('command', choices=['getState', 'getCapabilities', 'loadFigure', 'removeFigure', 'clearAll'])
    parser.add_argument('--path', help='Absolute path, only for loadFigure')
    parser.add_argument('--slot', type=int, help='Observed internal slot, only for removeFigure')
    args = parser.parse_args()
    payload = {'version': 1, 'command': args.command}
    if args.command not in ('getState', 'getCapabilities'):
        observed = state(args.socket)
        payload.update(commandId=str(uuid.uuid4()), epoch=observed['epoch'],
                       expectedRevision=observed['revision'], expiresAt=int(time.time() * 1000) + 2000)
    if args.path:
        payload['path'] = args.path
    if args.slot is not None:
        payload['slot'] = args.slot
    result = request(args.socket, payload)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result.get('ok') else 1


if __name__ == '__main__':
    raise SystemExit(main())
