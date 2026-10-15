"""New-candidate replay limited to T23-D1/D2/D3, with real HTTP/MySQL/Chrome."""
import base64, concurrent.futures, hashlib, json, os, subprocess, threading, time, uuid
from pathlib import Path
from rotation_environment import RotationEnvironment, Client, ROOT, OUT, immutable_candidate
from grants import bootstrap, create_role, unwrap

NODE='/opt/homebrew/opt/node@22/bin/node';SCRIPT=Path(__file__).resolve().parent
result={'stage':'setup','checks':[],'networkMocking':False};env=None;ui=None;browser=None
def check(name,value,**facts):
    result['checks'].append({'name':name,'passed':bool(value),**facts});print(name,'PASS' if value else 'FAIL',flush=True)
def rotate(client,etag,port=21081,key=None):
    rid='t23-'+uuid.uuid4().hex
    s,d,h=client.request(port,'/api/security/keys:rotate','POST',{'retentionDays':1},headers={'If-Match':etag,'Idempotency-Key':key or uuid.uuid4().hex,'X-Request-Id':rid})
    return s,d,h,rid
def metadata(client):
    s,d,h=client.request(21081,'/api/security/keys');assert s==200
    return unwrap(d)['items'],h['ETag']
def error_code(d):
    return d['error']['code'] if isinstance(d,dict) and isinstance(d.get('error'),dict) else None
def audited(rid,event,code=None):
    time.sleep(.3)
    return any(e['event']==event and e.get('requestId')==rid and e.get('operatorMatchesAdmin') and (code is None or e.get('result')==code) for e in env.security_events)
