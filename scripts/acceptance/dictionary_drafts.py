"""Explicit compatibility adapter for existing acceptance clients, using management HTTP."""
import re

def with_draft_revision(client, port, path, method, body):
    if not isinstance(body, dict) or 'expectedDraftRevision' in body:
        return body
    match = re.fullmatch(r'(/api/(?:v1/enterprises/[^/]+/)?dictionaries/[^/]+)(/items(?:/[^/]+)?)?', path)
    if not match or not (method == 'PATCH' or (method == 'POST' and match.group(2) == '/items')):
        return body
    status, envelope, _ = client.request(port, match.group(1) + '/draft')
    if status != 200:
        return body  # Keep the mutation's original permission/not-found assertion.
    draft = envelope.get('data', envelope)
    return dict(body, expectedDraftRevision=draft['draftRevision'])
