"""Isolated real-API setup and Chrome replay; does not repeat the full API suite."""
import json,os,secrets,subprocess
from pathlib import Path
from environment import Environment,Client,OUT,ROOT,immutable_candidate
from grants import BASE,bootstrap,headers

env=None;ui=None;browser=None
result={'mode':'real-api-setup-chrome-only','setup_checks':[],'errors':0}
def setup_user(admin,name,password):
    status,_,_=admin.request(23081,'/api/users','POST',{'username':name,'password':password,'role':'USER'});assert status==200
    status,_,_=admin.request(23081,BASE+'/members','POST',{'subject':name,'displayName':'T15 Chrome member','email':name+'@example.test'},headers=headers());assert status==201
    result['setup_checks'].append({'name':name+'_real_identity_and_membership','status':'passed'})
try:
    candidate=immutable_candidate();env=Environment();env.setup();env.start(23081,jar=candidate)
    admin=Client().login(23081,env.username,env.password);bootstrap(admin,env.username);admin=Client().login(23081,env.username,env.password)
    result['setup_checks'].append({'name':'real_pkce_and_management_api_bootstrap','status':'passed'})
    ui_user='t15-ui-member';ui_password=secrets.token_urlsafe(24);env.additional_secrets.extend([ui_password,admin.token])
    setup_user(admin,ui_user,ui_password);setup_user(admin,env.denied_username,env.denied_password)
    runenv=os.environ.copy();runenv.update({'SUMMER_E2E_USERNAME':env.username,'SUMMER_E2E_PASSWORD':env.password,'ROLES_DENIED_USERNAME':env.denied_username,'ROLES_DENIED_PASSWORD':env.denied_password,'ROLES_UI_USERNAME':ui_user,'ROLES_OUTPUT':str(OUT),'AUTHORIZATION_ROLES_VITE_CACHE':str(OUT/('vite-cache-'+env.run))})
    script=Path(__file__).resolve().parent
    ui=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(script/'ui-server.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True);assert ui.stdout.readline().strip()=='UI_READY'
    browser=subprocess.Popen(['/opt/homebrew/opt/node@22/bin/node',str(script/'browser.mjs')],cwd=ROOT/'summer-ui',env=runenv,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True)
    for line in browser.stdout:
        if not line.startswith('CONTROL '):continue
        action=line.strip()[8:];reply={}
        if action=='user_access':
            actual=Client().login(23081,ui_user,ui_password);env.additional_secrets.append(actual.token);status,_,response=actual.request(23081,'/api/customers');reply={'status':status,'requestId':response.get('X-Request-Id')}
        elif action=='denied_access':
            actual=Client().login(23081,env.denied_username,env.denied_password);env.additional_secrets.append(actual.token);status,body,response=actual.request(23081,BASE+'/roles');reply={'status':status,'code':(body.get('error') or {}).get('code'),'requestId':body.get('requestId') or response.get('X-Request-Id')}
        elif action=='stop':env.stop(23081)
        elif action=='restart':env.start(23081,jar=candidate)
        else:raise RuntimeError('unknown_browser_control')
        browser.stdin.write(json.dumps(reply)+'\n');browser.stdin.flush()
    browser.wait(timeout=30);result['browser_exit_code']=browser.returncode
    if not (OUT/'browser-result.json').exists():raise RuntimeError('browser_receipt_missing')
    result['browser']=json.loads((OUT/'browser-result.json').read_text());result['status']=result['browser'].get('status','INCOMPLETE')
except Exception as error:result.update(status='INCOMPLETE',error_type=type(error).__name__);result['errors']+=1
finally:
    for process in [browser,ui]:
        if process and process.poll() is None:
            process.terminate()
            try:process.wait(timeout=15)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    if env:
        try:env.close();result['cleanup']='VERIFIED'
        except Exception as error:result.update(cleanup='FAILED',cleanup_error=type(error).__name__);result['errors']+=1
    if env:result['sensitive_log_detected']=env.sensitive_log_detected
    result['http_observations']=Client.observations
    (OUT/'browser-probe-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:result.get(k) for k in ['status','errors','error_type','browser_exit_code','cleanup']}),flush=True)
raise SystemExit(0 if result.get('status')=='PASSED' and result.get('cleanup')=='VERIFIED' else 1)
