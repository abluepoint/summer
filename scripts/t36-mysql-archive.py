#!/usr/bin/env python3
"""Encrypt MySQL dumps before writing; restore decrypted bytes only through stdin."""
import datetime
import os
from pathlib import Path
import re
import subprocess
import sys


def run(arguments, data=None, env=None):
    result = subprocess.run(arguments, input=data, env=env, capture_output=True)
    if result.returncode:
        raise RuntimeError('archive subprocess failed')
    return result.stdout


def main():
    mode, report_dir = sys.argv[1:]
    required = ['T36_DB_HOST', 'T36_DB_PORT', 'T36_DB_NAME', 'T36_DB_USER',
                'T36_DB_PASSWORD', 'T36_BACKUP_PASSPHRASE']
    if any(not os.environ.get(key) for key in required):
        raise ValueError('missing archive environment')
    source = os.environ['T36_DB_NAME']
    if not re.fullmatch(r'[A-Za-z0-9_]+', source):
        raise ValueError('invalid source schema')
    env = os.environ.copy()
    env['MYSQL_PWD'] = env['T36_DB_PASSWORD']
    connection = ['--host=' + env['T36_DB_HOST'], '--port=' + env['T36_DB_PORT'],
                  '--user=' + env['T36_DB_USER']]
    crypto = ['openssl', 'enc', '-aes-256-cbc', '-pbkdf2', '-iter', '200000',
              '-pass', 'env:T36_BACKUP_PASSPHRASE']
    if mode == 'backup':
        dump = run(['mysqldump', *connection, '--single-transaction', '--routines', '--events',
                    '--triggers', '--hex-blob', '--no-tablespaces', '--set-gtid-purged=OFF', source], env=env)
        if not dump:
            raise ValueError('empty backup')
        encrypted = run(crypto + ['-salt'], dump, env)
        del dump
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        target = Path(env.get('T36_BACKUP_FILE', str(Path(report_dir) / ('summer-' + stamp + '.sql.enc'))))
        target.parent.mkdir(parents=True, exist_ok=True)
        # Refuse to replace an existing backup, and restrict access from creation.
        with os.fdopen(os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as output:
            output.write(encrypted)
        print('encrypted_backup=' + str(target))
    elif mode == 'restore':
        target = env.get('T36_RESTORE_DATABASE', '')
        if env.get('T36_ALLOW_RESTORE') != '1' or not re.fullmatch(r'[A-Za-z0-9_]+_t36_restore', target):
            raise ValueError('restore opt-in or isolated schema missing')
        backup = Path(env['T36_BACKUP_FILE']).read_bytes()
        dump = run(crypto + ['-d'], backup, env)
        mysql = ['mysql', *connection, '--batch', '--skip-column-names']
        # CREATE without IF NOT EXISTS prevents overwriting an existing rehearsal schema.
        run(mysql, ('CREATE DATABASE `' + target + '`;').encode(), env)
        run(mysql + [target], dump, env)
        del dump
        count = run(mysql + [target], b'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();', env).decode().strip()
        if not count.isdigit() or int(count) < 1:
            raise ValueError('restored schema is empty')
        print('restored_database=' + target + ' tables=' + count)
    else:
        raise ValueError('unknown archive mode')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('T36 archive failed; check environment, subprocess availability and isolated target', file=sys.stderr)
        sys.exit(1)
