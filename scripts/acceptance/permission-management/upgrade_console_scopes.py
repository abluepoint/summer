"""Versioned summer-ui scope migration. No credential is written to output or disk."""
import argparse, json, os, sys, uuid, hashlib, http.cookiejar, urllib.parse, urllib.request, urllib.error
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apply', action='store_true')
parser.add_argument('--evidence', type=Path, required=True)
parser.add_argument('--restore', type=Path)
args = parser.parse_args()
if args.evidence.exists():
    parser.error('evidence must be a new file; preserve the original migration receipt')
if args.restore and args.evidence.resolve() == args.restore.resolve():
    parser.error('restore input and output must differ')
base = os.environ['SUMMER_MIGRATION_BASE_URL'].rstrip('/')
token = os.environ.get('SUMMER_MIGRATION_BEARER')
csrf_header = csrf_token = None
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args): return None
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
expected = json.loads(Path(__file__).with_name('console-scopes.json').read_text())['scopes']
report = {'clientId':'summer-ui', 'state':'STARTING', 'requests':[], 'scopeSpecification':expected, 'scopeCsvLimit':4096, 'toolSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'operatorMode':'bearer' if token else 'local-console-session'}

def save():
    args.evidence.parent.mkdir(parents=True, exist_ok=True)
    args.evidence.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')

def request(path, method='GET', body=None):
    request_id = 'console-client-migration-'+uuid.uuid4().hex
    headers = {'X-Request-Id':request_id}
    if token: headers['Authorization']='Bearer '+token
    elif method not in ['GET','HEAD']: headers[csrf_header]=csrf_token
    if body is not None: headers['Content-Type']='application/json'
    req=urllib.request.Request(base+path, method=method, headers=headers,
        data=None if body is None else json.dumps(body).encode())
    try: response=opener.open(req, timeout=20)
    except urllib.error.HTTPError as error: response=error
    with response:
        status=response.status
        report['requests'].append({'method':method,'path':path,'status':status,
            'requestId':response.headers.get('X-Request-Id', request_id)})
        save()
        data=json.loads(response.read())
        if status != 200: raise RuntimeError('management_http_'+str(status))
        if not isinstance(data, dict) or data.get('success') is False or 'data' not in data:
            raise RuntimeError('invalid_management_response')
        return data['data']

def current():
    clients=request('/api/security/oauth-clients')['items']
    matches=[c for c in clients if c['clientId']=='summer-ui']
    if len(matches)!=1: raise RuntimeError('exact_console_client_not_found')
    return matches[0]

try:
    if not token:
        username=os.environ.get('SUMMER_MIGRATION_USERNAME')
        password=os.environ.get('SUMMER_MIGRATION_PASSWORD')
        if not username or not password: raise RuntimeError('operator_credentials_required')
        with opener.open(base+'/csrf',timeout=20) as response: csrf=json.loads(response.read())
        form=urllib.parse.urlencode({'username':username,'password':password,csrf['parameterName']:csrf['token']}).encode()
        login=urllib.request.Request(base+'/login',data=form,headers={'Content-Type':'application/x-www-form-urlencoded'},method='POST')
        try: response=opener.open(login,timeout=20)
        except urllib.error.HTTPError as error: response=error
        with response:
            if response.status!=302 or '?error' in response.headers.get('Location',''): raise RuntimeError('operator_login_failed')
        # Login rotates the CSRF secret. Fetch the authenticated session's actual token.
        with opener.open(base+'/csrf',timeout=20) as response: csrf=json.loads(response.read())
        csrf_header,csrf_token=csrf['headerName'],csrf['token']
    before=current(); report['before']=before
    if args.restore:
        prior=json.loads(args.restore.read_text())
        if prior.get('clientId')!='summer-ui' or not prior.get('after'):
            raise RuntimeError('invalid_restore_evidence')
        desired=prior['before']['scopes']
        if set(before['scopes'])==set(desired):
            report.update(state='ALREADY_RESTORED', after=before);save();sys.exit(0)
        if (before['version']!=prior['after']['version'] or
                set(before['scopes'])!=set(prior['after']['scopes'])):
            raise RuntimeError('restore_concurrent_change_requires_review')
    else:
        desired=sorted(set(before['scopes']) | set(expected))
        if set(desired)==set(before['scopes']):
            report.update(state='ALREADY_CURRENT',after=before);save();sys.exit(0)
    if before['status']!='ACTIVE': raise RuntimeError('disabled_console_is_not_reactivated')
    if len(','.join(desired))>4096: raise RuntimeError('combined_scope_storage_limit_requires_review')
    report['desiredScopes']=desired
    report['missingScopes']=sorted(set(desired)-set(before['scopes']))
    report['state']='REVIEWABLE';save()
    if not args.apply:
        print('REVIEWABLE: inspect the credential-free evidence, then use --apply');sys.exit(0)
    # This existing endpoint has optimistic versioning, not an idempotency-key protocol.
    # A completed state is reconciled by GET; an unknown response is never blindly replayed.
    try:
        request('/api/security/oauth-clients/summer-ui','PATCH',
                {'version':before['version'],'scopes':desired})
    except (OSError, TimeoutError):
        report['responseUncertain']=True
    after=current();report['after']=after
    invariant=['clientId','clientName','status','redirectUris','postLogoutRedirectUris',
               'authenticationMethods','grantTypes']
    if any(before[k]!=after[k] for k in invariant):
        raise RuntimeError('unexpected_client_metadata_change_requires_review')
    if set(after['scopes'])!=set(desired) or after['version']!=before['version']+1:
        raise RuntimeError('scope_or_version_conflict_requires_review')
    report['state']='RESTORED' if args.restore else 'APPLIED';save()
    print(report['state']+': exact summer-ui scope set verified; reauthenticate using PKCE')
except Exception as error:
    report.update(state='FAILED',errorType=type(error).__name__,
                  reason=str(error) if isinstance(error,RuntimeError) else 'redacted')
    save();print('FAILED: inspect credential-free evidence',file=sys.stderr);sys.exit(1)
