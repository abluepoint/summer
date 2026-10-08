#!/usr/bin/env python3
"""Read-only T36 probe. Authentication comes from the environment, never argv/files."""
import json
import os
import sys
import urllib.request

try:
    url, mode = sys.argv[1:]
    headers = {}
    token = os.environ.get('T36_ACCESS_TOKEN')
    if token:
        if '\n' in token or '\r' in token:
            raise ValueError('invalid authentication')
        headers['Authorization'] = 'Bearer ' + token
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=15) as response:
        payload = json.load(response)
    data = payload.get('data') or payload
    if mode == 'status':
        state = data.get('status')
        if state not in {'UP', 'READY'}:
            raise ValueError('runtime not ready')
        print(state)
    elif mode == 'diagnostics':
        components = data.get('components')
        metrics = data.get('metrics') or {}
        if payload.get('success') is not True or not isinstance(components, list):
            raise ValueError('invalid diagnostics')
        if not isinstance(metrics.get('registeredMeters'), int):
            raise ValueError('missing metrics')
        if any(not isinstance(item, dict) or not item.get('name') or not item.get('status') for item in components):
            raise ValueError('invalid component')
        print('runtime diagnostics available')
    else:
        raise ValueError('unknown mode')
except Exception:
    print('runtime probe failed', file=sys.stderr)
    sys.exit(1)
