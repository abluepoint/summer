"""Focused T17 UI recovery replay, real backend and current shared UI workspace."""
import hashlib, io, json, os, selectors, shutil, subprocess, tarfile, uuid
from environment import Environment, Client, OUT, ROOT, SCRIPT, immutable_candidate
from grants import bootstrap, create_role, bind

BASE='/api/v1/enterprises/ent-acme';env=ui=None
result={'issue':125,'mode':'targeted_ui','checks':[],'networkMocking':False,'businessSqlWrites':False}
def check(name,passed,**facts):
    result['checks'].append({'name':name,'passed':bool(passed),**facts});print(name,'PASS' if passed else 'FAIL',flush=True)
def call(c,path,method='GET',body=None,expected=200,headers=None):
    rid='t17-'+uuid.uuid4().hex;s,b,h=c.request(23081,path,method,body,headers={'X-Request-Id':rid,**(headers or {})})
    check(method+' '+path,s==expected and b['requestId']==h.get('X-Request-Id')==rid,status=s)
    if s!=expected:raise AssertionError('unexpected_http_status_'+str(s))
    return b['data'],h
try:
    candidate=immutable_candidate();result['candidateSha256']=hashlib.sha256(candidate.read_bytes()).hexdigest()
    archive=OUT/'executed-scripts';archive.mkdir(exist_ok=True)
    for p in SCRIPT.iterdir():
        if p.is_file():shutil.copy2(p,archive/p.name)
    env=Environment();env.setup();env.start(23081,jar=candidate)
    admin=Client().login(23081,env.username,env.password);boot=bootstrap(admin,env.username);admin=Client().login(23081,env.username,env.password)
    catalog,_=call(admin,BASE+'/capabilities/catalog');caps={c['code']:c['capabilityId'] for c in catalog['capabilities']}
    code='menu.dataScopes.read'
    if code not in caps:
        c,_=call(admin,BASE+'/capabilities','POST',{'code':code,'resource':'menu.dataScopes','action':'read','riskLevel':'LOW','scope':'ENTERPRISE'},201,{'Idempotency-Key':uuid.uuid4().hex});caps[code]=c['capabilityId']
    menu=create_role(admin,'ent-acme',[caps[code]],'T17 UI scope menu')
    _,h=call(admin,BASE+'/users/'+env.username+'/roles')
    call(admin,BASE+'/users/'+env.username+'/roles','PUT',{'roleIds':[boot,menu],'deniedCapabilityCodes':[]},headers={'Idempotency-Key':uuid.uuid4().hex,'If-Match':h['ETag']})
    call(admin,'/api/users','POST',{'username':env.denied_username,'password':env.denied_password,'role':'USER'})
    call(admin,BASE+'/members','POST',{'subject':env.denied_username,'displayName':'T17 denied scope manager','email':'t17-ui@example.test'},201,{'Idempotency-Key':uuid.uuid4().hex})
    bind(admin,'ent-acme',env.denied_username,[menu])
    ui_dir=ROOT/'summer-ui'
    commit=subprocess.check_output(['git','--no-optional-locks','rev-parse','HEAD'],text=True).strip()
    watched=list((ui_dir/'src/pages/data-scopes').glob('*'))+list((ui_dir/'src/menu').glob('*'))+[ui_dir/'src/main.tsx',ui_dir/'src/layout.tsx',ui_dir/'src/routes.ts']
    overlay={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in watched if p.is_file()}
    (OUT/'ui-provenance.json').write_text(json.dumps({'mode':'current_shared_workspace','commit':commit,'cwd':str(ui_dir),'sourceHashes':overlay},indent=2))
    child=os.environ.copy();child.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'DATA_SCOPES_READER_USERNAME':env.denied_username,'DATA_SCOPES_READER_PASSWORD':env.denied_password,'DATA_SCOPES_OUTPUT':str(OUT),'DATA_SCOPES_VITE_CACHE':str(OUT/'vite-cache')})
    node='/opt/homebrew/opt/node@22/bin/node'
    ui=subprocess.Popen([node,str(SCRIPT/'ui-server.mjs')],cwd=ui_dir,env=child,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    sel=selectors.DefaultSelector();sel.register(ui.stdout,selectors.EVENT_READ);ready=sel.select(timeout=45);sel.close()
    if not ready or ui.stdout.readline().strip()!='UI_READY':raise RuntimeError('ui_startup_failed')
    browser=subprocess.run([node,str(SCRIPT/'browser.mjs')],cwd=ui_dir,env=child,capture_output=True,text=True,timeout=180)
    result['browserExit']=browser.returncode
    br=json.loads((OUT/'browser-result.json').read_text());result['browserCounts']={k:br[k] for k in ['tests','failures','errors','skipped']}
    result['sourceChanged']=[p for p,h in overlay.items() if hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
    # Validate the UI source used above; hashes detect concurrent related edits.
    typecheck=subprocess.run([node,str(ROOT/'summer-ui/node_modules/typescript/bin/tsc'),'--noEmit'],cwd=ui_dir,capture_output=True,text=True,timeout=90)
    (OUT/'typecheck.txt').write_text(typecheck.stdout+typecheck.stderr);result['typecheckExit']=typecheck.returncode
    result['completed']=True
except Exception as error:
    result['errorType']=type(error).__name__
    if isinstance(error,AssertionError):result['safeAssertion']=str(error)
finally:
    if ui and ui.poll() is None:
        ui.terminate()
        try:ui.wait(timeout=10)
        except subprocess.TimeoutExpired:ui.kill();ui.wait()
    if env:
        result['sensitiveLogDetected']=env.sensitive_log_detected
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result['cleanupErrorType']=type(error).__name__
    result['counts']={'tests':len(result['checks']),'failures':sum(not c['passed'] for c in result['checks']),'errors':int('errorType' in result),'skipped':0}
    (OUT/'http-observations.json').write_text(json.dumps(Client.observations,indent=2));(OUT/'result.json').write_text(json.dumps(result,indent=2))
print(json.dumps({k:result.get(k) for k in ['counts','browserCounts','typecheckExit','cleanup','errorType']}))
raise SystemExit(0 if result.get('completed') and result.get('browserExit')==0 and result.get('typecheckExit')==0 and not result['counts']['failures'] and not result['counts']['errors'] and result.get('cleanup')=='VERIFIED' and not result.get('sourceChanged') else 1)
