"""T23 real dual-node rotation; fault fixtures touch only the run-owned schema."""
import base64, concurrent.futures, hashlib, json, os, subprocess, sys, threading, time, uuid
from pathlib import Path
from rotation_environment import RotationEnvironment, Client, ROOT, OUT, immutable_candidate
from grants import bootstrap, create_role, unwrap

SCRIPT=Path(__file__).resolve().parent
NODE=os.environ.get('SUMMER_IDENTITY_NODE','/opt/homebrew/opt/node@22/bin/node')
result={'stage':'setup','checks':[],'networkMocking':False,'businessAuthorizationSqlWrites':False}
env=None;ui=None;browser=None

def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts})
    (OUT/'progress.json').write_text(json.dumps(result,indent=2))
    print(name,'PASS' if passed else 'FAIL',flush=True)

def part(token,index):
    s=token.split('.')[index]
    return json.loads(base64.urlsafe_b64decode(s+'='*((4-len(s)%4)%4)))

def verify(token,jwks):
    r=subprocess.run([NODE,str(SCRIPT.parent/'keys/crypto.mjs')],input=json.dumps({'token':token,'jwks':jwks}),text=True,capture_output=True,check=True)
    return json.loads(r.stdout).get('signatureValid',False)

def error_code(data):
    e=data.get('error') if isinstance(data,dict) else None
    return e.get('code') if isinstance(e,dict) else None

def contract(data,headers):
    return bool(error_code(data)) and error_code(data).startswith('SUMMER_') and bool(data.get('requestId')) and data['requestId']==headers.get('X-Request-Id') and data.get('success') is False

def state(client,port=21081):
    s,d,h=client.request(port,'/api/security/keys')
    assert s==200,'metadata_status_'+str(s)
    return unwrap(d)['items'],h.get('ETag')

def rotate(client,etag,port=21081,key=None,days=1):
    rid='t23-'+uuid.uuid4().hex
    s,d,h=client.request(port,'/api/security/keys:rotate','POST',{'retentionDays':days},headers={'If-Match':etag,'Idempotency-Key':key or uuid.uuid4().hex,'X-Request-Id':rid})
    return s,d,h,rid

def db_state():
    return env.sql('SELECT kid,version,status,COALESCE(CAST(grace_until AS CHAR),\'\') FROM summer_security_keys ORDER BY kid',env.schema)

def audit_matches(rid,event,code=None):
    time.sleep(.3)
    return any(e['event']==event and e.get('requestId')==rid and e.get('operatorMatchesAdmin') and (code is None or e.get('result')==code) for e in env.security_events)

def inputs():
    paths=list((ROOT/'summer-sample/src/main/java/com/abluepoint/summer/sample/key').glob('*.java'))+list((ROOT/'summer-ui/src/pages/security-keys').glob('*.ts*'))
    return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}

