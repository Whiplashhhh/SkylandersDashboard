#!/usr/bin/env python3
"""Destructive only to a dedicated test copy: Cemu may save the supplied figure."""
import argparse
import json
import time
import uuid
from pathlib import Path
from portal_client import request, state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--socket', required=True)
    parser.add_argument('--test-copy', required=True)
    args = parser.parse_args()
    initial = state(args.socket)
    assert not initial['slots'], 'Use a separate empty test Cemu instance'
    def command(name, **fields):
        current = state(args.socket)
        return dict(version=1, command=name, commandId=str(uuid.uuid4()), epoch=current['epoch'],
                    expectedRevision=current['revision'], expiresAt=int(time.time()*1000)+2000, **fields)
    old = command('clearAll')
    load = command('loadFigure', path=str(Path(args.test_copy).resolve()))
    reply = request(args.socket, load)
    assert reply['ok'], reply
    assert request(args.socket, load) == reply, 'Retry changed response'
    assert len(state(args.socket)['slots']) == 1, 'Retry duplicated load'
    assert request(args.socket, old)['error'] == 'STALE_STATE'
    expired = command('clearAll'); expired['expiresAt'] = 0
    assert request(args.socket, expired)['error'] == 'EXPIRED'
    epoch = command('clearAll'); epoch['epoch'] = 'old'
    assert request(args.socket, epoch)['error'] == 'STALE_SESSION'
    assert request(args.socket, command('removeFigure', slot=16))['error'] == 'INVALID_SLOT'
    assert request(args.socket, command('loadFigure', path='/missing.sky'))['error'] == 'FILE_UNAVAILABLE'
    assert request(args.socket, command('loadFigure', path='/etc/passwd'))['error'] == 'PATH_FORBIDDEN'
    assert request(args.socket, command('removeFigure', slot=reply['assignedSlot']))['ok']
    assert not state(args.socket)['slots']
    assert request(args.socket, {'version': 99, 'command': 'getState'})['error'] == 'INCOMPATIBLE_VERSION'
    print(json.dumps({'passed': 10, 'epoch': initial['epoch'], 'finalRevision': state(args.socket)['revision']}))


if __name__ == '__main__': main()