def run_browser(mode):
    global browser
    e=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_ROTATION_MODE=mode)
    browser=subprocess.Popen([NODE,str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=e,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    code=browser.wait(timeout=150)
    result.setdefault('browsers',[]).append({'exitCode':code,**json.loads((OUT/f'browser-{mode}.json').read_text())})
def input_hashes():
    paths=list((ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/key').glob('*.java'))+list((ROOT/'summer-ui/src/pages/security-keys').glob('*.ts*'))
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
candidate=immutable_candidate();before=input_hashes()
try:
    env=RotationEnvironment();env.setup();env.start(21081,jar=candidate)
    admin=Client().login(21081,env.username,env.password);bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password)
    role=create_role(admin,'ent-acme',['GET:/api/security/keys','POST:/api/security/keys:rotate','security.key.read','security.key.rotate','menu.securityKeys.read'],'T23 repair replay')
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles');assert s==200
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles','PUT',{'roleIds':unwrap(d)['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':h.get('ETag'),'Idempotency-Key':uuid.uuid4().hex});assert s==200
    admin=Client().login(21081,env.username,env.password);env.start(21082,jar=candidate)
    items,etag=metadata(admin);old_kid=next(i['kid'] for i in items if i['status']=='ACTIVE')
    state=env.sql('SELECT kid,version,status FROM summer_security_keys ORDER BY kid',env.schema)
    env.phase='rollback';result['stage']='rollback';key=uuid.uuid4().hex
    env.sql("CREATE TRIGGER t23_fail_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T23 controlled insert failure'",env.schema)
    s,d,h,rid=rotate(admin,etag,key=key)
    check('database_insert_fault_returns_failure',s==500 and error_code(d)=='SUMMER_INTERNAL_ERROR',httpStatus=s)
    check('failed_rotation_database_state_unchanged',env.sql('SELECT kid,version,status FROM summer_security_keys ORDER BY kid',env.schema)==state)
    check('failed_rotation_audit_operator_result',audited(rid,'security_key_rotation_failed','FAILED'))
    env.sql('DROP TRIGGER t23_fail_insert',env.schema)
    s,d,h,rid=rotate(admin,etag,key=key);claimed=unwrap(d).get('activeKid') if s==200 else None
    rows=env.sql('SELECT kid,status FROM summer_security_keys',env.schema).splitlines()
    matches=[row for row in rows if row.split('\t')[0]==claimed]
    active=env.sql("SELECT kid FROM summer_security_keys WHERE status='ACTIVE'",env.schema)
    check('same_idempotency_key_retry_commits_reported_key',s==200 and len(matches)==1 and claimed==active and active!=old_kid,httpStatus=s,reportedKeyRowCount=len(matches),reportedKeyIsActive=claimed==active)
    fresh=Client().login(21082,env.username,env.password);encoded=fresh.token.split('.')[0]
    actual_kid=json.loads(base64.urlsafe_b64decode(encoded+'='*((4-len(encoded)%4)%4)))['kid']
    s,jwks,_=Client().request(21082,'/oauth2/jwks',bearer=False)
    proof=subprocess.run([NODE,str(SCRIPT.parent/'keys/crypto.mjs')],input=json.dumps({'token':fresh.token,'jwks':jwks}),text=True,capture_output=True,check=True)
    check('recovered_retry_key_signs_verifiable_token_on_other_node',s==200 and actual_kid==claimed and json.loads(proof.stdout).get('signatureValid'))
    result['stage']='concurrency';env.phase='concurrency';_,etag=metadata(admin)
    env.sql('CREATE TRIGGER t23_slow_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW DO SLEEP(2)',env.schema)
    barrier=threading.Barrier(2)
    def compete(port):
        client=Client();client.token=admin.token;barrier.wait(timeout=5)
        return rotate(client,etag,port)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:replies=list(pool.map(compete,(21081,21082)))
    env.sql('DROP TRIGGER t23_slow_insert',env.schema)
    statuses=sorted(r[0] for r in replies)
    check('dual_node_same_version_one_success_one_conflict',statuses==[200,412],httpStatuses=statuses)
    count=int(env.sql("SELECT COUNT(*) FROM summer_security_keys WHERE status='ACTIVE'",env.schema))
    check('dual_node_rotation_has_exactly_one_active_key',count==1,activeKeyCount=count)
    losers=[r for r in replies if r[0]==412]
    check('concurrent_loser_has_conflict_audit',len(losers)==1 and audited(losers[0][3],'security_key_rotation_failed','SUMMER_PRECONDITION_FAILED'))
    check('concurrent_conflict_response_contract',len(losers)==1 and error_code(losers[0][1])=='SUMMER_PRECONDITION_FAILED' and losers[0][1].get('requestId')==losers[0][2].get('X-Request-Id'))
    result['stage']='browser';env.phase='browser'
    ui=subprocess.Popen([NODE,str(SCRIPT.parent/'identity/ui-server.mjs')],cwd=ROOT/'summer-ui',env=os.environ,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    run_browser('conflict-replay')
    env.sql("CREATE TRIGGER t23_fail_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T23 controlled insert failure'",env.schema)
    run_browser('fault');env.sql('DROP TRIGGER t23_fail_insert',env.schema)
    check('no_sensitive_runtime_output',not env.sensitive_log_detected)
    result['stage']='complete'
except Exception as error:
    import traceback
    result['failureType']=type(error).__name__;result['failureFrames']=[{'file':Path(f.filename).name,'line':f.lineno} for f in traceback.extract_tb(error.__traceback__)]
finally:
    for p in (browser,ui):
        if p and p.poll() is None:
            p.terminate()
            try:p.wait(timeout=15)
            except subprocess.TimeoutExpired:p.kill();p.wait(timeout=5)
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
    after=input_hashes();result['keyInputsStable']=before==after;result['changedKeyPaths']=[p for p in before if before[p]!=after.get(p)]
    result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest();result['audit']=env.security_events if env else [];result['sensitiveFindings']=env.sensitive_findings if env else []
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int(result['stage']!='complete'),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and result.get('cleanup')=='VERIFIED' and all(b.get('passed') for b in result.get('browsers',[]))
    (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'stage':result['stage'],'counts':result['counts'],'cleanup':result.get('cleanup'),'passed':result['passed']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