def run_browser(mode):
    global browser
    e=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_E2E_READER=env.denied_username,SUMMER_E2E_READER_PASSWORD=env.denied_password,SUMMER_ROTATION_MODE=mode)
    browser=subprocess.Popen([NODE,str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=e,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    code=browser.wait(timeout=180)
    receipt=json.loads((OUT/f'browser-{mode}.json').read_text())
    result.setdefault('browsers',[]).append({'exitCode':code,**receipt})

candidate=immutable_candidate();before=inputs()
try:
    env=RotationEnvironment();env.setup();env.start(21081,jar=candidate)
    admin=Client().login(21081,env.username,env.password);bootstrap(admin,env.username)
    admin=Client().login(21081,env.username,env.password)
    role=create_role(admin,'ent-acme',['GET:/api/security/keys','POST:/api/security/keys:rotate','security.key.read','security.key.rotate','menu.securityKeys.read'],'T23 manual rotation')
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles');assert s==200
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles','PUT',{'roleIds':unwrap(d)['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':h.get('ETag'),'Idempotency-Key':uuid.uuid4().hex});assert s==200
    admin=Client().login(21081,env.username,env.password)
    s,_,_=admin.request(21081,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'});assert s==200
    reader=Client().login(21081,env.denied_username,env.denied_password)
    env.start(21082,jar=candidate);other=Client().login(21082,env.username,env.password)
    check('production_mysql_key_migration',env.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE script='V5__security_keys.sql' AND success=1",env.schema)=='1')
    result['stage']='rotation';env.phase='rotation'
    items,etag=state(admin);old_token=admin.token;old_kid=part(old_token,0)['kid']
    s,d,h,rid=rotate(admin,etag,days=0)
    check('invalid_retention_rejected',s==400 and error_code(d)=='SUMMER_KEY_ROTATION_INVALID',httpStatus=s)
    check('invalid_response_contract',contract(d,h))
    check('invalid_audit_operator_result',audit_matches(rid,'security_key_rotation_failed','SUMMER_KEY_ROTATION_INVALID'))
    s,d,h,rid=rotate(reader,etag)
    check('ungranted_rotation_denied',s==403,httpStatus=s)
    check('denied_response_contract',contract(d,h))
    s,d,h,rid=rotate(admin,etag)
    check('admin_creates_and_switches_active_key',s==200 and unwrap(d).get('activeKid')!=old_kid,httpStatus=s)
    check('successful_rotation_audit_operator_result',audit_matches(rid,'security_key_rotated'))
    new_kid=unwrap(d)['activeKid'];new_version=unwrap(d)['version']
    for port in (21081,21082):
        fresh=Client().login(port,env.username,env.password)
        s,jwks,h=Client().request(port,'/oauth2/jwks',bearer=False)
        check(f'node_{port}_signs_new_kid',part(fresh.token,0)['kid']==new_kid)
        check(f'node_{port}_new_token_verifies',s==200 and verify(fresh.token,jwks))
        check(f'node_{port}_old_valid_token_still_verifies',part(old_token,1)['exp']>time.time() and verify(old_token,jwks))
        check(f'node_{port}_jwks_public_only',all(not(set(k)&{'d','p','q','dp','dq','qi','oth','k'}) for k in jwks['keys']))
    items,next_etag=state(admin)
    old_item=next(x for x in items if x['kid']==old_kid)
    check('old_key_grace_metadata_and_version',old_item['status']=='GRACE' and bool(old_item['graceUntil']) and max(i['version'] for i in items)==new_version)
    s,d,h,rid=rotate(admin,etag)
    check('stale_version_conflict',s==412 and error_code(d)=='SUMMER_PRECONDITION_FAILED',httpStatus=s)
    check('conflict_response_contract',contract(d,h))
    check('conflict_audit_operator_result',audit_matches(rid,'security_key_rotation_failed','SUMMER_PRECONDITION_FAILED'))
    # Real DB commit failure, without changing production application code or permissions.
    result['stage']='rollback';env.phase='rollback';snapshot=db_state();fault_key=uuid.uuid4().hex
    env.sql("CREATE TRIGGER t23_fail_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T23 controlled insert failure'",env.schema)
    s,d,h,rid=rotate(admin,next_etag,key=fault_key)
    check('database_insert_fault_returns_failure',s>=500,httpStatus=s,errorCode=error_code(d))
    check('database_insert_fault_contract',contract(d,h))
    check('failed_rotation_database_state_unchanged',db_state()==snapshot)
    check('failed_rotation_audit_operator_result',audit_matches(rid,'security_key_rotation_failed','FAILED'))
    env.sql('DROP TRIGGER t23_fail_insert',env.schema)
    fresh=Client().login(21082,env.username,env.password)
    check('failed_rotation_preserves_effective_signing_key',part(fresh.token,0)['kid']==new_kid)
    s,d,h,rid=rotate(admin,next_etag,key=fault_key)
    claimed=unwrap(d).get('activeKid') if s==200 else None
    actual=env.sql("SELECT kid FROM summer_security_keys WHERE status='ACTIVE'",env.schema)
    check('same_idempotency_key_retry_commits_reported_key',s==200 and claimed==actual and actual!=new_kid,httpStatus=s,reportedKeyPersisted=claimed==actual)
    # UI tests use fresh DB state even when the preceding retry exposed a defect.
    result['stage']='browser';env.phase='browser'
    ui=subprocess.Popen([NODE,str(SCRIPT.parent/'identity/ui-server.mjs')],cwd=ROOT/'summer-ui',env=os.environ,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    assert ui.stdout.readline().strip()=='UI_READY'
    run_browser('normal')
    env.sql("CREATE TRIGGER t23_fail_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T23 controlled insert failure'",env.schema)
    run_browser('fault');env.sql('DROP TRIGGER t23_fail_insert',env.schema)
    # Delayed INSERT widens a genuine overlapping transaction window on the owned schema.
    result['stage']='concurrency';env.phase='concurrency'
    _,etag=state(admin);pre_count=int(env.sql('SELECT COUNT(*) FROM summer_security_keys',env.schema))
    env.sql('CREATE TRIGGER t23_slow_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW DO SLEEP(2)',env.schema)
    barrier=threading.Barrier(2)
    def compete(port):
        client=Client();client.token=admin.token
        barrier.wait(timeout=5)
        return rotate(client,etag,port=port)
    samples=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        jobs=[pool.submit(compete,p) for p in (21081,21082)]
        while not all(j.done() for j in jobs):
            try:
                token=Client().login(21081,env.username,env.password).token
                s,jwks,_=Client().request(21082,'/oauth2/jwks',bearer=False)
                samples.append(s==200 and verify(token,jwks) and verify(old_token,jwks))
            except Exception:samples.append(False)
        replies=[j.result() for j in jobs]
    env.sql('DROP TRIGGER t23_slow_insert',env.schema)
    statuses=sorted(r[0] for r in replies)
    active=int(env.sql("SELECT COUNT(*) FROM summer_security_keys WHERE status='ACTIVE'",env.schema))
    added=int(env.sql('SELECT COUNT(*) FROM summer_security_keys',env.schema))-pre_count
    check('dual_node_same_version_one_success_one_conflict',statuses==[200,412],httpStatuses=statuses)
    check('dual_node_rotation_has_exactly_one_active_key',active==1,activeKeyCount=active,newKeyRows=added)
    check('concurrent_publication_samples_verify_both_tokens',bool(samples) and all(samples),samples=len(samples),failedSamples=sum(not v for v in samples))
    check('concurrent_loser_has_conflict_audit',any(r[0]==412 and audit_matches(r[3],'security_key_rotation_failed','SUMMER_PRECONDITION_FAILED') for r in replies))
    # Explicit expiry fixture: not natural waiting and not a management disable operation.
    result['stage']='expiry_fixture';env.phase='expiry_fixture'
    admin=Client().login(21081,env.username,env.password)
    env.sql("UPDATE summer_security_keys SET grace_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE kid='"+old_kid+"' AND status='GRACE'",env.schema)
    s,jwks,_=Client().request(21082,'/oauth2/jwks',bearer=False)
    check('expired_grace_public_key_filtered_time_fixture',s==200 and old_kid not in [k['kid'] for k in jwks['keys']],fixture='owned_schema_graceUntil_past_not_natural_token_expiry')
    items,_=state(admin)
    check('expired_grace_metadata_filtered_time_fixture',old_kid not in [i['kid'] for i in items])
    result['unverifiedOriginalScope']=['Natural token expiration followed by administrator disable; no management disable endpoint found in key controller.']
    check('no_sensitive_runtime_output',not env.sensitive_log_detected)
    result['stage']='complete'
except Exception as error:
    result['failureType']=type(error).__name__
    import traceback
    result['failureFrames']=[{'file':Path(f.filename).name,'line':f.lineno} for f in traceback.extract_tb(error.__traceback__)]
finally:
    for process in (browser,ui):
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
    result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    after=inputs();result['keyInputsStable']=before==after
    result['changedKeyPaths']=[p for p in sorted(set(before)|set(after)) if before.get(p)!=after.get(p)]
    result['audit']=env.security_events if env else []
    result['sensitiveFindings']=env.sensitive_findings if env else []
    result['diagnostics']=env.diagnostics if env else []
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int(result['stage']!='complete'),'skipped':0}
    result['passed']=result['stage']=='complete' and not result['counts']['failures'] and result.get('cleanup')=='VERIFIED' and all(b.get('passed') for b in result.get('browsers',[]))
    (OUT/'result.json').write_text(json.dumps(result,indent=2))
    print(json.dumps({k:result[k] for k in ['stage','counts','passed','cleanup']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
