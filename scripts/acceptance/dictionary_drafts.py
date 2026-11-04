"""Explicit compatibility adapter for existing acceptance clients, using management HTTP."""
import re
import json

def with_draft_revision(client, port, path, method, body):
    if not isinstance(body, dict):
        return body
    lifecycle = re.fullmatch(r'(/api/(?:v1/enterprises/[^/]+/)?dictionaries/[^/]+)/(?:versions/)?(publish|disable|deactivate|rollback)', path)
    if lifecycle and method == 'POST':
        cache_key = (port, path, json.dumps(body, sort_keys=True))
        receipts = getattr(client, '_dictionary_lifecycle_receipts', {})
        if body.get('idempotencyKey') and cache_key in receipts:
            return dict(receipts[cache_key])
        status, envelope, _ = client.request(port, lifecycle.group(1) + '/draft')
        if status != 200:
            return body
        draft = envelope.get('data', envelope)
        result = dict(body)
        if not any(field in result for field in ['expectedVersion', 'version', 'revision']):
            result['expectedVersion'] = draft['publishedVersion']
        result.setdefault('expectedEffectiveRevision', draft['effectiveRevision'])
        if lifecycle.group(2) == 'publish':
            result.setdefault('expectedDraftRevision', draft['draftRevision'])
        if body.get('idempotencyKey'):
            receipts[cache_key] = dict(result)
            client._dictionary_lifecycle_receipts = receipts
        return result
    if 'expectedDraftRevision' in body:
        return body
    match = re.fullmatch(r'(/api/(?:v1/enterprises/[^/]+/)?dictionaries/[^/]+)(/items(?:/[^/]+)?)?', path)
    if not match or not (method == 'PATCH' or (method == 'POST' and match.group(2) == '/items')):
        return body
    status, envelope, _ = client.request(port, match.group(1) + '/draft')
    if status != 200:
        return body  # Keep the mutation's original permission/not-found assertion.
    draft = envelope.get('data', envelope)
    return dict(body, expectedDraftRevision=draft['draftRevision'])
