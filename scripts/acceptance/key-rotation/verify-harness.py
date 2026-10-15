"""Repeat only three checks affected by first-run harness mistakes."""
import base64, json, os, subprocess, time, uuid
from pathlib import Path
from rotation_environment import RotationEnvironment, Client, ROOT, OUT, immutable_candidate
from grants import bootstrap, create_role, unwrap

NODE='/opt/homebrew/opt/node@22/bin/node';SCRIPT=Path(__file__).resolve().parent
result={'checks':[],'stage':'setup','networkMocking':False};env=None;ui=None;browser=None
def check(name,value):
    result['checks'].append({'name':name,'passed':bool(value)});print(name,'PASS' if value else 'FAIL',flush=True)
try:
    jar=immutable_candidate();env=RotationEnvironment();env.setup();env.start(21081,jar=jar)
    admin=Client().login(21081,env.username,env.password);bootstrap(admin,env.username);admin=Client().login(21081,env.username,env.password)
    role=create_role(admin,'ent-acme',['GET:/api/security/keys','POST:/api/security/keys:rotate','security.key.read','security.key.rotate','menu.securityKeys.read'],'T23 harness corrections')
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles');assert s==200
    s,d,h=admin.request(21081,f'/api/v1/enterprises/ent-acme/users/{env.username}/roles','PUT',{'roleIds':unwrap(d)['roleIds']+[role],'deniedCapabilityCodes':[]},headers={'If-Match':h.get('ETag'),'Idempotency-Key':uuid.uuid4().hex});assert s==200
    admin=Client().login(21081,env.username,env.password)
    s,d,h=admin.request(21081,'/api/security/keys');assert s==200
    etag=h.get('ETag');old_kid=unwrap(d)['items'][0]['kid']
    env.sql("CREATE TRIGGER t23_fail_insert BEFORE INSERT ON summer_security_keys FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T23 controlled insert failure'",env.schema)
    rid='t23-'+uuid.uuid4().hex
    s,d,h=admin.request(21081,'/api/security/keys:rotate','POST',{'retentionDays':1},headers={'If-Match':etag,'Idempotency-Key':uuid.uuid4().hex,'X-Request-Id':rid});assert s==500
    time.sleep(.5)
    check('failed_rotation_audit_operator_result',any(x['event']=='security_key_rotation_failed' and x.get('requestId')==rid and x.get('operatorMatchesAdmin') and x.get('result')=='FAILED' for x in env.security_events))
    env.sql('DROP TRIGGER t23_fail_insert',env.schema)
    s,d,h=admin.request(21081,'/api/security/keys:rotate','POST',{'retentionDays':1},headers={'If-Match':etag,'Idempotency-Key':uuid.uuid4().hex});assert s==200
    runenv=dict(os.environ,SUMMER_E2E_USERNAME=env.username,SUMMER_E2E_PASSWORD=env.password,SUMMER_ROTATION_MODE='retention')
    ui=subprocess.Popen([NODE,str(SCRIPT.parent/'identity/ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen([NODE,str(SCRIPT/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    result['browserExitCode']=browser.wait(timeout=120);result['browser']=json.loads((OUT/'browser-retention.json').read_text())
    # Use a fresh token before filtering the old signing key; the old admin token is deliberately invalidated by this fixture.
    admin=Client().login(21081,env.username,env.password)
    env.sql("UPDATE summer_security_keys SET grace_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE kid='"+old_kid+"' AND status='GRACE'",env.schema)
    s,d,h=admin.request(21081,'/api/security/keys')
    check('expired_grace_metadata_filtered_time_fixture',s==200 and old_kid not in [i['kid'] for i in unwrap(d)['items']])
    check('no_sensitive_runtime_output',not env.sensitive_log_detected)
    result['stage']='complete'
except Exception as error:
    result['failureType']=type(error).__name__
finally:
    for p in (browser,ui):
        if p and p.poll() is None:
            p.terminate()
            try:p.wait(timeout=15)
            except subprocess.TimeoutExpired:p.kill();p.wait(timeout=5)
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanup']='FAILED';result['cleanupFailureType']=type(error).__name__
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int(result['stage']!='complete'),'skipped':0}
    result['audit']=env.security_events if env else [];result['sensitiveFindings']=env.sensitive_findings if env else []
    result['passed']=result['stage']=='complete' and result['counts']['failures']==0 and result.get('browser',{}).get('passed') and result.get('cleanup')=='VERIFIED'
    (OUT/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps({'counts':result['counts'],'cleanup':result.get('cleanup'),'passed':result['passed']}),flush=True)
raise SystemExit(0 if result['passed'] else 1)
