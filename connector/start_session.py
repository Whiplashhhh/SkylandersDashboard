#!/usr/bin/env python3
"""Start patched Cemu and its outgoing connector without opening the USB window."""
import argparse
import json
import os
from pathlib import Path
import socket
import stat
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--cemu', required=True, help='Patched Cemu executable')
    parser.add_argument('--game', help='Wii U game path')
    args = parser.parse_args()
    config_path = Path(args.config).expanduser().resolve(strict=True)
    config = json.loads(config_path.read_text())
    root = Path(config['skylandersRoot']).expanduser().resolve(strict=True)
    if not root.is_dir() or config_path.is_relative_to(root):
        raise ValueError('Configuration must be outside the figure folder')
    local = Path(config['socket']).expanduser().absolute()
    if local.is_relative_to(root):
        raise ValueError('Socket must be outside the figure folder')
    local.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    parent = local.parent.lstat()
    if not stat.S_ISDIR(parent.st_mode) or parent.st_uid != os.getuid() or parent.st_mode & 0o077:
        raise ValueError('Socket directory must be owned by you with mode 0700')
    if local.exists():
        metadata = local.lstat()
        if not stat.S_ISSOCK(metadata.st_mode) or metadata.st_uid != os.getuid():
            raise ValueError('Refusing to replace a non-socket or foreign socket')
        with socket.socket(socket.AF_UNIX) as probe:
            probe.settimeout(1)
            try:
                probe.connect(str(local))
            except ConnectionRefusedError:
                # A crash left our socket behind. Never touch any figure file.
                if local.lstat().st_ino == metadata.st_ino:
                    local.unlink()
            else:
                raise ValueError('A Cemu bridge is already running')
    env = os.environ | {'CEMU_PORTAL_ROOT': str(root), 'CEMU_PORTAL_SOCKET': str(local)}
    cemu = [str(Path(args.cemu).expanduser().resolve(strict=True))]
    if args.game:
        cemu += ['-g', str(Path(args.game).expanduser().resolve(strict=True))]
    emulator = subprocess.Popen(cemu, env=env)
    connector = subprocess.Popen([sys.executable, str(Path(__file__).with_name('skylanders_connector.py')),
                                  '--config', str(config_path)])
    try:
        return emulator.wait()
    except KeyboardInterrupt:
        # Cemu owns saving and shutdown. Do not force-kill it on a terminal interrupt.
        print('Close Cemu normally to finish saving.', file=sys.stderr)
        return emulator.wait()
    finally:
        connector.terminate()
        try: connector.wait(timeout=5)
        except subprocess.TimeoutExpired: connector.kill(); connector.wait()


if __name__ == '__main__':
    raise SystemExit(main())
